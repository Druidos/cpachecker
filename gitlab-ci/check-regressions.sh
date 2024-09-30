# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0


CURR=output-$TESTSET.*.results.$RUNDEF.$TASKS.xml
PREV=$TESTSET.$RUNDEF.$TASKS.previous-results.xml
TOUT=$TESTSET.$RUNDEF.$TASKS.timeouts.xml
TGFLAGS="--dump --ignore-flapping-timeout-regressions"
GREEN_COLOR='\033[0;32m'
RED_COLOR='\033[1;31m'
NO_COLOR='\033[0m'


echo "running CPAchecker on $TASKS with $RUNDEF configuration"
benchexec --read-only-dir / --hidden-dir /home --no-compress-results \
  $CI_DIR/$TESTSET.xml -r $RUNDEF -t $TASKS -o output- || exit $?
echo -e "generate current table: $GREEN_COLOR $ table-generator $CURR -o . -n current $NO_COLOR"
table-generator $CURR -o . -n current

if [ -f gitlab-ci/previous-results/$PREV ] && [ -f gitlab-ci/previous-results/$TOUT ]
then
  echo "comparing against $PREV and $TOUT"
  mv gitlab-ci/previous-results/$PREV -t .
  mv gitlab-ci/previous-results/$TOUT -t .
  # table-generator checks for flaky timeouts across non-last archives it considers previous
  echo -e "normal comparison: $GREEN_COLOR $ table-generator $TOUT $PREV $CURR $TGFLAGS -o . -n with-previous >> stats.txt $NO_COLOR"
  table-generator $TOUT $PREV $CURR $TGFLAGS -o . -n with-previous > stats.txt
elif [ -f gitlab-ci/previous-results/$PREV ]
then
  echo "comparing against $PREV"
  mv gitlab-ci/previous-results/$PREV -t .
  # table-generator checks for flaky timeouts across non-last archives it considers previous
  echo -e "normal comparison: $GREEN_COLOR $ table-generator $PREV $CURR $TGFLAGS -o . -n with-previous >> stats.txt $NO_COLOR"
  table-generator $PREV $CURR $TGFLAGS -o . -n with-previous > stats.txt
else
  echo -e "$RED_COLOR no previous results found $NO_COLOR"
  echo "expected $PREV and maybe $TOUT present"
  exit 2
fi

cat stats.txt
python3 gitlab-ci/log-table-generator-diff.py < stats.txt || exit $?
