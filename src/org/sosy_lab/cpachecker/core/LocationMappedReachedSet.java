/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core;

import java.util.Collections;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;

import com.google.common.collect.LinkedHashMultimap;

public class LocationMappedReachedSet extends ReachedElements {

  private final LinkedHashMultimap<CFANode, AbstractElement> locationMappedReached = LinkedHashMultimap.create();

  public LocationMappedReachedSet(TraversalMethod traversal) {
    super(traversal);
  }
  
  @Override
  public void add(AbstractElement pElement, Precision pPrecision) {
    super.add(pElement, pPrecision);
    
    CFANode location = getLocationFromElement(pElement);
    assert location != null : "Location information necessary for LocationMappedReachedSet";
    locationMappedReached.put(location, pElement);
  }
  
  @Override
  public void remove(AbstractElement pElement) {
    super.remove(pElement);
    
    CFANode location = getLocationFromElement(pElement);
    assert location != null : "Location information necessary for LocationMappedReachedSet";
    locationMappedReached.remove(location, pElement);
  }
  
  @Override
  public void clear() {
    super.clear();
    
    locationMappedReached.clear();
  }
  
  @Override
  public Set<AbstractElement> getReached(AbstractElement element) {
    CFANode loc = getLocationFromElement(element);
    return getReached(loc);
  }

  @Override
  public Set<AbstractElement> getReached(CFANode location) {
    return Collections.unmodifiableSet(locationMappedReached.get(location));
  }
}
