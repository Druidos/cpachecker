// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.object;

public enum AllocationColor {
  COLORLESS,
  GREEN,
  RED;

  /** GREEN memory cannot store pointers to RED memory. */
  public boolean canStorePointerWithColor(AllocationColor pColor) {
    return (this != GREEN || pColor != RED);
  }
}
