// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.edge;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Optional;
import org.junit.Test;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGHasValueEdgeSet;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;

public class SMGEdgeHasValueFilterTest {

  private record PairSet(SMGEdgeHasValue edge1, SMGEdgeHasValue edge2, SMGHasValueEdgeSet set) {
    @Override
    public String toString() {
      return "{" + edge1 + ", " + edge2 + "}=" + set;
    }

    Optional<SMGEdgeHasValue> getMergedEdge() {
      if (set().size() == 1) {
        return Optional.of(Iterables.getOnlyElement(set()));
      }
      assertWithMessage("%s has two edges", this).that(set().size()).isEqualTo(2);
      return Optional.empty();
    }
  }

  private static final ImmutableList<SMGEdgeHasValue> edges;
  private static final ImmutableList<PairSet> pairsets;

  static {
    final ImmutableList<SMGObject> objects =
        ImmutableList.of(
            new SMGRegion(31, "a"),
            new SMGRegion(31, "a"),
            new SMGRegion(64, "b"),
            new SMGRegion(129, "c"));
    final ImmutableList<SMGKnownSymbolicValue> values =
        ImmutableList.of(SMGZeroValue.INSTANCE, SMGKnownSymValue.of(), SMGKnownSymValue.of());

    final long bitstep = 8;
    ImmutableList.Builder<SMGEdgeHasValue> edgesBuilder = ImmutableList.builder();

    for (SMGObject object : objects) {
      for (long offset = 0; offset < object.getSize(); offset += bitstep) {
        for (long endOffset = offset + bitstep;
            endOffset <= object.getSize();
            endOffset += bitstep) {
          long bitsize = endOffset - offset;
          for (SMGKnownSymbolicValue value : values) {
            edgesBuilder.add(new SMGEdgeHasValue(bitsize, offset, object, value));
          }
        }
      }
    }
    edges = edgesBuilder.build();

    final ImmutableList.Builder<PairSet> setsBuilder = ImmutableList.builder();
    for (int i1 = 0; i1 < edges.size(); i1++) {
      SMGEdgeHasValue edge1 = edges.get(i1);
      for (int i2 = i1 + 1; i2 < edges.size(); i2++) {
        SMGEdgeHasValue edge2 = edges.get(i2);
        if (edge1.getObject().equals(edge2.getObject()) && edge1.overlapsWith(edge2)) {
          // edges intersect -- cannot be in one set
          continue;
        }
        SMGHasValueEdgeSet set =
            new SMGHasValueEdgeSet().addEdgeAndCopy(edge1).addEdgeAndCopy(edge2);
        setsBuilder.add(new PairSet(edge1, edge2, set));
      }
    }
    pairsets = setsBuilder.build();
  }

  private static void symmetricObjects(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    SMGEdgeHasValueFilter f1 = new SMGEdgeHasValueFilter().filterByObject(pEdge1.getObject());
    SMGEdgeHasValueFilter f2 = new SMGEdgeHasValueFilter().filterByObject(pEdge2.getObject());

    boolean haveSameObject = pEdge1.getObject().equals(pEdge2.getObject());
    String msg =
        pEdge1 + " and " + pEdge2 + (haveSameObject ? "" : " do not") + " have the same object";
    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(haveSameObject);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(haveSameObject);
  }

  private static void symmetricValues(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    SMGValue value1 = pEdge1.getValue();
    SMGValue value2 = pEdge2.getValue();
    boolean areSameValue = value1.equals(value2);

    SMGEdgeHasValueFilter f1 = new SMGEdgeHasValueFilter().filterHavingValue(value1);
    SMGEdgeHasValueFilter f2 = new SMGEdgeHasValueFilter().filterHavingValue(value2);

    String msg =
        pEdge1 + " and " + pEdge2 + (areSameValue ? "" : " do not") + " have the same values";
    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(areSameValue);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(areSameValue);

    SMGEdgeHasValueFilter f3 = new SMGEdgeHasValueFilter().filterNotHavingValue(value1);
    SMGEdgeHasValueFilter f4 = new SMGEdgeHasValueFilter().filterNotHavingValue(value2);

    assertWithMessage(msg).that(f3.holdsFor(pEdge2)).isEqualTo(!areSameValue);
    assertWithMessage(msg).that(f4.holdsFor(pEdge1)).isEqualTo(!areSameValue);
  }

  @Test
  public void symmetricTest() {
    for (int i = 0; i < edges.size(); i++) {
      for (int j = i + 1; j < edges.size(); j++) {
        SMGEdgeHasValue edge1 = edges.get(i);
        SMGEdgeHasValue edge2 = edges.get(j);
        symmetricObjects(edge1, edge2);
        symmetricValues(edge1, edge2);
      }
    }
  }

  @Test
  public void reflexiveTest() {
    for (SMGEdgeHasValue hve : edges) {
      // object
      assertWithMessage(hve + " has the same object as itself")
          .that(new SMGEdgeHasValueFilter().filterByObject(hve.getObject()).holdsFor(hve))
          .isTrue();

      // value
      assertWithMessage(hve + " has the same value as itself")
          .that(new SMGEdgeHasValueFilter().filterHavingValue(hve.getValue()).holdsFor(hve))
          .isTrue();
      assertWithMessage(hve + " has the same value as itself")
          .that(new SMGEdgeHasValueFilter().filterNotHavingValue(hve.getValue()).holdsFor(hve))
          .isFalse();
    }
  }

  @Test
  public void fourfoldContainsTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {

        boolean doesContain = pairset.set().contains(edge);
        Optional<SMGEdgeHasValue> trifoldMatch =
            pairset.set().getEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());

        if (doesContain) {
          // for {@link SMGState#hasEdgeCorrespondedToRead(SMGReadParams, SMGValue)}
          assertWithMessage("%s has corresponding read (contains) %s", pairset, edge)
              .that(trifoldMatch)
              .hasValue(edge);

          if (edge.getValue().isZero()) {
            // zero-value edge is present if an equal or covering edge is present
            Optional<SMGEdgeHasValue> maybeCoveringEdge =
                pairset
                    .set()
                    .getCoveringEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
            assertWithMessage("%s contains a covering edge for %s", pairset, edge)
                .that(maybeCoveringEdge)
                .isPresent();
          } else {
            assertWithMessage("%s contains %s", pairset, edge).that(pairset.set()).contains(edge);
          }
        } else {
          assertWithMessage("%s does not contain %s", pairset, edge)
              .that(pairset.set())
              .doesNotContain(edge);
          // for {@link SMGState#hasEdgeCorrespondedToRead(SMGReadParams, SMGValue)}
          Optional<SMGValue> readValue = trifoldMatch.map(SMGEdgeHasValue::getValue);
          Optional<SMGValue> edgeValue = Optional.of(edge.getValue());
          assertWithMessage("%s has no corresponding read (does not contain) %s", pairset, edge)
              .that(readValue)
              .isNotEqualTo(edgeValue);
        }
      }
    }
  }

  private static boolean doesCover(SMGEdgeHasValue pCover, SMGEdgeHasValue pCovered) {
    return pCover.getObject().equals(pCovered.getObject())
        && pCover.getOffset() <= pCovered.getOffset()
        && pCovered.getEndOffset() <= pCover.getEndOffset();
  }

  private static boolean isEqualOrZeroCovers(SMGEdgeHasValue pCover, SMGEdgeHasValue pCovered) {
    if (pCover.equals(pCovered)) {
      return true;
    }
    return pCover.getValue().isZero()
        && pCovered.getValue().isZero()
        && doesCover(pCover, pCovered);
  }

  @Test
  public void containsAndCoversTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {
        boolean contains1 = isEqualOrZeroCovers(pairset.edge1(), edge);
        boolean contains2 = isEqualOrZeroCovers(pairset.edge2(), edge);
        boolean containsM =
            pairset.getMergedEdge().isPresent()
                && isEqualOrZeroCovers(pairset.getMergedEdge().orElseThrow(), edge);

        assertWithMessage("%s contains %s", pairset, edge)
            .that(pairset.set().contains(edge))
            .isEqualTo(contains1 || contains2 || containsM);

        Optional<SMGEdgeHasValue> coveringEdge =
            pairset.set().getCoveringEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
        if (containsM) {
          assertWithMessage("%s covers %s with the merged edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.getMergedEdge().orElseThrow());
        } else if (contains1) {
          assertWithMessage("%s covers %s with the first edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.edge1());
        } else if (contains2) {
          assertWithMessage("%s covers %s with the second edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.edge2());
        } else {
          // A bigger edge is not considered to contain the edge
          // even if it is covering the edge and has the same value.
          assertWithMessage("%s covers %s", pairset, edge)
              .that(coveringEdge)
              .isNotEqualTo(Optional.of(edge));
        }
      }
    }
  }

  @Test
  public void coversTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {
        boolean covers1 = doesCover(pairset.edge1(), edge);
        boolean covers2 = doesCover(pairset.edge2(), edge);
        boolean coversM =
            pairset.getMergedEdge().isPresent()
                && doesCover(pairset.getMergedEdge().orElseThrow(), edge);

        Optional<SMGEdgeHasValue> coveringEdge =
            pairset.set().getCoveringEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
        if (coversM) {
          assertWithMessage("%s covers %s with the merged edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.getMergedEdge().orElseThrow());
        } else if (covers1) {
          assertWithMessage("%s covers %s with the first edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.edge1());
        } else if (covers2) {
          assertWithMessage("%s covers %s with the second edge", pairset, edge)
              .that(coveringEdge)
              .hasValue(pairset.edge2());
        } else {
          assertWithMessage("%s covers %s", pairset, edge).that(coveringEdge).isEmpty();
        }
      }
    }
  }

  private static boolean doOverlap(SMGEdgeHasValue p1, SMGEdgeHasValue p2) {
    return p1.getObject().equals(p2.getObject())
        && p1.getOffset() < p2.getEndOffset()
        && p2.getOffset() < p1.getEndOffset();
  }

  @Test
  public void overlapsTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {
        Iterable<SMGEdgeHasValue> expected =
            Iterables.filter(pairset.set(), hve -> doOverlap(hve, edge));
        Collection<SMGEdgeHasValue> methodResult =
            pairset
                .set()
                .getOverlappingEdges(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
        assertWithMessage("edges in %s that overlap %s", pairset, edge)
            .that(methodResult)
            .containsExactlyElementsIn(expected);
      }
    }
  }

  @Test
  public void containsCoversOverlapsTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {
        Iterable<SMGEdgeHasValue> expected =
            Iterables.filter(pairset.set(), hve -> doOverlap(hve, edge));

        // instead of checking presence among overlapping edges, use #contains directly
        // test shows it has the same result:
        Optional<SMGEdgeHasValue> coveringEdge =
            pairset.set().getCoveringEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());

        if (coveringEdge.isEmpty()) {
          assertWithMessage(
                  "%s has no covering edge and does not contain %s in overlapping edges",
                  pairset, edge)
              .that(expected)
              .doesNotContain(edge);
          assertWithMessage("%s has no covering edge / does not contain %s", pairset, edge)
              .that(pairset.set().contains(edge))
              .isFalse();

        } else if (!edge.getValue().isZero() || !coveringEdge.orElseThrow().getValue().isZero()) {
          assertWithMessage("%s contains %s", pairset, edge)
              .that(pairset.set().contains(edge))
              .isEqualTo(Iterables.contains(expected, edge));

        } else if (coveringEdge.orElseThrow().equals(edge)) {
          assertWithMessage("%s has a zero-value covering edge equal to %s", pairset, edge)
              .that(expected)
              .containsExactly(edge);
          assertWithMessage("%s contains a covering zero-value edge for %s", pairset, edge)
              .that(pairset.set().contains(edge))
              .isTrue();

        } else {
          // zero-value edge can be 'present' if covered by a zero-value edge in the set
          // -- the only difference between presence in overlapping edges and in the set itself
          assertWithMessage(
                  "%s has a zero-value covering edge but does not contain %s in overlapping edges",
                  pairset, edge)
              .that(expected)
              .doesNotContain(edge);
          // cannot use assertThat(set).contains(edge) as set can contain a super-edge instead
          assertWithMessage("%s contains a covering zero-value edge for %s", pairset, edge)
              .that(pairset.set().contains(edge))
              .isTrue();
        }
      }
    }
  }

  private static boolean haveSameBits(SMGEdgeHasValue p1, SMGEdgeHasValue p2) {
    return p1.getObject().equals(p2.getObject())
        && p1.getOffset() == p2.getOffset()
        && p1.getSizeInBits() == p2.getSizeInBits();
  }

  @Test
  public void filterByObjectOffsetBitsizeTest() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {

        Optional<SMGEdgeHasValue> coveringEdge =
            pairset.set().getCoveringEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
        final Optional<SMGEdgeHasValue> expected;

        if (coveringEdge.isEmpty()) {
          expected = Optional.empty();

        } else if (haveSameBits(coveringEdge.orElseThrow(), edge)) {
          expected = coveringEdge;

        } else if (coveringEdge.orElseThrow().getValue().isZero()) {
          expected =
              Optional.of(
                  new SMGEdgeHasValue(
                      edge.getSizeInBits(),
                      edge.getOffset(),
                      edge.getObject(),
                      SMGZeroValue.INSTANCE));

        } else {
          expected = Optional.empty();
        }

        Optional<SMGEdgeHasValue> methodResult =
            pairset.set().getEdge(edge.getObject(), edge.getOffset(), edge.getSizeInBits());
        assertWithMessage("find %s in %s", edge, pairset).that(methodResult).isEqualTo(expected);
      }
    }
  }

  private static Optional<SMGEdgeHasValue> getSameStartAs(
      SMGEdgeHasValue pFilter, SMGEdgeHasValue pFiltered) {
    boolean sameObjects = pFilter.getObject().equals(pFiltered.getObject());
    if (sameObjects && pFilter.getOffset() == pFiltered.getOffset()) {
      return Optional.of(pFiltered);
    }
    if (sameObjects
        && pFiltered.getValue().isZero()
        && pFiltered.getOffset() <= pFilter.getOffset()
        && pFilter.getOffset() < pFiltered.getEndOffset()) {
      long bitsize = pFiltered.getEndOffset() - pFilter.getOffset();
      return Optional.of(
          new SMGEdgeHasValue(
              bitsize, pFilter.getOffset(), pFilter.getObject(), SMGZeroValue.INSTANCE));
    }
    return Optional.empty();
  }

  @Test
  public void filterByObjectOffsetWithoutSize() {
    for (PairSet pairset : pairsets) {
      for (SMGEdgeHasValue edge : edges) {
        final Optional<SMGEdgeHasValue> expected;

        if (pairset.getMergedEdge().isPresent()) {
          expected = getSameStartAs(edge, pairset.getMergedEdge().orElseThrow());
        } else {
          expected =
              getSameStartAs(edge, pairset.edge1()).or(() -> getSameStartAs(edge, pairset.edge2()));
        }

        Optional<SMGEdgeHasValue> methodResult =
            pairset.set().getEdge(edge.getObject(), edge.getOffset());
        assertWithMessage("find edge from %s:%s in %s", edge.getObject(), edge.getOffset(), pairset)
            .that(methodResult)
            .isEqualTo(expected);
      }
    }
  }
}
