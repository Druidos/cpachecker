#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0


import os
import re
import sys
from defusedxml.minidom import parse
from datetime import datetime

year = datetime.today().strftime('%Y')
today = datetime.today().strftime('%Y-%m-%d')

red_color = '\033[1;31m'
green_color = '\033[1;32m'
yellow_color = '\033[1;33m'
no_color = '\033[0m'  # No Color

common_header = """<?xml version="1.0" encoding="utf-8"?>
<!--
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

SPDX-FileCopyrightText: %s Dirk Beyer <https://www.sosy-lab.org>
SPDX-License-Identifier: Apache-2.0
-->

<!DOCTYPE result
  PUBLIC '+//IDN sosy-lab.org//DTD BenchExec result 3.0//EN'
  'https://www.sosy-lab.org/benchexec/result-3.0.dtd'>
<result benchmarkname="%s.%s.%s" tool="CPAchecker" version="CI"
  toolmodule="benchexec.tools.cpachecker" date="%s" starttime="-"
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
    dom_with_runs = [
        child for child in document.childNodes
        if child.nodeType == child.ELEMENT_NODE and child.nodeName == 'result'
    ]
    if len(dom_with_runs) != 1:
        sys.exit('expected one <result/>, got %d' % len(dom_with_runs))
    results = [
        child for child in dom_with_runs[0].childNodes
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


def write(key_triple, table, xml_filename):
    if xml_filename.endswith('previous-results.xml'):
        postfix = 'previous-results'
    elif xml_filename.endswith('timeouts.xml'):
        postfix = 'flaky-timeouts'
    else:
        exit('unknown postfix in ' + xml_filename)
    all_files = sorted(table.keys())
    # the table is { file: (status, dom) }
    doms = [table[filename][1] for filename in all_files]
    with open(xml_filename, 'wt', encoding='utf8') as f:
        testset, rundef, tasks = key_triple
        f.write(common_header % (year, testset, rundef, tasks, today, postfix))
        f.writelines(common_newline + r.toxml() for r in doms)
        f.write(common_footer)


old_files = re.compile(
    r'(?P<testset>[^.]+)\.'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)\.'
    r'(previous-results|timeouts)\.xml'
)
xml_bz2 = {}

if __name__ == '__main__':
    for file in os.listdir('gitlab-ci/previous-results'):
        match = old_files.fullmatch(file)
        if not match:
            print('ignoring', file)
            continue
        triple = match.group('testset'), match.group('rundef'), match.group('tasks')
        if triple not in xml_bz2:
            xml_bz2[triple] = []
        xml_bz2[triple].append(file)
    for triple in xml_bz2:
        for xml in xml_bz2[triple]:
            print('reading', xml)
            xml = 'gitlab-ci/previous-results/' + xml
            with open(xml, 'rt', encoding='utf8') as f:
                document = parse(f)
                rows = parse_document(document)
                print(f'rewriting {xml}')
                write(triple, rows, xml)
