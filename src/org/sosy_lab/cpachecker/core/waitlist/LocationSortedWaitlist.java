// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.waitlist;

import java.util.HashMap;
import java.util.Map;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.util.AbstractStates;

/**
 * Waitlist implementation that sorts the abstract states by the number of visits to program
 * location. States with a lower number of visit are considered first. A secondary strategy needs to
 * be given that decides what to do with states of the same number of visit.
 */
public class LocationSortedWaitlist extends AbstractSortedWaitlist<Integer> {

  private Map<LocationState, Integer> locationVisitCounts = new HashMap<>();

  protected LocationSortedWaitlist(WaitlistFactory pSecondaryStrategy) {
    super(pSecondaryStrategy);
  }

  @Override
  protected Integer getSortKey(AbstractState pState) {
    LocationState locationState = AbstractStates.extractStateByType(pState, LocationState.class);
    assert locationState != null;
    locationVisitCounts.put(locationState, locationVisitCounts.getOrDefault(locationState, 0) + 1);
    return -locationVisitCounts.get(locationState);
  }

  public static WaitlistFactory factory(final WaitlistFactory pSecondaryStrategy) {
    return () -> new LocationSortedWaitlist(pSecondaryStrategy);
  }
}
