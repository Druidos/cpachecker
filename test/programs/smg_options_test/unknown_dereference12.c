// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

struct device {
  struct device *field;
};

int main() {
  struct device *dev = malloc(sizeof(struct device));
  struct device *priv = dev->field; // unknown
  priv->field = 0; // unknown deref -- not reported
  if (priv->field) {
    free(priv->field); // unreachable (except UB above), but reported
  }
  free(dev);
  return 0; // no memleak, no wrong free
}
