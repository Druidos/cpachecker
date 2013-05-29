/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.reachdef;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.util.reachingdef.ReachingDefUtils;

/*
 * Requires preprocessing with cil to get proper result because preprocessing guarantees that
 * 1) no two variables accessible in function f, have same name in function f
 * 2) all local variables are declared at begin of function body
 *
 * If function x is called from at least two distinct functions y and z, analysis must be done together
 * with CallstackCPA.
 */
@Options(prefix="cpa.reachdef")
public class ReachingDefCPA implements ConfigurableProgramAnalysis {

  private LogManager logger;

  private ReachingDefDomain domain;

  private ReachingDefTransferRelation transfer;

  @Option(name="merge", toUppercase=true, values={"SEP", "JOIN"},
      description="which merge operator to use for ReachingDefCPA")
  private String mergeType = "JOIN";

  @Option(name="stop", toUppercase=true, values={"SEP", "JOIN"},
      description="which stop operator to use for ReachingDefCPA")
  private String stopType = "SEP";

  private StopOperator stop;
  private MergeOperator merge;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ReachingDefCPA.class);
  }

  private ReachingDefCPA(LogManager logger, Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;

    domain = new ReachingDefDomain();
    transfer = new ReachingDefTransferRelation(logger);

    if (stopType.equals("SEP"))
      stop = new StopSepOperator(domain);
    else
      stop = new StopJoinOperator(domain);
    if (mergeType.equals("SEP"))
      merge = new MergeSepOperator();
    else
      merge = new MergeJoinOperator(domain);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transfer;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return merge;
  }

  @Override
  public StopOperator getStopOperator() {
    return stop;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return StaticPrecisionAdjustment.getInstance();
  }

  @Override
  public AbstractState getInitialState(CFANode node) {
    logger.log(Level.FINE, "Start extracting all declared variables in program.",
        "Distinguish between local and global variables.");
    Pair<Set<String>, Map<FunctionEntryNode, Set<String>>> result = ReachingDefUtils.getAllVariables(node);
    logger.log(Level.FINE, "Extracted all declared variables.", "Create initial state.");
    transfer.provideLocalVariablesOfFunctions(result.getSecond());
    transfer.setMainFunctionNode(node);
    return new ReachingDefState(result.getFirst());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return SingletonPrecision.getInstance();
  }

}
