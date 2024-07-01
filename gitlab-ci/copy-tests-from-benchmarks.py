#  This file is part of CPAchecker,
#  a tool for configurable software verification:
#  https://cpachecker.sosy-lab.org
#
#  SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
#  SPDX-License-Identifier: Apache-2.0

# As full benchmarks are heavy, copy only the used source files in the directory gitlab-ci/test

import csv
import os
import shutil

total = 0
for csv_file in os.listdir('gitlab-ci/test-set'):
    if not csv_file.endswith('.csv'):
        continue
    with open('gitlab-ci/test-set/' + csv_file, 'r', encoding='utf8') as f:
        r = csv.reader(f, delimiter='\t')
        next(r)  # header
        i = 0
        for row in r:
            old_filename = row[0]
            if old_filename.startswith('test/programs/benchmarks') or old_filename.startswith('test/programs/ldv-benchmarks'):
                new_filename = 'gitlab-ci/' + old_filename
                os.makedirs(os.path.split(new_filename)[0], exist_ok=True)
                shutil.copy(old_filename, new_filename)
                i += 1
        print('copied', i, 'files from', csv_file)
        total += i
print('copied', total, 'files total')
