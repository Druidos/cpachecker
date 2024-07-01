#!/usr/bin/env python3

#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0


# This script was used to obtain first versions of gitlab-ci/*.csv from BenchExec's .csv tables.


import csv
import sys
from os.path import join


def format_status(status):
    if status == 'true':
        return 'TRUE'

    if status.startswith('false'):
        # false(valid-memleak) -> FALSE valid-memleak
        return 'FALSE ' + status[6:-1]

    if status in ['EXCEPTION', 'ERROR', 'TIMEOUT']:
        return status

    print('unexpected status', status)
    return status


def write_taskset_from_results(results_csv, set_csv):
    with open(results_csv, 'r', encoding='utf8') as result_table:
        reader = csv.reader(result_table, delimiter='\t')
        next(reader)  # skip tool version line
        next(reader)  # skip run set line
        header = next(reader)
        set_directory = header[0]
        skipped = 0
        # expected verdict is in [1] if present
        expected_idx = None if header[1] == 'status' else 1
        status_idx = 1 if header[1] == 'status' else 2
        # cputime_idx = status_idx + 1
        with open(set_csv, 'w', encoding='utf8') as test_suite:
            writer = csv.writer(test_suite, delimiter='\t')
            writer.writerow('test previous correct'.split())
            for row in reader:
                status = row[status_idx]
                if not status:
                    skipped += 1
                    continue
                status = format_status(status)
                # cpu_time = float(row[cputime_idx])
                # if not cpu_time > 20.0:  # TODO timeout threshold option
                #     skipped += 1
                #     continue
                # if status != 'TRUE' and not status.startswith('FALSE'):
                #     skipped += 1
                #     continue
                filename = join(set_directory, row[0])
                # yml_desc = safe_load(yml_filename)
                # row_to_write = [yml_desc['input_files'], status]
                row_to_write = [filename, status]
                if expected_idx:
                    exp = format_status(row[expected_idx])
                    row_to_write.append(exp)
                writer.writerow(row_to_write)
        if skipped > 0:
            print('no status for', skipped, 'programs in this set')
        print('read', reader.line_num - 3, 'rows from table')


if __name__ == '__main__':
    write_taskset_from_results(sys.argv[1], sys.argv[2])
