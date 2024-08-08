#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0


import bz2
from defusedxml.minidom import parse
import os
import re
import sys


common_header = """<?xml version="1.0" encoding="utf-8"?>
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
common_footer = "\n</result>"


def parse_document(document):
    results = [
        child for child in document.childNodes[1].childNodes
        if child.nodeType == child.ELEMENT_NODE
        and child.nodeName == 'run'
    ]
    filenames = [r.getAttribute('name').split('/programs/', 2)[1] for r in results]
    statuses = [
        column.getAttribute('value')
        for r in results
        for column in r.childNodes
        if column.nodeType == column.ELEMENT_NODE
        and column.getAttribute('title') == 'status'
    ]
    return zip(results, filenames, statuses)


# fix paths in case manual results
def fix_paths_in_dom(dom):
    for atr in ['name', 'propertyFile', 'files']:
        old = dom.getAttribute(atr)
        if '/programs/' not in old:
            continue
        new = 'test/programs/' + old.split('/programs/', 2)[1]
        dom.setAttribute(atr, new)
    # 'files' is always an array with only element
    dom.setAttribute('files', '[' + dom.getAttribute('files'))


def compose_two_bodies(key_triple, parsed_triples):
    primary = []
    secondary = []
    any_flaky = False

    for row in zip(*parsed_triples):
        filename = {name for (_, name, _) in row}
        if len(filename) > 1:
            sys.exit('comparing results for different files: %s' % filename)
        filename = filename.pop()

        non_timeout = {status for (_, _, status) in row if not status.startswith('TIMEOUT')}
        if len(non_timeout) == 0:
            non_timeout = None
        elif len(non_timeout) == 1:
            non_timeout = non_timeout.pop()
            for (dom, _, status) in row:
                if status == non_timeout:
                    non_timeout = dom
                    break
            fix_paths_in_dom(non_timeout)
            primary.append(non_timeout)
        else:
            sys.exit('found multiple outcomes for ' + filename + ': '
                     + ', '.join(st for (_, st) in non_timeout))

        timeout = {status for (_, _, status) in row if status.startswith('TIMEOUT')}
        if len(timeout) == 0:
            timeout = None
            # no timeouts among outcomes
            secondary.append(non_timeout)
            continue  # to next row
        if 'TIMEOUT' in timeout:
            timeout = 'TIMEOUT'
        else:
            if len(timeout) > 1:
                print('found multiple outcomes for ' + filename + ': ' + timeout)
            timeout = timeout.pop()
        for (dom, _, status) in row:
            if status == timeout:
                timeout = dom
                break

        fix_paths_in_dom(timeout)
        if non_timeout is None:
            primary.append(timeout)
        else:
            any_flaky = True
        secondary.append(timeout)

    testset, rundef, tasks = key_triple
    prefix = f'gitlab-ci/previous-results/{testset}.{rundef}.{tasks}'
    with bz2.open(prefix + '.previous-results.xml.bz2', 'wt', encoding='utf8') as f:
        f.write(common_header % (testset, rundef, tasks, 'previous-results'))
        f.write(common_newline)
        f.writelines(r.toxml() + common_newline for r in primary)
        f.write(common_footer)
    if any_flaky:
        with bz2.open(prefix + '.timeouts.xml.bz2', 'wt', encoding='utf8') as f:
            f.write(common_header % (testset, rundef, tasks, 'flaky-timeouts'))
            f.write(common_newline)
            f.writelines(r.toxml() + common_newline for r in secondary)
            f.write(common_footer)


p1 = re.compile(
    r'output-'
    r'(?P<testset>[^.]+)\.'
    r'(2024-\d\d-\d\d_\d\d-\d\d-\d\d\.results\.)?'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)\.'
    r'xml\.bz2'
)
p2 = re.compile(
    r'(?P<testset>[^.]+)\.'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)'
    r'-(half|one|double)-time\.'
    r'xml\.bz2'
)
p3 = re.compile(
    r'(?P<testset>[^.]+)\.'
    r'(?P<rundef>[^.]+)\.'
    r'(?P<tasks>[^.]+)\.'
    r'(previous-results|timeouts)\.'
    r'xml\.bz2'
)
xml_bz2 = {}


if __name__ == '__main__':
    for filename in os.listdir('gitlab-ci/previous-results/to-parse'):
        match = p1.fullmatch(filename) or p2.fullmatch(filename)
        if not match:
            print('ignoring', filename)
            continue
        triple = match.group('testset'), match.group('rundef'), match.group('tasks')
        if triple not in xml_bz2:
            xml_bz2[triple] = []
            for file in os.listdir('gitlab-ci/previous-results'):
                m = p3.fullmatch(file)
                if m and triple == (m.group('testset'), m.group('rundef'), m.group('tasks')):
                    print('found already present', file, 'to update')
                    xml_bz2[triple].append(file)
        xml_bz2[triple].append('to-parse/' + filename)
    for triple in xml_bz2:
        tables = []
        print('reading', ', '.join(xml_bz2[triple]))
        for compressed_xml in xml_bz2[triple]:
            compressed_xml = 'gitlab-ci/previous-results/' + compressed_xml
            with bz2.open(compressed_xml, 'rt', encoding='utf8') as f:
                document = parse(f)
                rows = parse_document(document)
                tables.append(rows)
        print(f'writing {triple[0]}.{triple[1]}.{triple[2]}.{{previous-results,timeouts}}.xml.bz2')
        compose_two_bodies(triple, tables)
