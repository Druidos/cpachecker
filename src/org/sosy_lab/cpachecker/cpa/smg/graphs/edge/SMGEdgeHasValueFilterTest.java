// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2025 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs.edge;

import static com.google.common.truth.Truth.assertWithMessage;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;

public class SMGEdgeHasValueFilterTest {

  private static final List<SMGEdgeHasValue> edges = new ArrayList<>();

  static {
    final List<SMGObject> objects = new ArrayList<>();
    final SMGObject object1 = new SMGRegion(31, "a");
    final SMGObject object4 = new SMGRegion(31, "a");
    final SMGObject object2 = new SMGRegion(64, "b");
    final SMGObject object3 = new SMGRegion(129, "c");
    objects.add(object1);
    objects.add(object4);
    objects.add(object2);
    objects.add(object3);

    final SMGValue v0 = SMGZeroValue.INSTANCE;
    final SMGValue v1 = SMGKnownSymValue.of();
    final SMGValue v2 = SMGKnownSymValue.of();

    final long bitstep = 8;

    for (SMGObject object : objects) {
      for (long offset = 0; offset < object.getSize(); offset += bitstep) {
        for (long endOffset = offset + bitstep;
            endOffset <= object.getSize();
            endOffset += bitstep) {
          long bitsize = endOffset - offset;
          SMGEdgeHasValue hve0 = new SMGEdgeHasValue(bitsize, offset, object, v0);
          SMGEdgeHasValue hve1 = new SMGEdgeHasValue(bitsize, offset, object, v1);
          SMGEdgeHasValue hve2 = new SMGEdgeHasValue(bitsize, offset, object, v2);
          edges.add(hve0);
          edges.add(hve1);
          edges.add(hve2);
        }
      }
    }
  }

  private static void symmetricOverlaps(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    SMGEdgeHasValueFilter overlaps1 = new SMGEdgeHasValueFilter().overlapsWith(pEdge1);
    SMGEdgeHasValueFilter overlaps2 = new SMGEdgeHasValueFilter().overlapsWith(pEdge2);

    long ofs1 = pEdge1.getOffset();
    long end1 = ofs1 + pEdge1.getSizeInBits();
    long ofs2 = pEdge2.getOffset();
    long end2 = ofs2 + pEdge2.getSizeInBits();

    boolean areOverlapping =
        pEdge1.getObject().equals(pEdge2.getObject())
            && ((ofs1 <= ofs2 && ofs2 < end1) || (ofs2 <= ofs1 && ofs1 < end2));

    String msg =
        pEdge1 + " and " + pEdge2 + (areOverlapping ? " are" : " are not") + " overlapping";
    assertWithMessage(msg).that(overlaps1.holdsFor(pEdge2)).isEqualTo(areOverlapping);
    assertWithMessage(msg).that(overlaps2.holdsFor(pEdge1)).isEqualTo(areOverlapping);
  }

  private static void symmetricObjects(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    // TODO why different constructors...
    SMGEdgeHasValueFilter f1 =
        new SMGEdgeHasValueFilter().filterByObject(pEdge1.getObject()).filterWithoutSize();
    SMGEdgeHasValueFilter f2 =
        SMGEdgeHasValueFilter.objectFilter(pEdge2.getObject()).filterWithoutSize();

    boolean haveSameObject = pEdge1.getObject().equals(pEdge2.getObject());
    String msg =
        pEdge1 + " and " + pEdge2 + (haveSameObject ? "" : " do not") + " have the same object";
    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(haveSameObject);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(haveSameObject);
  }

  private static void symmetricOffsets(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    SMGEdgeHasValueFilter f1 =
        new SMGEdgeHasValueFilter().filterAtOffset(pEdge1.getOffset()).filterWithoutSize();
    SMGEdgeHasValueFilter f2 =
        new SMGEdgeHasValueFilter().filterAtOffset(pEdge2.getOffset()).filterWithoutSize();

    boolean haveSameOffset = pEdge1.getOffset() == pEdge2.getOffset();
    String msg =
        pEdge1 + " and " + pEdge2 + (haveSameOffset ? "" : " do not") + " have the same offset";
    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(haveSameOffset);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(haveSameOffset);
  }

  private static void symmetricBitsizes(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    long bitsize1 = pEdge1.getSizeInBits();
    long bitsize2 = pEdge2.getSizeInBits();
    SMGEdgeHasValueFilter f1 = new SMGEdgeHasValueFilter().filterBySize(bitsize1);
    SMGEdgeHasValueFilter f2 = new SMGEdgeHasValueFilter().filterBySize(bitsize2);

    boolean haveSameBitsize = bitsize1 == bitsize2;
    String msg =
        pEdge1 + " and " + pEdge2 + (haveSameBitsize ? "" : " do not") + " have the same bitsize";

    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(haveSameBitsize);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(haveSameBitsize);
  }

  private static void asymmetricBitsizes(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    long bitsize1 = pEdge1.getSizeInBits();
    long bitsize2 = pEdge2.getSizeInBits();
    boolean isZero1 = pEdge1.getValue().isZero();
    boolean isZero2 = pEdge2.getValue().isZero();

    SMGEdgeHasValueFilter f1 = new SMGEdgeHasValueFilter().filterBySize(bitsize1);
    SMGEdgeHasValueFilter f2 = new SMGEdgeHasValueFilter().filterBySize(bitsize2);

    assertWithMessage(pEdge2 + " can contain " + bitsize1 + " bits")
        .that(f1.holdsFor(pEdge2))
        .isEqualTo(bitsize1 == bitsize2 || (isZero2 && bitsize1 <= bitsize2));
    assertWithMessage(pEdge1 + " can contain " + bitsize2 + " bits")
        .that(f2.holdsFor(pEdge1))
        .isEqualTo(bitsize1 == bitsize2 || (isZero1 && bitsize2 <= bitsize1));
  }

  private static void symmetricValues(SMGEdgeHasValue pEdge1, SMGEdgeHasValue pEdge2) {
    SMGValue value1 = pEdge1.getValue();
    SMGValue value2 = pEdge2.getValue();
    boolean areSameValue = value1.equals(value2);

    SMGEdgeHasValueFilter f1 =
        new SMGEdgeHasValueFilter().filterHavingValue(value1).filterWithoutSize();
    SMGEdgeHasValueFilter f2 =
        new SMGEdgeHasValueFilter().filterHavingValue(value2).filterWithoutSize();

    String msg =
        pEdge1 + " and " + pEdge2 + (areSameValue ? "" : " do not") + " have the same values";
    assertWithMessage(msg).that(f1.holdsFor(pEdge2)).isEqualTo(areSameValue);
    assertWithMessage(msg).that(f2.holdsFor(pEdge1)).isEqualTo(areSameValue);

    SMGEdgeHasValueFilter f3 =
        new SMGEdgeHasValueFilter().filterNotHavingValue(value1).filterWithoutSize();
    SMGEdgeHasValueFilter f4 =
        new SMGEdgeHasValueFilter().filterNotHavingValue(value2).filterWithoutSize();

    assertWithMessage(msg).that(f3.holdsFor(pEdge2)).isEqualTo(!areSameValue);
    assertWithMessage(msg).that(f4.holdsFor(pEdge1)).isEqualTo(!areSameValue);
  }

  @Test
  public void symmetricTest() {
    for (int i = 0; i < edges.size(); i++) {
      for (int j = i + 1; j < edges.size(); j++) {
        SMGEdgeHasValue edge1 = edges.get(i);
        SMGEdgeHasValue edge2 = edges.get(j);
        symmetricOverlaps(edge1, edge2);
        symmetricObjects(edge1, edge2);
        symmetricOffsets(edge1, edge2);
        if (edge1.getValue().isZero() || edge2.getValue().isZero()) {
          asymmetricBitsizes(edge1, edge2);
        } else {
          symmetricBitsizes(edge1, edge2);
        }
        symmetricValues(edge1, edge2);
      }
    }
  }

  @Test
  public void reflexiveTest() {
    for (SMGEdgeHasValue hve : edges) {
      // overlap
      assertWithMessage(hve + " is overlapping itself")
          .that(new SMGEdgeHasValueFilter().overlapsWith(hve).holdsFor(hve))
          .isTrue();

      // object TODO why different constructors...
      assertWithMessage(hve + " has the same object as itself")
          .that(
              new SMGEdgeHasValueFilter()
                  .filterByObject(hve.getObject())
                  .filterWithoutSize()
                  .holdsFor(hve))
          .isTrue();
      assertWithMessage(hve + " has the same object as itself")
          .that(
              SMGEdgeHasValueFilter.objectFilter(hve.getObject()).filterWithoutSize().holdsFor(hve))
          .isTrue();

      // offset
      assertWithMessage(hve + " has the same offset as itself")
          .that(
              new SMGEdgeHasValueFilter()
                  .filterAtOffset(hve.getOffset())
                  .filterWithoutSize()
                  .holdsFor(hve))
          .isTrue();

      // bitsize
      assertWithMessage(hve + " has the same bitsize as itself")
          .that(new SMGEdgeHasValueFilter().filterBySize(hve.getSizeInBits()).holdsFor(hve))
          .isTrue();

      // value
      assertWithMessage(hve + " has the same value as itself")
          .that(
              new SMGEdgeHasValueFilter()
                  .filterHavingValue(hve.getValue())
                  .filterWithoutSize()
                  .holdsFor(hve))
          .isTrue();
      assertWithMessage(hve + " has the same value as itself")
          .that(
              new SMGEdgeHasValueFilter()
                  .filterNotHavingValue(hve.getValue())
                  .filterWithoutSize()
                  .holdsFor(hve))
          .isFalse();
    }
  }
}
