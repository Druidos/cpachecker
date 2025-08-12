// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Optional;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;

public class SMGHasValueEdgeSet implements SMGHasValueEdges {

  private final PersistentSortedMap<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>> map;
  private final PersistentSortedMap<SMGObject, Integer> sizesMap;
  private int size = 0;

  public SMGHasValueEdgeSet() {
    map = PathCopyingPersistentTreeMap.of();
    sizesMap = PathCopyingPersistentTreeMap.of();
  }

  private SMGHasValueEdgeSet(
      PersistentSortedMap<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>> pMap,
      PersistentSortedMap<SMGObject, Integer> pSizesMap,
      int pSize) {
    map = pMap;
    sizesMap = pSizesMap;
    size = pSize;
  }

  @Override
  public SMGHasValueEdgeSet removeAllEdgesOfObjectAndCopy(SMGObject obj) {
    PersistentSortedMap<Long, SMGEdgeHasValue> edgesForObject = map.get(obj);
    if (edgesForObject == null) {
      return this;
    }
    PersistentSortedMap<SMGObject, Integer> newSizesMap = sizesMap.removeAndCopy(obj);
    int pSize = size - sizesMap.get(obj);
    return new SMGHasValueEdgeSet(map.removeAndCopy(obj), newSizesMap, pSize);
  }

  @Override
  public SMGHasValueEdgeSet addEdgeAndCopy(SMGEdgeHasValue pEdge) {
    SMGHasValueEdgeSet result = this;
    Integer sizeForObject = sizesMap.getOrDefault(pEdge.getObject(), 0);
    PersistentSortedMap<Long, SMGEdgeHasValue> sortedByOffsets =
        map.getOrDefault(pEdge.getObject(), PathCopyingPersistentTreeMap.of());

    // Check on overlapping edges
    Entry<Long, SMGEdgeHasValue> ceilingEntry = sortedByOffsets.ceilingEntry(pEdge.getOffset());
    long endOffset = pEdge.getEndOffset();
    assert (ceilingEntry == null || endOffset <= ceilingEntry.getKey());

    Entry<Long, SMGEdgeHasValue> floorEntry = sortedByOffsets.lowerEntry(endOffset);
    assert (floorEntry == null || pEdge.getOffset() >= floorEntry.getValue().getEndOffset());

    // Merge zero edges
    if (pEdge.getValue().isZero()) {
      if (ceilingEntry != null
          && ceilingEntry.getKey() == endOffset
          && ceilingEntry.getValue().getValue().isZero()) {
        pEdge =
            new SMGEdgeHasValue(
                pEdge.getSizeInBits() + ceilingEntry.getValue().getSizeInBits(),
                pEdge.getOffset(),
                pEdge.getObject(),
                pEdge.getValue());
        result = result.removeEdgeAndCopy(ceilingEntry.getValue());
      }
      if (floorEntry != null
          && pEdge.getOffset() == floorEntry.getValue().getEndOffset()
          && floorEntry.getValue().getValue().isZero()) {
        pEdge =
            new SMGEdgeHasValue(
                pEdge.getSizeInBits() + floorEntry.getValue().getSizeInBits(),
                floorEntry.getKey(),
                pEdge.getObject(),
                pEdge.getValue());
        result = result.removeEdgeAndCopy(floorEntry.getValue());
      }
      sizeForObject = result.sizesMap.getOrDefault(pEdge.getObject(), 0);
      sortedByOffsets =
          result.map.getOrDefault(pEdge.getObject(), PathCopyingPersistentTreeMap.of());
    }

    sortedByOffsets = sortedByOffsets.putAndCopy(pEdge.getOffset(), pEdge);
    PersistentSortedMap<SMGObject, Integer> newSizesMap =
        result.sizesMap.putAndCopy(pEdge.getObject(), sizeForObject + 1);
    return new SMGHasValueEdgeSet(
        result.map.putAndCopy(pEdge.getObject(), sortedByOffsets), newSizesMap, result.size + 1);
  }

  @Override
  public SMGHasValueEdgeSet removeEdgeAndCopy(SMGEdgeHasValue pEdge) {

    int sizeForObject = sizesMap.getOrDefault(pEdge.getObject(), 0);
    PersistentSortedMap<Long, SMGEdgeHasValue> updated;
    int pSize = size;

    if (sizeForObject == 0) {
      throw new AssertionError();
    } else {
      PersistentSortedMap<Long, SMGEdgeHasValue> sortedByOffsets = map.get(pEdge.getObject());
      if (pEdge.getValue().isZero()) {
        Entry<Long, SMGEdgeHasValue> floorEntry = sortedByOffsets.floorEntry(pEdge.getOffset());
        if (floorEntry != null) {
          SMGEdgeHasValue removingEdge = floorEntry.getValue();
          if (removingEdge.getEndOffset() <= pEdge.getOffset()
              && (removingEdge.getOffset() != pEdge.getOffset())) {
            throw new AssertionError();
          } else {
            updated = sortedByOffsets.removeAndCopy(removingEdge.getOffset());
            pSize--;
            sizeForObject--;
            if (removingEdge.getOffset() < pEdge.getOffset()) {
              updated =
                  updated.putAndCopy(
                      removingEdge.getOffset(),
                      new SMGEdgeHasValue(
                          pEdge.getOffset() - removingEdge.getOffset(),
                          removingEdge.getOffset(),
                          pEdge.getObject(),
                          pEdge.getValue()));
              pSize++;
              sizeForObject++;
            }
            if (removingEdge.getEndOffset() > pEdge.getEndOffset()) {
              updated =
                  updated.putAndCopy(
                      pEdge.getEndOffset(),
                      new SMGEdgeHasValue(
                          removingEdge.getEndOffset() - pEdge.getEndOffset(),
                          pEdge.getEndOffset(),
                          pEdge.getObject(),
                          pEdge.getValue()));
              pSize++;
              sizeForObject++;
            }
          }
        } else {
          throw new AssertionError();
        }
      } else {
        updated = sortedByOffsets.removeAndCopy(pEdge.getOffset());
        pSize--;
        sizeForObject--;
      }

      if (updated == sortedByOffsets) {
        throw new AssertionError();
      } else {
        if (updated.isEmpty()) {
          return new SMGHasValueEdgeSet(
              map.removeAndCopy(pEdge.getObject()),
              sizesMap.removeAndCopy(pEdge.getObject()),
              pSize);
        } else {
          return new SMGHasValueEdgeSet(
              map.putAndCopy(pEdge.getObject(), updated),
              sizesMap.putAndCopy(pEdge.getObject(), sizeForObject),
              pSize);
        }
      }
    }
  }

  @Override
  public SMGHasValueEdgeSet getHvEdges() {
    return this;
  }

  @Override
  public Iterable<SMGEdgeHasValue> filter(SMGEdgeHasValueFilter pFilter) {
    SMGHasValueEdgeSet smgEdgeHasValues = this;
    return () -> new SMGHasValueEdgeSetIteratorWithFilter(smgEdgeHasValues, pFilter);
  }

  @Override
  public SMGHasValueEdgeSet getEdgesForObject(SMGObject pObject) {
    PersistentSortedMap<Long, SMGEdgeHasValue> edges = map.get(pObject);
    PersistentSortedMap<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>> newMap =
        PathCopyingPersistentTreeMap.of();
    PersistentSortedMap<SMGObject, Integer> newSizesMap = PathCopyingPersistentTreeMap.of();
    int newSize = 0;
    if (edges != null && !edges.isEmpty()) {
      newSize = sizesMap.get(pObject);
      newSizesMap = newSizesMap.putAndCopy(pObject, newSize);
      newMap = newMap.putAndCopy(pObject, edges);
    }
    return new SMGHasValueEdgeSet(newMap, newSizesMap, newSize);
  }

  @Override
  public Collection<SMGEdgeHasValue> getOverlappingEdges(
      SMGObject pObject, long pOffset, long pBitsize) {
    PersistentSortedMap<Long, SMGEdgeHasValue> objectEdges = map.get(pObject);
    if (objectEdges == null) {
      return ImmutableList.of();
    }

    long left = pOffset;
    long right = pOffset + pBitsize;

    Entry<Long, SMGEdgeHasValue> lowerEntry = objectEdges.lowerEntry(left);
    if (lowerEntry != null && left < lowerEntry.getValue().getEndOffset()) {
      // left-lower edge is partially covered, include it in result
      left = lowerEntry.getKey();
    }

    return Collections.unmodifiableCollection(objectEdges.subMap(left, right).values());
  }

  @Override
  public Optional<SMGEdgeHasValue> getCoveringEdge(
      SMGObject pObject, long pOffset, long pSizeInBits) {
    PersistentSortedMap<Long, SMGEdgeHasValue> objectEdges = map.get(pObject);
    if (objectEdges == null) {
      return Optional.empty();
    }

    Entry<Long, SMGEdgeHasValue> floorEntry = objectEdges.floorEntry(pOffset);
    if (floorEntry == null) {
      return Optional.empty();
    }

    SMGEdgeHasValue candidate = floorEntry.getValue();
    if (candidate.getEndOffset() < pOffset + pSizeInBits) {
      return Optional.empty();
    }

    return Optional.of(candidate);
  }

  @Override
  public Optional<SMGEdgeHasValue> getEdge(SMGObject pObject, long pOffset, long pBitsize) {
    Optional<SMGEdgeHasValue> coveringEdge = getCoveringEdge(pObject, pOffset, pBitsize);
    if (coveringEdge.isEmpty()) {
      return Optional.empty();
    }

    SMGEdgeHasValue candidate = coveringEdge.orElseThrow();
    if (candidate.getValue().isZero()) {
      // covering zero-value edge contains all smaller edges
      return Optional.of(new SMGEdgeHasValue(pBitsize, pOffset, pObject, SMGZeroValue.INSTANCE));
    }

    // non-zero value means there must be strict equality in start and end offsets
    if (candidate.getOffset() != pOffset || candidate.getSizeInBits() != pBitsize) {
      return Optional.empty();
    }

    return Optional.of(candidate);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean contains(SMGEdgeHasValue pEdge) {
    Optional<SMGEdgeHasValue> coveringEdge =
        getCoveringEdge(pEdge.getObject(), pEdge.getOffset(), pEdge.getSizeInBits());
    if (coveringEdge.isEmpty()) {
      return false;
    }

    SMGEdgeHasValue candidate = coveringEdge.orElseThrow();
    if (!candidate.getValue().equals(pEdge.getValue())) {
      // different values => different edges
      return false;
    }

    if (candidate.getOffset() == pEdge.getOffset()
        && candidate.getSizeInBits() == pEdge.getSizeInBits()) {
      // same edge
      return true;
    }

    // only 0-value candidate can contain smaller pEdge
    return candidate.getValue().isZero();
  }

  @Override
  public SMGHasValueEdgeSet addEdgesForObject(Iterable<SMGEdgeHasValue> pEdgesSet) {
    checkArgument(
        (pEdgesSet instanceof SMGHasValueEdgeSet),
        "Can't use different SMGHasValueEdges implementations");
    SMGHasValueEdgeSet edgesSet = (SMGHasValueEdgeSet) pEdgesSet;
    NavigableSet<Entry<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>>> entries =
        edgesSet.map.entrySet();
    assert entries.size() == 1;

    Entry<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>> entry = entries.first();

    assert !map.containsKey(entry.getKey());
    PersistentSortedMap<SMGObject, PersistentSortedMap<Long, SMGEdgeHasValue>> newMap =
        map.putAndCopy(entry.getKey(), entry.getValue());
    PersistentSortedMap<SMGObject, Integer> newSizesMap =
        sizesMap.putAndCopy(entry.getKey(), edgesSet.size);
    return new SMGHasValueEdgeSet(newMap, newSizesMap, size + edgesSet.size);
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public Iterable<SMGEdgeHasValue> remove(SMGHasValueEdges pEdgesToRemove) {
    SMGHasValueEdgeSet result = new SMGHasValueEdgeSet(map, sizesMap, size);
    for (SMGObject object : map.keySet()) {
      SMGHasValueEdgeSet originalEdges = getEdgesForObject(object);
      SMGHasValueEdges edgesForObjectToRemove = pEdgesToRemove.getEdgesForObject(object);
      if (!edgesForObjectToRemove.isEmpty()) {
        if (originalEdges.equals(edgesForObjectToRemove)) {
          result = result.removeAllEdgesOfObjectAndCopy(object);
        } else {
          for (SMGEdgeHasValue edge : originalEdges) {
            if (edgesForObjectToRemove.contains(edge)) {
              result = result.removeEdgeAndCopy(edge);
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    return pObj instanceof SMGHasValueEdgeSet other && map.equals(other.map);
  }

  @Override
  public String toString() {
    return map.toString();
  }

  @Override
  public AbstractIterator<SMGEdgeHasValue> iterator() {
    return new SMGHasValueEdgeSetIterator(this);
  }

  private static class SMGHasValueEdgeSetIteratorWithFilter
      extends AbstractIterator<SMGEdgeHasValue> {
    SMGEdgeHasValueFilter filter;
    Iterator<PersistentSortedMap<Long, SMGEdgeHasValue>> iteratorMain;
    Iterator<SMGEdgeHasValue> iteratorSecond;

    protected SMGHasValueEdgeSetIteratorWithFilter(
        SMGHasValueEdgeSet pSMGEdgeHasValues, SMGEdgeHasValueFilter pFilter) {
      SMGObject filterObject = pFilter.getObject();
      SMGHasValueEdgeSet filtered;

      if (filterObject != null) {
        filtered = pSMGEdgeHasValues.getEdgesForObject(filterObject);
      } else {
        filtered = pSMGEdgeHasValues;
      }

      iteratorMain = filtered.map.values().iterator();
      iteratorSecond = null;
      filter = pFilter;
    }

    @Override
    protected SMGEdgeHasValue computeNext() {
      while (iteratorMain.hasNext() || iteratorSecond != null) {
        if (iteratorSecond != null) {
          while (iteratorSecond.hasNext()) {
            SMGEdgeHasValue result = iteratorSecond.next();
            if (filter.holdsFor(result)) {
              return result;
            }
          }
          iteratorSecond = null;
        } else {
          iteratorSecond = computeSecondLevelIterator(iteratorMain.next(), filter);
        }
      }
      return endOfData();
    }

    private Iterator<SMGEdgeHasValue> computeSecondLevelIterator(
        PersistentSortedMap<Long, SMGEdgeHasValue> pMap, SMGEdgeHasValueFilter pFilter) {
      Long filterOffset = pFilter.getOffset();
      SMGValue filterValue = pFilter.getValue();
      Collection<SMGEdgeHasValue> candidateSet = ImmutableSet.of();

      if (filterOffset == null && filterValue == null) {
        return pMap.values().iterator();
      }

      if (filterOffset != null) {
        Entry<Long, SMGEdgeHasValue> candidateEntry = pMap.floorEntry(filterOffset);
        if (candidateEntry != null) {
          SMGEdgeHasValue candidate = candidateEntry.getValue();

          // we can return 'sub-edges' for 0-value HVEs
          long newSize = candidate.getEndOffset() - filterOffset;
          if (candidate.getValue().isZero() && newSize > 0) {
            candidate =
                new SMGEdgeHasValue(
                    newSize, filterOffset, candidate.getObject(), candidate.getValue());
          }

          if (pFilter.holdsFor(candidate)) {
            candidateSet = ImmutableSet.of(candidate);
            return candidateSet.iterator();
          }
        }

        return candidateSet.iterator();
      }

      return pMap.values().iterator();
    }
  }

  private static class SMGHasValueEdgeSetIterator extends AbstractIterator<SMGEdgeHasValue> {
    Iterator<PersistentSortedMap<Long, SMGEdgeHasValue>> firstLevelIterator;
    Iterator<SMGEdgeHasValue> secondLevelIterator = null;

    protected SMGHasValueEdgeSetIterator(SMGHasValueEdgeSet pSMGEdgeHasValues) {
      firstLevelIterator = pSMGEdgeHasValues.map.values().iterator();
      if (firstLevelIterator.hasNext()) {
        PersistentSortedMap<Long, SMGEdgeHasValue> next = firstLevelIterator.next();
        secondLevelIterator = next.values().iterator();
      }
    }

    @Override
    protected SMGEdgeHasValue computeNext() {
      if (secondLevelIterator == null) {
        return endOfData();
      }
      SMGEdgeHasValue result = secondLevelIterator.next();
      if (!secondLevelIterator.hasNext()) {
        if (firstLevelIterator.hasNext()) {
          secondLevelIterator = firstLevelIterator.next().values().iterator();
        } else {
          secondLevelIterator = null;
        }
      }
      return result;
    }
  }
}
