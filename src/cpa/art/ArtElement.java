package cpa.art;

import cfa.objectmodel.CFANode;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.AbstractElementWithLocation;

public class ArtElement implements AbstractElementWithLocation {

  private AbstractElementWithLocation element;
  private ArtElement parentElement;
  private int elementId;
  private static int nextArtElementId= 0;

  public ArtElement(AbstractElementWithLocation pAbstractElement, ArtElement pParentElement) {
    element = pAbstractElement;
    parentElement = pParentElement;
    elementId = ++nextArtElementId;
  }

  public ArtElement getParent(){
    return parentElement;
  }

  public AbstractElementWithLocation getAbstractElementOnArtNode(){
    return element;
  }

  @Override
  public boolean equals(Object pObj) {
    return ((ArtElement)pObj).elementId == this.elementId;
  }

  @Override
  public int hashCode() {
    return this.elementId;
  }

  @Override
  public String toString() {
    String s = "";
    s = s + "ART Element Id: " + elementId + "\n";
    if(parentElement != null){
      s = s + "Parent Element's Id: " + getParent().elementId + "\n";
    }
    else{
      s = s + "parent is null" + "\n";
    }
    s = s + element;
    return s;
  }

  @Override
  public CFANode getLocationNode() {
    return element.getLocationNode();
  }

}
