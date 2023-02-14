// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg;

public class SMGStateStatistics {
  // Counters for Allocated Memory
  private long heapAllocatedSize;
  private long stackAllocatedSize;
  private long externalAllocations;
  private long abstractions;

  private SMGStateStatistics(
      long pHeapAllocatedSize,
      long pStackAllocatedSize,
      long pExternalAllocations,
      long pAbstractions) {
    heapAllocatedSize = pHeapAllocatedSize;
    stackAllocatedSize = pStackAllocatedSize;
    externalAllocations = pExternalAllocations;
    abstractions = pAbstractions;
  }

  public SMGStateStatistics() {
    this(0, 0, 0, 0);
  }

  public void changeHeapAllocatedSize(long pDiffAllocatedSize) {
    heapAllocatedSize += pDiffAllocatedSize;
  }

  public void changeStackAllocatedSize(long pDiffStackAllocatedSize) {
    stackAllocatedSize += pDiffStackAllocatedSize;
  }

  public void changeExternalAllocations(long pDiffExternalAllocations) {
    externalAllocations += pDiffExternalAllocations;
  }

  public void changeAbstractions(long pDiffAbstractions) {
    abstractions += pDiffAbstractions;
  }

  public long getHeapAllocatedSize() {
    return heapAllocatedSize;
  }

  public long getStackAllocatedSize() {
    return stackAllocatedSize;
  }

  public long getExternalAllocations() {
    return externalAllocations;
  }

  public long getAbstractions() {
    return abstractions;
  }

  public SMGStateStatistics copy() {
    return new SMGStateStatistics(
        heapAllocatedSize, stackAllocatedSize, externalAllocations, abstractions);
  }
}
