package org.sosy_lab.cpachecker.util.faultlocalization.appendables;

public class Hint extends FaultInfo {

  protected Hint(InfoType pType, String pDescription){
    super(pType);
    description = pDescription;
    score = 0;
  }

}
