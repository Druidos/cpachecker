#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

# As full benchmarks are heavy, copy only the used source files in the directory gitlab-ci/test

import os
import shutil
from defusedxml.minidom import parse
from glob import glob
import yaml


test_sets_for_ci_directory = 'test/test-sets/gitlab-ci/'
long_test_sets_for_ci_directory = 'test/test-sets/gitlab-ci/long/'


def copy_files(filelist):
    global copied_files_count, read_files_count
    worklist = set(filelist)
    while worklist:
        waitlist = []
        print('working on', len(worklist), 'filepaths')
        for file in worklist:
            if '*' in file:
                waitlist += glob(file)
                continue
            if file.startswith('test/programs/benchmarks/') \
                    or file.startswith('test/programs/ldv-benchmarks/'):
                copied = os.path.join('gitlab-ci', file)
                os.makedirs(os.path.split(copied)[0], exist_ok=True)
                shutil.copy(file, copied)
                copied_files_count += 1

            cur_dir = os.path.split(file)[0]
            if file.endswith('.set'):
                with open(file, 'r', encoding='utf8') as f:
                    for line in f.readlines():
                        line = line.split('#')[0].strip()
                        if line:
                            waitlist.append(os.path.normpath(os.path.join(cur_dir, line)))
                    read_files_count += 1
            elif file.endswith('.yml'):
                with open(file, 'r', encoding='utf8') as f:
                    task_desc = yaml.safe_load(f)
                    waitlist.append(
                        os.path.normpath(os.path.join(cur_dir, task_desc['input_files'])))
                    waitlist += [
                        os.path.normpath(os.path.join(cur_dir, p['property_file']))
                        for p in task_desc['properties']
                    ]
                    read_files_count += 1
            elif file.endswith('.c') or file.endswith('.i') or file.endswith('.h') \
                    or file.endswith('.prp'):
                pass
            else:
                print('unexpected file seen: ' + file)
        worklist = set(waitlist)


if __name__ == '__main__':
    total_copied_files_count = 0
    total_read_files_count = 0
    for directory in [test_sets_for_ci_directory, long_test_sets_for_ci_directory]:
        copied_files_count = 0
        read_files_count = 0
        for test_set_name in os.listdir(directory):
            if not test_set_name.endswith('.xml'):
                continue
            document = parse(directory + test_set_name)
            files = [
                # TODO account for exclude tag
                # TODO copy only files that have a property as in task
                os.path.normpath(directory + textNode.data)
                for filesTag in ['propertyfile', 'includesfile', 'requiredfiles', 'include']
                for node in document.getElementsByTagName(filesTag)
                for textNode in node.childNodes
            ]
            print(test_set_name, end=':\n')
            copy_files(files)
            print(read_files_count, 'files read')
            print(copied_files_count, 'files copied')
            print()
            total_read_files_count += read_files_count
            total_copied_files_count += copied_files_count
    print(total_read_files_count, 'files read')
    print(total_copied_files_count, 'files copied')
