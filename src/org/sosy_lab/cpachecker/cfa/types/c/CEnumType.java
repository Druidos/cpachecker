// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.types.c;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.c.CEnumerator;

public final class CEnumType implements CComplexType {

  @Serial private static final long serialVersionUID = -986078271714119880L;

  private final CSimpleType compatibleType;
  private final ImmutableList<CEnumerator> enumerators;
  private final String name;
  private final String origName;
  private final boolean isConst;
  private final boolean isVolatile;
  private final Alignment alignment;
  private final boolean isPacked;
  private int hashCache = 0;

  public CEnumType(
      final boolean pConst,
      final boolean pVolatile,
      final Alignment pAlignment,
      final boolean pPacked,
      final CSimpleType pCompatibleType,
      final List<CEnumerator> pEnumerators,
      final String pName,
      final String pOrigName) {
    isConst = pConst;
    isVolatile = pVolatile;
    alignment = checkNotNull(pAlignment);
    isPacked = pPacked;
    compatibleType = checkNotNull(pCompatibleType);
    enumerators = ImmutableList.copyOf(pEnumerators);
    name = pName.intern();
    origName = pOrigName.intern();
  }

  @Override
  public boolean isConst() {
    return isConst;
  }

  @Override
  public boolean isVolatile() {
    return isVolatile;
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public Alignment getAlignment() {
    return alignment;
  }

  @Override
  public boolean isPacked() {
    return isPacked;
  }

  @Override
  public boolean hasKnownConstantSize() {
    return true;
  }

  /**
   * §6.7.2.2 (4) Each enumerated type shall be compatible with char, a signed integer type, or an
   * unsigned integer type. The choice of type is implementation-defined, but shall be capable of
   * representing the values of all the members of the enumeration.
   *
   * <p>GCC selects type that can represent all enumerator values. If enumerated type has no
   * negative enumerators, GCC selects unsigned type. For a packed enum GCC selects smallest
   * possible type from char, short, int, long, long long. For a not packed enum GCC selects type
   * from int, long, long long.
   *
   * <p>Enumerator of any enumerated type has type int if its value is inside int bounds. GCC allows
   * enumerators with values out of int bounds (breaks §6.7.2.2 (2)). The type for such enumerator
   * is the compatible integer type of the enum.
   *
   * <p>Note that types of enumerators may be either int or the underlying type of the enum and so
   * may differ from each other and the enum compatible type.
   *
   * @return integer type compatible with this enum
   */
  public CSimpleType getCompatibleType() {
    return compatibleType;
  }

  public ImmutableList<CEnumerator> getEnumerators() {
    return enumerators;
  }

  @Override
  public ComplexTypeKind getKind() {
    return ComplexTypeKind.ENUM;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getQualifiedName() {
    return ("enum " + name).trim();
  }

  @Override
  public String getOrigName() {
    return origName;
  }

  @Override
  public String toASTString(String pDeclarator) {
    checkNotNull(pDeclarator);
    List<String> parts = new ArrayList<>();
    parts.add(Strings.emptyToNull(alignment.stringAlignas()));
    if (isConst()) {
      parts.add("const");
    }
    if (isVolatile()) {
      parts.add("volatile");
    }
    parts.add("enum");
    parts.add(name);

    if (!pDeclarator.isEmpty()) {
      parts.add(
          "{\n  "
              + Joiner.on(",\n  ").join(transform(enumerators, CEnumerator::toASTString))
              + "\n}");
    }

    if (isPacked) {
      parts.add("__attribute__((__packed__))");
    }

    parts.add(Strings.emptyToNull(alignment.stringTypeAligned()));
    parts.add(Strings.emptyToNull(pDeclarator));
    parts.add(Strings.emptyToNull(alignment.stringVarAligned()));
    return Joiner.on(' ').skipNulls().join(parts);
  }

  @Override
  public String toString() {
    return toASTString("");
  }

  @Override
  public <R, X extends Exception> R accept(CTypeVisitor<R, X> pVisitor) throws X {
    return pVisitor.visit(this);
  }

  @Override
  public int hashCode() {
    if (hashCache == 0) {
      hashCache = Objects.hash(isConst, isVolatile, name, alignment, isPacked);
    }
    return hashCache;
  }

  /**
   * Be careful, this method compares the CType as it is to the given object, typedefs won't be
   * resolved. If you want to compare the type without having typedefs in it use
   * #getCanonicalType().equals()
   */
  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }

    return obj instanceof CEnumType other
        && isConst == other.isConst
        && isVolatile == other.isVolatile
        && alignment.equals(other.alignment)
        && isPacked == other.isPacked
        && compatibleType.equals(other.compatibleType)
        && Objects.equals(name, other.name)
        && Objects.equals(enumerators, other.enumerators);
  }

  @Override
  public boolean equalsWithOrigName(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }

    return obj instanceof CEnumType other
        && isConst == other.isConst
        && isVolatile == other.isVolatile
        && alignment.equals(other.alignment)
        && isPacked == other.isPacked
        && compatibleType.equals(other.compatibleType)
        && (Objects.equals(name, other.name) || (origName.isEmpty() && other.origName.isEmpty()))
        && Objects.equals(enumerators, other.enumerators);
  }

  @Override
  public CEnumType getCanonicalType() {
    return getCanonicalType(false, false);
  }

  @Override
  public CEnumType getCanonicalType(boolean pForceConst, boolean pForceVolatile) {
    if ((isConst == pForceConst) && (isVolatile == pForceVolatile)) {
      return this;
    }
    return new CEnumType(
        isConst || pForceConst,
        isVolatile || pForceVolatile,
        alignment,
        isPacked,
        compatibleType,
        enumerators,
        name,
        origName);
  }

  @Override
  public CType copyWithPacked(boolean pPacked) {
    if (isPacked == pPacked) {
      return this;
    }
    return new CEnumType(
        isConst, isVolatile, alignment, pPacked, compatibleType, enumerators, name, origName);
  }
}
