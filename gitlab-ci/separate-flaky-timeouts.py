#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0


import bz2
import os
import re
import sys
from defusedxml.minidom import parse

red_color = '\033[1;31m'
green_color = '\033[1;32m'
yellow_color = '\033[1;33m'
no_color = '\033[0m'  # No Color

common_header = """<?xml version="1.0" encoding="utf-8"?>
<!--
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
SPDX-License-Identifier: Apache-2.0
-->

<!DOCTYPE result
  PUBLIC '+//IDN sosy-lab.org//DTD BenchExec result 3.0//EN'
  'https://www.sosy-lab.org/benchexec/result-3.0.dtd'>
<result benchmarkname="%s.%s.%s" tool="CPAchecker" version="CI"
  toolmodule="benchexec.tools.cpachecker" date="-" starttime="-"
  generator="merged benchexec tables" name="%s">
  <description>Table merged from some GitLab CI results.</description>
  <columns>
    <column title="status"/>
    <column title="cputime"/>
    <column title="walltime"/>
    <column title="reached"/>
    <column title="transfer"/>
    <column title="stop"/>
    <column title="error"/>
    <column title="exception"/>
  </columns>
"""
common_newline = '\n  '
common_footer = "\n</result>\n"


def parse_document(document):
    domWithRuns = [
        child for child in document.childNodes
        if child.nodeType == child.ELEMENT_NODE and child.nodeName == 'result'
    ]
    if len(domWithRuns) != 1:
        sys.exit('expected one <result/>, got ' + len(domWithRuns))
    results = [
        child for child in domWithRuns[0].childNodes
        if child.nodeType == child.ELEMENT_NODE and child.nodeName == 'run'
    ]
    filenames = [r.getAttribute('name').split('/programs/', 2)[1] for r in results]
    statuses = [
        column.getAttribute('value')
        for r in results
        for column in r.childNodes
        if column.nodeType == column.ELEMENT_NODE and column.getAttribute('title') == 'status'
    ]
    return {file: (status, dom) for (dom, file, status) in zip(results, filenames, statuses)}


# fix paths in case manual results
def fix_paths_in_dom(dom):
    for atr in ['name', 'propertyFile', 'files']:
        old = dom.getAttribute(atr)
        if '/programs/' in old:
            new = 'test/programs/' + old.split('/programs/', 2)[1]
            dom.setAttribute(atr, new)
        elif '/config/' in old:
            new = 'test/config/' + old.split('/config/', 2)[1]
            dom.setAttribute(atr, new)
    # 'files' is always an array with only element
    dom.setAttribute('files', '[' + dom.getAttribute('files'))


def choose_dom(row, timeouts):
    statuses = {status for (status, _) in row if status.startswith('TIMEOUT') == timeouts}

    if len(statuses) == 0:
        return None

    if len(statuses) > 1:
        print('found multiple outcomes for ' + filename + ': ' + ', '.join(statuses))

    prefered = None
    if timeouts or len(statuses) == 1:
        # it is either the only status or all are timeouts so choose any
        prefered = statuses.pop()
    if 'TIMEOUT' in statuses:
        # ...but prefer a clean timeout
        prefered = 'TIMEOUT'
    if prefered:
        for (status, dom) in row:
            if status == prefered:
                return dom
        raise AssertionError()

    if not timeouts and len(statuses) > 2:
        sys.exit('expected at most two non-timeouts: an old and a new one')

    new_status, new_dom = row[-1]
    correct_verdicts = {dom.getAttribute('expectedVerdict') for (_, dom) in row}
    if len(correct_verdicts) == 0:
        correct_verdict = 'absent'
        color = red_color
    elif len(correct_verdicts) == 1:
        correct_verdict = correct_verdicts.pop()
        color = green_color if new_status == correct_verdict else red_color
    else:
        correct_verdict = ', '.join(correct_verdicts)
        color = red_color
    print('choosing', color + new_status + no_color, '(correct verdict is',
          correct_verdict + ')')
    return new_dom


def compose_two_bodies(key_triple, tables):
    primary = []
    secondary = []
    any_flaky = False

    all_files = {file for table in tables for file in table}
    for filename in all_files:
        if filename not in tables[-1]:
            print(f'{yellow_color}Removing{no_color}', "a file from the test set:", filename)
            continue

        any_old_have_file = any(filename in table for table in tables[:-1])
        if not any_old_have_file:
            print(f'{yellow_color}Adding{no_color}', "a file to the test set:", filename)
            row = [tables[-1][filename]]

        else:
            all_old_have_file = all(filename in table for table in tables[:-1])
            if not all_old_have_file:
                sys.exit("File %s was missing in some tables" % filename)
            row = [table[filename] for table in tables]

        non_timeout_dom = choose_dom(row, timeouts=False)
        if non_timeout_dom:
            fix_paths_in_dom(non_timeout_dom)
        timeout_dom = choose_dom(row, timeouts=True)
        if timeout_dom:
            fix_paths_in_dom(timeout_dom)
        if non_timeout_dom is None and timeout_dom is None:
            sys.exit("File %s has no outcome" % filename)
        if non_timeout_dom and timeout_dom:
            any_flaky = True
        primary_dom = non_timeout_dom or timeout_dom
        secondary_dom = timeout_dom or non_timeout_dom
        primary.append(primary_dom)
        secondary.append(secondary_dom)

    testset, rundef, tasks = key_triple
    prefix = f'gitlab-ci/previous-results/{testset}.{rundef}.{tasks}'
    with open(prefix + '.previous-results.xml', 'wt', encoding='utf8') as f:
        f.write(common_header % (testset, rundef, tasks, 'previous-results'))
        f.write(common_newline)
        f.writelines(r.toxml() + common_newline for r in primary)
        f.write(common_footer)
    if any_flaky:
        with open(prefix + '.timeouts.xml', 'wt', encoding='utf8') as f:
            f.write(common_header % (testset, rundef, tasks, 'flaky-timeouts'))
            f.write(common_newline)
            f.writelines(r.toxml() + common_newline for r in secondary)
            f.write(common_footer)


new_files = re.compile(
    r'output-'
    r'(?P<testset>[^.]+)\.'
    r'(2024-\d\d-\d\d_\d\d-\d\d-\d\d\.results\.)?'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)\.xml(\.bz2)?'
)
old_files = re.compile(
    r'(?P<testset>[^.]+)\.'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)\.'
    r'(previous-results|timeouts)\.xml'
)
xml_bz2 = {}

if __name__ == '__main__':
    for filename in os.listdir('gitlab-ci/previous-results/to-parse'):
        match = new_files.fullmatch(filename)
        if not match:
            print('ignoring', filename)
            continue
        triple = match.group('testset'), match.group('rundef'), match.group('tasks')
        if triple not in xml_bz2:
            xml_bz2[triple] = []
            for file in os.listdir('gitlab-ci/previous-results'):
                m = old_files.fullmatch(file)
                if m and triple == (m.group('testset'), m.group('rundef'), m.group('tasks')):
                    print('found already present', file, 'to update')
                    xml_bz2[triple].append(file)
        # should be last in case verdict changed
        # if there are different verdicts in multiple new files
        # the result depends on their order
        xml_bz2[triple].append('to-parse/' + filename)
    for triple in xml_bz2:
        tables = []
        print('reading', ', '.join(xml_bz2[triple]))
        for xml in xml_bz2[triple]:
            xml = 'gitlab-ci/previous-results/' + xml
            if xml.endswith('.bz2'):
                with bz2.open(xml, 'rt', encoding='utf8') as f:
                    document = parse(f)
                    rows = parse_document(document)
                    tables.append(rows)
            else:
                with open(xml, 'rt', encoding='utf8') as f:
                    document = parse(f)
                    rows = parse_document(document)
                    tables.append(rows)
        print(f'writing {triple[0]}.{triple[1]}.{triple[2]}.{{previous-results,timeouts}}.xml')
        compose_two_bodies(triple, tables)
