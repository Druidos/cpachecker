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
package org.sosy_lab.cpachecker.cfa.ast;



public abstract class AFunctionCallStatement extends AStatement implements AFunctionCall {

  private final AFunctionCallExpression functionCall;

  public AFunctionCallStatement(FileLocation pFileLocation, AFunctionCallExpression pFunctionCall) {
    super(pFileLocation);
    functionCall = pFunctionCall;
  }

  @Override
  public <R, X extends Exception> R accept(AStatementVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public String toASTString() {
    return functionCall.toASTString() + ";";
  }

  @Override
  public AFunctionCallExpression getFunctionCallExpression() {
    return functionCall;
  }

  @Override
  public IAStatement asStatement() {
    return this;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((functionCall == null) ? 0 : functionCall.hashCode());
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) { return true; }
    if (obj == null) { return false; }
    if (!(obj instanceof AFunctionCallStatement)) { return false; }
    AFunctionCallStatement other = (AFunctionCallStatement) obj;
    if (functionCall == null) {
      if (other.functionCall != null) { return false; }
    } else if (!functionCall.equals(other.functionCall)) { return false; }
    return true;
  }

}