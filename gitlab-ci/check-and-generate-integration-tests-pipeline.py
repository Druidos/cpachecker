#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

import os
import sys
from defusedxml.minidom import parse


test_sets_for_ci_directory = 'test/test-sets/gitlab-ci/'
long_test_sets_for_ci_directory = 'test/test-sets/gitlab-ci/long/'


def write_jobs(f, test_set_name, long_set=False):
    directory = long_test_sets_for_ci_directory if long_set else test_sets_for_ci_directory
    document = parse(directory + test_set_name)
    # check both error and exception columns are present
    columns_elements = document.getElementsByTagName('columns')
    columns = {
        column_element.getAttribute('title'): column_element
        for columns_element in columns_elements
        for column_element in columns_element.getElementsByTagName('column')
        if column_element.getAttribute('title') in ['error', 'exception']
    }
    if 'error' not in columns or 'exception' not in columns:
        sys.exit(
            'expected <column title="error"> and <column title="exception">, got %s'
            % columns)
    children = columns['error'].childNodes
    if len(children) != 1 or children[0].data != 'Error':
        sys.exit('expected text "Error" in error column, got %s' % children)
    children = columns['exception'].childNodes
    if len(children) != 1 or children[0].data != 'Exception':
        sys.exit('expected text "Exception" in exception column, got %s' % children)

    rundefs = [
        r.getAttribute('name')
        for r in document.getElementsByTagName('rundefinition')
        if r.getAttribute('name')
    ]
    if not rundefs:
        sys.exit('no named rundefinitions found in ' + test_set_name)
    tasks = [
        t.getAttribute('name')
        for t in document.getElementsByTagName('tasks')
        if t.getAttribute('name')
    ]
    if not tasks:
        sys.exit('no named tasks found in ' + test_set_name)

    testset = test_set_name[:-len('.xml')]
    for r in rundefs:
        for t in tasks:
            f.writelines([
                '\n',
                f'{testset}.{r}.{t}:\n',
                f'  extends: .integration-tests-{"long" if long_set else "short"}\n',
                '  variables:\n',
                f'    TESTSET: {testset}\n'
                f'    RUNDEF: {r}\n',
                f'    TASKS: {t}\n',
            ])


if __name__ == '__main__':
    with open('generated-ci.yml', 'w', encoding='utf8') as f:
        f.write("include: gitlab-ci/integration-tests.yml\n")
        for test_set_name in os.listdir(test_sets_for_ci_directory):
            if not test_set_name.endswith('.xml'):
                continue
            write_jobs(f, test_set_name)
        for test_set_name in os.listdir(long_test_sets_for_ci_directory):
            if not test_set_name.endswith('.xml'):
                continue
            write_jobs(f, test_set_name, long_set=True)
