// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg;

import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.common.time.Timer;

public class SMGStateStatistics {
  // Counters for Allocated Memory
  private long heapAllocatedSize;
  private long stackAllocatedSize;
  private long externalAllocations;
  private long abstractions;
  private long stops;

  private TimeSpan stopTime;

  private SMGStateStatistics(
      long pHeapAllocatedSize,
      long pStackAllocatedSize,
      long pExternalAllocations,
      long pAbstractions,
      long pStops,
      TimeSpan pStopTime) {
    heapAllocatedSize = pHeapAllocatedSize;
    stackAllocatedSize = pStackAllocatedSize;
    externalAllocations = pExternalAllocations;
    abstractions = pAbstractions;
    stops = pStops;
    stopTime = TimeSpan.ofMillis(pStopTime.asMillis());
  }

  public SMGStateStatistics() {
    this(0, 0, 0, 0, 0, TimeSpan.ofMillis(0));
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

  public void changeStops(long pDiffStops) {
    stops += pDiffStops;
  }

  public void changeStopTime(Timer pTimer) {
    stopTime = TimeSpan.sum(stopTime, pTimer.getSumTime());
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

  public long getStops() {
    return stops;
  }

  public TimeSpan getStopTime() {
    return stopTime;
  }

  public SMGStateStatistics copy() {
    return new SMGStateStatistics(
        heapAllocatedSize, stackAllocatedSize, externalAllocations, abstractions, stops, stopTime);
  }
}
