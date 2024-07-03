#!/usr/bin/env python3

#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

# CI script, as ISP RAS shared runners are not configured to run benchexec due to cgroups, see
# https://github.com/sosy-lab/benchexec/blob/main/doc/INSTALL.md#setting-up-cgroups-in-a-dockerpodman-container,
# although maybe --privileged flag is unneccessary, and only -v /sys/fs/cgroup:/sys/fs/cgroup:rw
# is needed. Code is largely copied from
# https://gitlab.ispras.ru/p.andrianov/cpachecker/-/blob/6fc4878774a049868c819eab9531684c5914f5dc/ci-test-run.py

import argparse
import logging
import re
import subprocess
import sys
import csv
from multiprocessing import Pool, cpu_count
from os.path import exists
from time import perf_counter

sys.dont_write_bytecode = True  # prevent creation of .pyc files
logger = logging.getLogger(__name__)


def run_parallel(cmd_lines, processes):
    logger.info("Starting %d processes for %d tasks", processes, len(cmd_lines))
    if not cmd_lines:
        sys.exit("No tasks")

    outcomes = []
    for i in range(0, 1 + len(cmd_lines), processes):
        logger.debug("batching from %d to %d out of %d", i, i + processes, len(cmd_lines))
        pool = Pool(processes=processes)
        # allocate a task for each cpu core
        async_results = [
            pool.apply_async(run, [cmd, j % processes])
            for j, cmd in enumerate(cmd_lines[i:i + processes])
        ]
        pool.close()
        pool.join()
        # wait for each task in this batch to finish
        # TODO sophisticated task management using .ready() or some better solution
        # anyway it is better than one task at a time or running all tasks with set affinity
        outcomes += [ares.get() for ares in async_results]

    return outcomes


class Outcome:
    def __init__(self, completed_process):
        self.outcome = parse_verdict(completed_process)

        match = re.search(r'Total CPU time for CPAchecker:\s+(.*)', completed_process.stdout)
        self.cpu_time = match.group(1) if match else None


def run(cmd_line, allowed_cpu):
    # can throw timeout exception if process hangs
    # do not check return code (e.g. it was exception)
    logger.debug('RUN cpu%d %s', allowed_cpu, ' '.join(cmd_line))
    start_time = perf_counter()
    ret = subprocess.run(
        # cpu affinity is needed to get reliable results regarding time usage
        # (and to avoid wasting cputime i guess)
        ['taskset', '--cpu-list', str(allowed_cpu)] + cmd_line,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding='utf8')
    finish_time = perf_counter()
    outcome = Outcome(ret)
    # TODO better logging?
    logger.info(f"cpu{allowed_cpu:02} {finish_time - start_time:5.2f}s walltime {outcome.cpu_time} cputime for {cmd_line[-1]}")  # noqa: G004
    return outcome


class OutcomeParserError(Exception):
    pass


def parse_verdict(ret):
    match = re.search(r'Verification result: TRUE', ret.stdout)
    if match:
        return 'TRUE'

    match = re.search(r'Verification result: FALSE\. Property violation \((.*)\)', ret.stdout)
    if match:
        return 'FALSE ' + match.group(1)

    match = re.search(r'Error: (.*)', ret.stderr)
    if match:
        return 'ERROR ' + match.group(1)

    match = re.search(r'Exception (in thread .*)', ret.stderr)
    if match:
        return 'EXCEPTION ' + match.group(1)

    match = re.search(r'Shutdown requested|Analysis interrupted', ret.stderr)
    if match:
        return 'TIMEOUT'

    # don't match 'UNKNOWN' and fail, so that additional info is checked manually

    logger.fatal(" ".join(ret.args))
    logger.fatal(ret.stdout)
    logger.fatal(ret.stderr)
    raise OutcomeParserError("Cannot parse verification result.")


def compare_outcomes(current_outcome, previous_outcome, correct_verdict):
    if current_outcome == previous_outcome:
        return 'NO CHANGE'

    if previous_outcome == 'TRUE' or previous_outcome.startswith('FALSE'):
        # use #startswith() to match "FALSE subproperty: ..." with "FALSE subproperty"
        if correct_verdict and not previous_outcome.startswith(correct_verdict):
            prev = 'PROBLEM'
        else:
            prev = 'VERDICT'
    else:
        prev = 'TIMEOUT' if previous_outcome == 'TIMEOUT' else 'PROBLEM'

    if current_outcome == 'TRUE' or current_outcome.startswith('FALSE'):
        if correct_verdict and not current_outcome.startswith(correct_verdict):
            curr = 'PROBLEM'
        else:
            curr = 'VERDICT'
    else:
        curr = 'TIMEOUT' if current_outcome == 'TIMEOUT' else 'PROBLEM'

    if curr == 'TIMEOUT':
        assert prev != 'TIMEOUT'
        return 'NEW TIMEOUT'

    if prev == 'VERDICT':
        # verdict change if we don't know correct verdict
        # (or both are same expected violation with different details)
        return 'VERDICT CHANGE' if curr == 'VERDICT' else 'LOST VERDICT'

    if curr == 'VERDICT':
        return 'NEW VERDICT'

    assert curr == 'PROBLEM'
    # new problem if was timeout
    return 'PROBLEM CHANGE' if prev == 'PROBLEM' else 'NEW PROBLEM'


def log_if_any(count, total, description, warning=True):
    if count == 0:
        return
    msg = f'{count} out of {total} ' + description
    if warning:
        logger.warning(msg)
    else:
        logger.info(msg)


def conclude(test_descriptions, outcomes, cmd_lines):
    full_log_rows = [['source file', 'outcome', 'correct verdict', 'command line']]
    log_no_flaky = [full_log_rows[0]]
    diff_rows = [['source file', 'change', 'new outcome', 'previous outcome',
                  'correct verdict', 'command line']]
    flaky_timeouts = [['source file', 'outcome', 'correct verdict', 'command line']]
    count = {
        'NO CHANGE': 0,
        'NEW VERDICT': 0,
        'LOST VERDICT': 0,
        'VERDICT CHANGE': 0,
        'NEW PROBLEM': 0,
        'PROBLEM CHANGE': 0,
        'NEW TIMEOUT': 0,
    }
    for desc, outcome, cmd in zip(test_descriptions, outcomes, cmd_lines):
        outcome = outcome.outcome
        print_cmd = ' '.join(cmd)
        log_row = [desc.source_filename, outcome, desc.correct_verdict or '', print_cmd]
        full_log_rows.append(log_row)
        cmp = compare_outcomes(outcome, desc.previous_outcome, desc.correct_verdict)
        count[cmp] += 1
        if cmp == 'NO CHANGE':
            log_no_flaky.append(log_row)
        else:
            diff_rows.append([desc.source_filename, cmp, outcome, desc.previous_outcome,
                              desc.correct_verdict or '', print_cmd])
            if desc.previous_outcome == 'TIMEOUT':
                flaky_timeouts.append(log_row)
            elif outcome == 'TIMEOUT':
                log_row[1] = desc.previous_outcome
                flaky_timeouts.append(log_row)
            else:
                log_no_flaky.append(log_row)

    if len(diff_rows) > 1:
        with open('diff.csv', 'w', encoding='utf8') as diff_csv:
            w = csv.writer(diff_csv, delimiter='\t')
            w.writerows(diff_rows)
    if len(flaky_timeouts) > 1:
        logger.warning(
            'There are %d flaky timeouts. Add flaky-as-non-timeouts.csv with a longer '
            'timelimit or flaky-as-timeouts.csv with a shorter timelimit in the '
            'gitlab-ci/test-set directory. The flaky timeouts are included in full-log.csv, '
            'but excluded in log-without-flaky-timeouts.csv, so you can use that one '
            'to update the current test-set.', len(flaky_timeouts) - 1)
        with open('flaky-as-non-timeouts.csv', 'w', encoding='utf8') as f:
            w = csv.writer(f, delimiter='\t')
            w.writerows(flaky_timeouts)
        with open('flaky-as-timeouts.csv', 'w', encoding='utf8') as f:
            w = csv.writer(f, delimiter='\t')
            for row in flaky_timeouts:
                row[1] = 'TIMEOUT'
                w.writerow(row)
        with open('log-without-flaky-timeouts.csv', 'w', encoding='utf8') as f:
            w = csv.writer(f, delimiter='\t')
            w.writerows(log_no_flaky)
    with open('full-log.csv', 'w', encoding='utf8') as logfile:
        w = csv.writer(logfile, delimiter='\t')
        w.writerows(full_log_rows)

    total = len(outcomes)
    the_same = count["NO CHANGE"]
    new_verdicts = count["NEW VERDICT"]
    flaky_timeouts_count = len(flaky_timeouts) - 1

    log_if_any(the_same, total, 'have same outcome as before', warning=False)
    log_if_any(count["LOST VERDICT"], total, 'have lost verdict to an error')
    log_if_any(count["VERDICT CHANGE"], total, 'have changed verdict to another verdict')
    log_if_any(count["PROBLEM CHANGE"], total, 'have encountered an error other than expected')
    log_if_any(count["NEW PROBLEM"], total, 'have encountered an error instead of a timeout')
    log_if_any(count["NEW TIMEOUT"], total, 'have a timeout instead of a verdict or an error')
    log_if_any(
        new_verdicts, total,
        'have obtained verdict. maybe update respective gitlab-ci/test-set/%.csv with full-log.csv')
    if total > the_same + flaky_timeouts_count:
        sys.exit(
            f'{total - the_same} out of {total} tests degraded or obtained a verdict, see diff.csv')
    elif flaky_timeouts_count > 0:
        print(f'{flaky_timeouts_count} tests have flaky timeout verdicts')
        sys.exit(110)
    else:
        print(f'all {total} tests have same outcomes as previously')


class TestDescription:
    def __init__(self, row):
        self.source_filename = row[0]
        self.previous_outcome = row[1]
        self.correct_verdict = row[2] if len(row) == 3 else None


if __name__ == "__main__":
    # parse args
    parser = argparse.ArgumentParser(
        description='Run integration tests for CPAchecker SMG analysis without BenchExec.')
    parser.add_argument(
        'task_set',
        type=str,
        help='a test set .csv file that lists '
             '[source file, previous verdict, (optional) correct verdict] for each test.\n'
             'must have a corresponding gitlab-ci/config/*.config '
             'that matches this set\'s filename prefix.'
    )
    parser.add_argument('--log', type=str, default='INFO', help='console logging level')

    args = parser.parse_args()
    logging.basicConfig(level=getattr(logging, args.log.upper()),
                        format='%(asctime)s %(levelname)s: %(message)s')
    if not args.task_set:
        sys.exit("No test set specified")

    # read description
    test_descriptions = []
    with open('gitlab-ci/test-set/' + args.task_set, 'r', encoding='utf8') as test_set_file:
        test_rows = csv.reader(test_set_file, delimiter='\t')
        next(test_rows)  # header
        for row in test_rows:
            test_descriptions.append(TestDescription(row))
    if not test_descriptions:
        sys.exit("Empty test set " + args.task_set)

    # check that all sources exist
    not_found_sources = [
        test.source_filename
        for test in test_descriptions
        if not exists(test.source_filename)
    ]
    if not_found_sources:
        sys.exit('These files are in test set, but do not exist:\n' + '\n'.join(not_found_sources))

    # make command line
    cmd_template = ['scripts/cpa.sh']
    config = args.task_set.split('.', 1)[0]
    with open('gitlab-ci/config/' + config + '.config', 'r', encoding='utf8') as config_file:
        for line in config_file.readlines():
            cmd_template += line.split()

    # run each test
    cmd_lines = [
        cmd_template + [test.source_filename]
        for test in test_descriptions
        # TODO run without timeouts by default
        #  if args.with_timeouts or test.previous_outcome != 'TIMEOUT'
    ]
    # seq.version: outcomes = [run(cmd) for cmd in cmd_lines]
    outcomes = run_parallel(
        # timelimit subprocess so CPAchecker does not hang there forever,
        # but also allow CPU to be used by other tasks
        cmd_lines, processes=cpu_count())
    assert len(test_descriptions) == len(cmd_lines) == len(outcomes)
    conclude(test_descriptions, outcomes, cmd_lines)
