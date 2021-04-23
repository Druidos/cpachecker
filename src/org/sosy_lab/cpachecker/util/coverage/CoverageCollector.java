// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.coverage;

import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.path.PathIterator;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentMultimap;
import org.sosy_lab.cpachecker.util.AbstractStates;

/**
 * Class responsible for extracting coverage information.
 */
public abstract class CoverageCollector {

  public static CoverageData fromReachedSet(Iterable<AbstractState> pReached, CFA pCfa) {
    return new ReachedSetCoverageCollector().collectFromReachedSet(pReached, pCfa);
  }

  public static CoverageData fromCounterexample(ARGPath pPath, CFA pCfa) {
    return new CounterexampleCoverageCollector().collectFromCounterexample(pPath, pCfa);
  }

  public static CoverageData additionalInfoFromReachedSet(
      Iterable<AbstractState> pReached, CFA pCfa) {
    return new AdditionalCoverageCollector().collectFromReachedSet(pReached, pCfa);
  }

  public static CoverageData additionalInfoFromCounterexample(ARGPath pPath, CFA pCfa) {
    return new CounterexampleAdditionalCoverageCollector().collectFromCounterexample(pPath, pCfa);
  }

  static CoverageData processStateForAdditionalInfo(AbstractState state, CoverageData pCov) {
    SMGState smgState = AbstractStates.extractStateByType(state, SMGState.class);
    ARGState argState = AbstractStates.extractStateByType(state, ARGState.class);
    if (argState != null && smgState != null) {
      PersistentMap<String, SMGValue> valueMessages = smgState.getReadValues();
      PersistentMultimap<String, SMGKnownExpValue> result = PersistentMultimap.of();
      for (Entry<String, SMGValue> entry : valueMessages.entrySet()) {
        if (smgState.isExplicit(entry.getValue())) {
          result = result.putAndCopy(entry.getKey(), smgState.getExplicit(entry.getValue()));
        }
      }
      if (!result.isEmpty()) {
        for (ARGState child : argState.getParents()) {
          // Do not specially check child.isCovered, as the edge to covered state also should be
          // marked as covered edge
          List<CFAEdge> edges = child.getEdgesToChild(argState);
          for (CFAEdge innerEdge : edges) {
            pCov.addInfoOnEdge(innerEdge, result);
          }
        }
      }
    }
    return pCov;
  }
}

class CounterexampleAdditionalCoverageCollector extends CounterexampleCoverageCollector {
  @Override
  CoverageData collectFromCounterexample(ARGPath cexPath, CFA pCfa) {
    CoverageData cov = super.collectFromCounterexample(cexPath, pCfa);
    PathIterator pathIterator = cexPath.fullPathIterator();
    while (pathIterator.hasNext()) {
      AbstractState state = pathIterator.getAbstractState().getWrappedState();
      cov = CoverageCollector.processStateForAdditionalInfo(state, cov);
      pathIterator.advance();
    }
    return cov;
  }
}

class CounterexampleCoverageCollector {

  /**
   * Coverage from a counterexample does not report all existing edges, but the set of existing
   * edges needs to contain all covered edges at the minimum.
   */
  CoverageData collectFromCounterexample(ARGPath cexPath, CFA pCfa) {
    CoverageData cov = new CoverageData();
    cov.putCFA(pCfa);
    collectCoveredEdges(cexPath, cov);
    return cov;
  }

  private boolean isOutsideAssumptionAutomaton(ARGState s) {
    for (AutomatonState aState : AbstractStates.asIterable(s).filter(AutomatonState.class)) {
      if (aState.getOwningAutomatonName().equals("AssumptionAutomaton")) {
        if (aState.getInternalStateName().equals("__FALSE")) {
          return true;
        }
      }
    }
    return false;
  }

  private void collectCoveredEdges(ARGPath cexPath, CoverageData cov) {
    PathIterator pathIterator = cexPath.fullPathIterator();
    while (pathIterator.hasNext()) {
      CFAEdge edge = pathIterator.getOutgoingEdge();

      // Considering covered up until (but not including) when the
      // AssumptionAutomaton state is __FALSE.
      if (isOutsideAssumptionAutomaton(pathIterator.getNextAbstractState())) {
        break;
      }
      cov.addVisitedEdge(edge);

      CFANode location = pathIterator.getLocation();
      if (location instanceof FunctionEntryNode) {
        FunctionEntryNode entryNode = (FunctionEntryNode) location;

        final FileLocation loc = entryNode.getFileLocation();
        if (loc.getStartingLineNumber() != 0) {
          cov.addVisitedFunction(entryNode);
        }
      }

      pathIterator.advance();
    }
  }
}

class AdditionalCoverageCollector extends ReachedSetCoverageCollector {
  @Override
  CoverageData collectFromReachedSet(Iterable<AbstractState> reached, CFA cfa) {
    CoverageData cov = super.collectFromReachedSet(reached, cfa);
    for (AbstractState state : reached) {
      cov = CoverageCollector.processStateForAdditionalInfo(state, cov);
    }
    return cov;
  }
}

class ReachedSetCoverageCollector {

  CoverageData collectFromReachedSet(Iterable<AbstractState> reached, CFA cfa) {
    CoverageData cov = new CoverageData();
    cov.putCFA(cfa);

    // Add information about visited functions
    for (FunctionEntryNode entryNode :
        AbstractStates.extractLocations(reached)
            .filter(notNull())
            .filter(FunctionEntryNode.class)) {

      final FileLocation loc = entryNode.getFileLocation();
      if (loc.getStartingLineNumber() == 0) {
        // dummy location
        continue;
      }

      cov.addVisitedFunction(entryNode);
    }

    collectCoveredEdges(reached, cov);

    return cov;
  }

  private void collectCoveredEdges(Iterable<AbstractState> reached, CoverageData cov) {
    Set<CFANode> reachedNodes =
        from(reached).transform(AbstractStates::extractLocation).filter(notNull()).toSet();
    //Add information about visited locations

    for (AbstractState state : reached) {
      ARGState argState = AbstractStates.extractStateByType(state, ARGState.class);
      if (argState != null ) {
        for (ARGState child : argState.getChildren()) {
          // Do not specially check child.isCovered, as the edge to covered state also should be marked as covered edge
          List<CFAEdge> edges = argState.getEdgesToChild(child);
          if (edges.size() > 1) {
            for (CFAEdge innerEdge : edges) {
              cov.addVisitedEdge(innerEdge);
            }

            //BAM produces paths with no edge connection thus the list will be empty
          } else if (!edges.isEmpty()) {
            cov.addVisitedEdge(Iterables.getOnlyElement(edges));
          }
        }
      } else {
        //Simple kind of analysis
        //Cover all edges from reached nodes
        //It is less precise, but without ARG it is impossible to know what path we chose
        CFANode node = AbstractStates.extractLocation(state);
        for (int i = 0; i < node.getNumLeavingEdges(); i++) {
          CFAEdge edge = node.getLeavingEdge(i);
          if (reachedNodes.contains(edge.getSuccessor())) {
            cov.addVisitedEdge(edge);
          }
        }
      }
    }
  }
}