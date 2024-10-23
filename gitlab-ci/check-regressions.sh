# This file is part of CPAchecker,
# a tool for configurable software verification:
# https://cpachecker.sosy-lab.org
#
# SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
#
# SPDX-License-Identifier: Apache-2.0


CURR=output-$TESTSET.*.results.$RUNDEF.$TASKS.xml.bz2
PREV=$TESTSET.$RUNDEF.$TASKS.previous-results.xml
TOUT=$TESTSET.$RUNDEF.$TASKS.timeouts.xml
TGFLAGS="--dump --ignore-flapping-timeout-regressions"
GREEN_COLOR='\033[0;32m'
RED_COLOR='\033[1;31m'
NO_COLOR='\033[0m'


echo "running CPAchecker on $TASKS with $RUNDEF configuration"
benchexec --read-only-dir / --hidden-dir /home \
  $CI_DIR/$TESTSET.xml -r $RUNDEF -t $TASKS -o output- || exit $?
echo -e "generate current table: $GREEN_COLOR $ python3 gitlab-ci/table-generator.py $CURR -o . -n current $NO_COLOR"
python3 gitlab-ci/table-generator.py $CURR -o . -n current

if [ -f gitlab-ci/previous-results/$PREV ] && [ -f gitlab-ci/previous-results/$TOUT ]
then
  echo "comparing against $PREV and $TOUT"
  mv gitlab-ci/previous-results/$PREV -t .
  mv gitlab-ci/previous-results/$TOUT -t .
  # table-generator checks for flaky timeouts across non-last archives it considers previous
  echo -e "$GREEN_COLOR $ python3 gitlab-ci/table-generator.py $TOUT $PREV $CURR $TGFLAGS -o . -n with-previous $NO_COLOR"
  python3 gitlab-ci/table-generator.py $TOUT $PREV $CURR $TGFLAGS -o . -n with-previous || exit $?
elif [ -f gitlab-ci/previous-results/$PREV ]
then
  echo "comparing against $PREV"
  mv gitlab-ci/previous-results/$PREV -t .
  echo -e "$GREEN_COLOR $ python3 gitlab-ci/table-generator.py $PREV $CURR $TGFLAGS -o . -n with-previous $NO_COLOR"
  python3 gitlab-ci/table-generator.py $PREV $CURR $TGFLAGS -o . -n with-previous || exit $?
else
  echo -e "$RED_COLOR no previous results found $NO_COLOR"
  echo "expected $PREV and maybe $TOUT present"
  exit 2
fi
