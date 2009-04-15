package cpa.art;

import java.util.ArrayList;
import java.util.List;

import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFANode;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.AbstractElementWithLocation;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.TransferRelation;
import exceptions.CPAException;
import exceptions.CPATransferException;

public class ArtTransferRelation implements TransferRelation {
  
  private final TransferRelation transferRelation;
  
  public ArtTransferRelation(TransferRelation tr) {
    transferRelation = tr;
  }
  

  @Override
  public AbstractElement getAbstractSuccessor(AbstractElement pPrevElement,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException {
//    AbstractElement abstractElement = ((ArtElement)pPrevElement).getParent();
//    ArtElement parent = (ArtElement)pPrevElement;
//    return new ArtElement((AbstractElementWithLocation)abstractElement, parent);
    // we don't use this method
    assert(false);
    return null;
  }

  @Override
  public List<AbstractElementWithLocation> getAllAbstractSuccessors(
      AbstractElementWithLocation pElement, Precision pPrecision)
      throws CPAException, CPATransferException {
    ArtElement element = (ArtElement)pElement;
    AbstractElementWithLocation wrappedElement = element.getAbstractElementOnArtNode();
    Precision wrappedPrecision = ((ArtPrecision)pPrecision).getPrecision();
    List<AbstractElementWithLocation> successors = transferRelation.getAllAbstractSuccessors(wrappedElement, wrappedPrecision);
    List<AbstractElementWithLocation> wrappedSuccessors = new ArrayList<AbstractElementWithLocation>();
    for(AbstractElementWithLocation absElement:successors){
      ArtElement successorElem = new ArtElement(absElement, element); 
      wrappedSuccessors.add(successorElem);
    }
    return wrappedSuccessors;
  }

}
