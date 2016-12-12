/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.core.algorithm.mpa.PropertyStats;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.CounterexamplesSummary;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonExpression.ResultValue;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState.AutomatonUnknownState;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA.ControlAutomatonOptions;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatIntHist;
import org.sosy_lab.cpachecker.util.statistics.StatKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/** The TransferRelation of this CPA determines the AbstractSuccessor of a {@link AutomatonState}
 * and strengthens an {@link AutomatonState.AutomatonUnknownState}.
 */
class AutomatonTransferRelation extends SingleEdgeTransferRelation {

  private final ControlAutomatonCPA cpa;
  private final LogManager logger;
  private final AutomatonState intermediateState;
  private final ControlAutomatonOptions options;
  private @Nullable CounterexamplesSummary cexSummary;

  private static final Pattern exitFunctionNamePattern = Pattern.compile(".*check_final_state.*");

  int statNumberOfMatches = 0;
  Timer totalPostTime = new Timer();
  Timer matchTime = new Timer();
  Timer inactivityCheckTime = new Timer();
  Timer assertionsTime = new Timer();
  Timer actionTime = new Timer();
  Timer totalStrengthenTime = new Timer();
  StatIntHist automatonSuccessors = new StatIntHist(StatKind.AVG, "Automaton transfer successors");

  public AutomatonTransferRelation(ControlAutomatonCPA pCpa,
      LogManager pLogger, AutomatonState pIntermediateState, ControlAutomatonOptions pOptions)
          throws InvalidConfigurationException {

    options = pOptions;
    cpa = pCpa;
    logger = pLogger;
    intermediateState = pIntermediateState;
  }

  @Override
  public Collection<AutomatonState> getAbstractSuccessorsForEdge(
                      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
                      throws CPATransferException {

    final Collection<AutomatonState> basicResult = getAbstractSuccessorsForEdge0(pElement, pPrecision, pCfaEdge);

    List<AutomatonState> targetStates = Lists.newLinkedList();
    List<AutomatonState> firstToReturnStates = Lists.newLinkedList();

    for (AutomatonState q: basicResult) {
      if (q.isTarget()) {
        PropertyStats.INSTANCE.signalRelevancesOfProperties(q.getViolatedProperties());
        targetStates.add(q);
      } else {
        firstToReturnStates.add(q);
      }
    }

    if (options.splitOnTargetStatesToInactive) {
      if (targetStates.size() > 0) {
        // The order of the states is important (!!) because
        //    the CPAAlgorithm terminates after it has found the target state
        //    --> The target state should not be the first element here!
        firstToReturnStates.add(intermediateState);
      }
    }

    // IMPORTAT:
    //    Return non-target states first!!
    firstToReturnStates.addAll(targetStates);
    return firstToReturnStates;
  }

  public Collection<AutomatonState> getAbstractSuccessorsForEdge0(
                      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
                      throws CPATransferException {

    Preconditions.checkArgument(pElement instanceof AutomatonState);

    if (pElement instanceof AutomatonUnknownState) {
      // the last CFA edge could not be processed properly
      // (strengthen was not called on the AutomatonUnknownState or the strengthen operation had not enough information to determine a new following state.)
      AutomatonState top = cpa.getTopState();
      return Collections.singleton(top);
    }

    if (!(pCfaEdge instanceof MultiEdge)) {
      Collection<AutomatonState> result = getAbstractSuccessors0((AutomatonState)pElement, pPrecision, pCfaEdge);
      automatonSuccessors.setNextValue(result.size());
      return result;
    }

    Preconditions.checkState(!(pCfaEdge instanceof MultiEdge), "This CPA does not yet support multi-edges reliable!");

    final List<CFAEdge> edges = ((MultiEdge)pCfaEdge).getEdges();
    checkArgument(!edges.isEmpty());

    // As long as each transition produces only 0 or 1 successors,
    // we can just iterate through the edges.
    AutomatonState currentState = (AutomatonState)pElement;
    Collection<AutomatonState> currentSuccessors = null;
    int edgeIndex;
    for (edgeIndex=0; edgeIndex<edges.size(); edgeIndex++) {
      CFAEdge edge = edges.get(edgeIndex);
      currentSuccessors = getAbstractSuccessors0(currentState, pPrecision, edge);
      if (currentSuccessors.isEmpty()) {
        automatonSuccessors.setNextValue(0);
        return currentSuccessors; // bottom
      } else if (currentSuccessors.size() == 1) {
        automatonSuccessors.setNextValue(1);
        currentState = Iterables.getOnlyElement(currentSuccessors);
      } else { // currentSuccessors.size() > 1
        Preconditions.checkState(false, "Automata states with multiple successors are only supported with cfa.useMultiEdges=false! Implement the feature?");
        break;
      }
    }

    if (edgeIndex == edges.size()) {
      automatonSuccessors.setNextValue(currentSuccessors.size());
      return currentSuccessors;
    }

    // If there are two or more successors once, we use a waitlist algorithm.
    Deque<Pair<AutomatonState, Integer>> queue = new ArrayDeque<>(1);
    for (AutomatonState successor : currentSuccessors) {
      queue.addLast(Pair.of(successor, edgeIndex));
    }
    currentSuccessors.clear();

    List<AutomatonState> results = new ArrayList<>();
    while (!queue.isEmpty()) {
      Pair<AutomatonState, Integer> entry = queue.pollFirst();
      AutomatonState state = entry.getFirst();
      edgeIndex = entry.getSecond();
      CFAEdge edge = edges.get(edgeIndex);
      Integer successorIndex = edgeIndex+1;

      if (successorIndex == edges.size()) {
        // last iteration
        results.addAll(getAbstractSuccessors0(state, pPrecision, edge));

      } else {
        for (AutomatonState successor : getAbstractSuccessors0(state, pPrecision, edge)) {
          queue.addLast(Pair.of(successor, successorIndex));
        }
      }

    }

    automatonSuccessors.setNextValue(results.size());
    return results;
  }

  private Collection<AutomatonState> getAbstractSuccessors0(
      AutomatonState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException {

    Preconditions.checkArgument(pPrecision instanceof AutomatonPrecision);

    totalPostTime.start();
    try {
      if (pElement instanceof AutomatonUnknownState) {
        // happens only inside MultiEdges,
        // here we have no chance (because strengthen is called only at the end of the edge),
        // so we just stay in the previous state
        pElement = ((AutomatonUnknownState)pElement).getPreviousState();
      }

      return getFollowStates(pElement, (AutomatonPrecision) pPrecision, null, pCfaEdge, false);

    } finally {
      totalPostTime.stop();
    }
  }

  /**
   * Returns the <code>AutomatonStates</code> that follow this
   *  state in the ControlAutomatonCPA.
   *
   * If the passed <code>AutomatonExpressionArguments</code>
   *  are not sufficient to determine the following state
   *  this method returns a <code>AutomatonUnknownState</code>
   *  that contains this as previous state.
   * The strengthen method of the <code>AutomatonUnknownState</code> should
   *  be used once enough Information is available to determine the
   *  correct following state.
   *
   * If the state is a NonDet-State multiple following states may be returned.
   * If the only following state is BOTTOM an empty set is returned.
   *
   * @throws CPATransferException
   */
  private Collection<AutomatonState> getFollowStates(
      AutomatonState pState,
      AutomatonPrecision pPrecision,
      List<AbstractState> pOtherElements,
      CFAEdge pEdge, boolean pFailOnUnknownMatch)
          throws CPATransferException {

    Preconditions.checkArgument(!(pState instanceof AutomatonUnknownState));

    // BOTTOM does not have any successors
    //  (this case should not appear because the analysis
    //    should not try to compute successors for BOTTOM)
    if (pState == cpa.getBottomState()) {
      return Collections.emptySet();
    }

    // SINK state: Do not compute successor states for
    //  states without outgoing transitions!
    if (pState.getLeavingTransitions().isEmpty()) {
      // shortcut
      return Collections.singleton(pState);
    }

    final Automaton automaton = pState.getOwningAutomaton();

    Collection<AutomatonState> result = Sets.newLinkedHashSet();
    AutomatonExpressionArguments exprArgs = new AutomatonExpressionArguments(pState, pState.getVars(), pOtherElements, pEdge, logger);

    boolean atLeastOneMatch = false;
    int failedMatches = 0;
    final boolean isMatchAllState = pState.getInternalState().isNonDetState();

    // These transitions cannot be evaluated until last,
    //  because they might have side-effects on other CPAs (dont want to execute them twice).
    // TransitionVariables have to be cached (produced during the match operation).

    // Following lists holds a Transition and the corresponding TransitionVariables generated during its match
    List<Pair<AutomatonTransition, List<Map<Integer, AAstNode>>>> transitionsToBeTaken = new ArrayList<>(2);

    for (final AutomatonTransition t : pState.getLeavingTransitions()) {

      exprArgs.clearTransitionVariables();

      matchTime.start();
      ResultValue<Boolean> match = t.match(exprArgs);
      matchTime.stop();

      if (match.canNotEvaluate()) {
        // Matching NOT POSSIBLE ................................................
        if (pFailOnUnknownMatch) {
          throw new CPATransferException("Automaton transition condition could not be evaluated: " + match.getFailureMessage());
        }
        // if one transition cannot be evaluated the evaluation must be postponed until enough information is available
        return Collections.<AutomatonState>singleton(new AutomatonUnknownState(pState));

      } else if (match.getValue()) {
        // Transition MATCHES ................................................
        statNumberOfMatches++;
        atLeastOneMatch = true;

        // Collect information on relevant properties
        collectStatisticsForMatchedTransition(automaton, pState.getInternalState(), t);

        // Check if the ASSERTION holds
        assertionsTime.start();
        ResultValue<Boolean> assertionsHold = t.assertionsHold(exprArgs);
        assertionsTime.stop();

        if (assertionsHold.canNotEvaluate()) {

          if (pFailOnUnknownMatch) {
            throw new CPATransferException("Automaton transition assertions could not be evaluated: "
                + assertionsHold.getFailureMessage());
          }

          // The assertion cannot be evaluated yet.
          return Collections.<AutomatonState>singleton(new AutomatonUnknownState(pState));

        } else if (assertionsHold.getValue()) {

          if (!t.canExecuteActionsOn(exprArgs)) {
            if (pFailOnUnknownMatch) {
              throw new CPATransferException("Automaton transition action could not be executed");
            }
            // cannot yet execute, goto UnknownState
            return Collections.<AutomatonState>singleton(new AutomatonUnknownState(pState));
          }

          // delay execution as described above
          List<Map<Integer, AAstNode>> transitionVariablesSeries = ImmutableList.copyOf(exprArgs.getTransitionVariablesSeries());
          transitionsToBeTaken.add(Pair.of(t, transitionVariablesSeries));

        } else {
          // matching transitions, but unfulfilled assertions: goto error state

          Set<SafetyProperty> assertionProperties = Sets.newHashSet();
          assertionProperties.addAll(t.getViolatedWhenAssertionFailed());

          if (assertionProperties.isEmpty()) {
            assertionProperties.addAll(automaton.getPropertyFactory().createAssertionProperty(t.getAssertion()));
          }

          Map<SafetyProperty, ResultValue<?>> violatedProperties = Maps.newHashMap();
          for (SafetyProperty p : assertionProperties) {
            violatedProperties.put(p, p.instantiate(exprArgs));
          }

          AutomatonState errorState = AutomatonState.automatonStateFactory(
              Collections.<String, AutomatonVariable>emptyMap(), AutomatonInternalState.ERROR, cpa, 0, 0, false, violatedProperties);

          logger.log(Level.INFO, "Automaton going to ErrorState on edge \"" + pEdge.getDescription() + "\"");
          result.add(errorState);
        }

        if (!isMatchAllState) {
          // not a nondet State, break on the first matching edge
          break;
        }
      } else {
        // Transition does NOT MATCH ................................................
        failedMatches++;
      }
    }

    if (atLeastOneMatch) {
      // execute Transitions
      for (Pair<AutomatonTransition, List<Map<Integer, AAstNode>>> pair : transitionsToBeTaken) {

        // this transition will be taken. copy the variables
        AutomatonTransition t = pair.getFirst();
        List<Map<Integer, AAstNode>> transitionVariablesSeries = pair.getSecond();

        boolean checkFeasibility = false;

        actionTime.start();
        Map<String, AutomatonVariable> newVars = deepCloneVars(pState.getVars());
        exprArgs.setAutomatonVariables(newVars);
        exprArgs.putTransitionVariablesSeries(transitionVariablesSeries);

        for (AutomatonAction action : t.getActions()) {
          ResultValue<?> res = action.eval(exprArgs);
          if (res.canNotEvaluate()) {
            exprArgs.getLogger().log(Level.SEVERE, res.getFailureMessage() + " in " + res.getFailureOrigin());
          }

          if (action instanceof AutomatonAction.CheckFeasibility) {
            checkFeasibility = true;
          }
        }
        if (exprArgs.getLogMessage() != null && exprArgs.getLogMessage().length() > 0) {
          exprArgs.getLogger().log(Level.INFO, exprArgs.getLogMessage());
          exprArgs.clearLogMessage();
        }
        actionTime.stop();

        Map<SafetyProperty, ResultValue<?>> violatedProperties = Maps.newHashMap();
        if (t.getFollowState().isTarget()) {
          Preconditions.checkState(!t.getViolatedWhenEnteringTarget().isEmpty());
          for (SafetyProperty p : t.getViolatedWhenEnteringTarget()) {
            violatedProperties.put(p, p.instantiate(exprArgs));
          }
        }

        // The assumptions might reference to the current automata variables!
        //  --> We have to instantiate them!
        ImmutableList<Pair<AStatement, Boolean>> symbolicAssumes = t.getAssumptionWithTruth();
        exprArgs.setCFA(cpa.getCfa());
        ImmutableList<Pair<AStatement, Boolean>> instantiatedAssumes = exprArgs.instantiateAssumptions(symbolicAssumes);
        List<AAstNode> instantiatedShadowCode = exprArgs.instantiateCode(t.getShadowCode());

        // Create the new successor state of the automaton state
        AutomatonState lSuccessor = AutomatonState.automatonStateFactory(
            newVars,
            t.getFollowState(),
            cpa,
            instantiatedAssumes,
            instantiatedShadowCode,
            pState.getMatches() + 1,
            pState.getFailedMatches(),
            checkFeasibility,
            violatedProperties);

        if (!(lSuccessor instanceof AutomatonState.BOTTOM)) {
          result.add(lSuccessor);
        } else {
          // add nothing
        }
      }
      return result;
    } else {
      // stay in same state, no transitions to be executed here (no transition matched)
      final AutomatonState stateNewCounters = AutomatonState.automatonStateFactory(
          pState.getVars(), pState.getInternalState(),
          cpa, pState.getMatches(), pState.getFailedMatches() + failedMatches, false, null);
      return Collections.singleton(stateNewCounters);
    }
  }

  private void collectStatisticsForMatchedTransition(Automaton pAutomaton, AutomatonInternalState pQ,
      AutomatonTransition pT) {

    // TODO: Refactor
    //    Hacky code to get the research done...

    boolean matchesIndependentOfProperty =
               (pAutomaton.getInitialState().equals(pQ) &&  pT.getTrigger() instanceof AutomatonBoolExpr.MatchProgramEntry)
            || (pT.getFollowState().isTarget() && pT.getTrigger() instanceof AutomatonBoolExpr.MatchProgramExit)
            || (pT.getFollowState().isTarget() && triggeredByFunctionCallOrReturn(pT.getTrigger(), exitFunctionNamePattern));

    if (matchesIndependentOfProperty) {
      return;
    }

    ImmutableSet<? extends SafetyProperty> relevantFor = pAutomaton.getIsRelevantForProperties(pT);
    PropertyStats.INSTANCE.signalRelevancesOfProperties(relevantFor);
  }

  private boolean triggeredByFunctionCallOrReturn(AutomatonBoolExpr pTrigger,
      Pattern pExitfunctionnamepattern) {

    if (pTrigger instanceof AutomatonBoolExpr.MatchCFAEdgeASTComparison) {
      AutomatonBoolExpr.MatchCFAEdgeASTComparison trigger = (AutomatonBoolExpr.MatchCFAEdgeASTComparison) pTrigger;
      return pExitfunctionnamepattern.matcher(trigger.getPatternAST().getPatternString()).matches();
    }

    return false;
  }

  private static Map<String, AutomatonVariable> deepCloneVars(Map<String, AutomatonVariable> pOld) {
    Map<String, AutomatonVariable> result = Maps.newHashMapWithExpectedSize(pOld.size());
    for (Entry<String, AutomatonVariable> e : pOld.entrySet()) {
      result.put(e.getKey(), e.getValue().clone());
    }
    return result;
  }

  /* (non-Javadoc)
   * @see org.sosy_lab.cpachecker.core.interfaces.TransferRelation#strengthen(org.sosy_lab.cpachecker.core.interfaces.AbstractState, java.util.List, org.sosy_lab.cpachecker.cfa.model.CFAEdge, org.sosy_lab.cpachecker.core.interfaces.Precision)
   */
  @Override
  public Collection<AutomatonState> strengthen(AbstractState pElement,
                                    List<AbstractState> pOtherElements,
                                    CFAEdge pCfaEdge, Precision pPrecision)
                                    throws CPATransferException {

    Preconditions.checkArgument(pPrecision instanceof AutomatonPrecision);

    if (pElement instanceof AutomatonUnknownState) {

      totalStrengthenTime.start();
      AutomatonUnknownState lUnknownState = (AutomatonUnknownState)pElement;

      /*
       * Strengthening might depend on the strengthening of other automaton
       * states, so we do a fixed-point iteration.
       */
      Collection<List<AbstractState>> strengtheningCombinations = new HashSet<>();
      strengtheningCombinations.add(pOtherElements);
      boolean changed = from(pOtherElements).anyMatch(instanceOf(AutomatonUnknownState.class));
      while (changed) {
        changed = false;
        Collection<List<AbstractState>> newCombinations = new HashSet<>();
        for (List<AbstractState> otherStates : strengtheningCombinations) {
          Collection<List<AbstractState>> newPartialCombinations = new ArrayList<>();
          newPartialCombinations.add(new ArrayList<AbstractState>());
          for (AbstractState otherState : otherStates) {
            AbstractState toAdd = otherState;
            if (otherState instanceof AutomatonUnknownState) {
              AutomatonUnknownState unknownState = (AutomatonUnknownState) otherState;

              // Compute the successors of the other unknown state
              List<AbstractState> statesOtherToCurrent = new ArrayList<>(otherStates);
              statesOtherToCurrent.remove(unknownState);
              statesOtherToCurrent.add(lUnknownState);
              Collection<? extends AbstractState> successors =
                  getFollowStates(unknownState.getPreviousState(), (AutomatonPrecision) pPrecision,
                      statesOtherToCurrent, pCfaEdge, true);

              // There might be zero or more than one successor,
              // so the list of states is multiplied with the list of successors
              Collection<List<AbstractState>> multipliedPartialCrossProduct = new ArrayList<>();
              for (List<AbstractState> newOtherStates : newPartialCombinations) {
                for (AbstractState successor : successors) {
                  List<AbstractState> multipliedNewOtherStates = new ArrayList<>(newOtherStates);
                  multipliedNewOtherStates.add(successor);
                  multipliedPartialCrossProduct.add(multipliedNewOtherStates);
                }
              }
              newPartialCombinations = multipliedPartialCrossProduct;
            } else {
              // Not an (unknown) automaton state, so just add it at the end of each list
              for (List<AbstractState> newOtherStates : newPartialCombinations) {
                newOtherStates.add(toAdd);
              }
            }
          }
          newCombinations.addAll(newPartialCombinations);
        }
        changed = !strengtheningCombinations.equals(newCombinations);
        strengtheningCombinations = newCombinations;
      }

      // For each list of other states, do the strengthening
      Collection<AutomatonState> successors = new HashSet<>();
      for (List<AbstractState> otherStates : strengtheningCombinations) {
        successors.addAll(getFollowStates(lUnknownState.getPreviousState(),
            (AutomatonPrecision) pPrecision, otherStates, pCfaEdge, true));
      }
      totalStrengthenTime.stop();
      assert !from(successors).anyMatch(instanceOf(AutomatonUnknownState.class));
      return successors;
    }

    return null;
  }
}
