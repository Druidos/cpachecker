// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.c;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serial;
import java.math.BigInteger;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.AbstractSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;

public final class CEnumerator extends AbstractSimpleDeclaration implements CSimpleDeclaration {

  @Serial private static final long serialVersionUID = -2526725372840523651L;

  private final BigInteger value;
  private @Nullable CEnumType enumType;
  private @Nullable CSimpleType actualType;
  private final String qualifiedName;

  public CEnumerator(
      final FileLocation pFileLocation,
      final String pName,
      final String pQualifiedName,
      final BigInteger pValue,
      final @Nullable CSimpleType pActualType) {
    super(pFileLocation, pName);
    checkNotNull(pName);
    value = checkNotNull(pValue);
    qualifiedName = checkNotNull(pQualifiedName);
    actualType = pActualType;
  }

  /** Get the enum that declared this enumerator. */
  public CEnumType getEnum() {
    return enumType;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    return obj instanceof CEnumerator other
        && super.equals(obj)
        && value.equals(other.value)
        && qualifiedName.equals(other.qualifiedName);
    // do not compare the enumType, comparing it with == is wrong because types which
    // are the same but not identical would lead to wrong results
    // comparing it with equals is no good choice, too. This would lead to a stack
    // overflow
    //  && (enumType == other.enumType);
    // enumerator have to have unique names anyway
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, enumType, qualifiedName) * 31 + super.hashCode();
  }

  /** This method should be called only during parsing. */
  public void setEnum(CEnumType pEnumType) {
    checkState(enumType == null);
    enumType = checkNotNull(pEnumType);
    if (actualType == null) {
      // if value does not fit in int, it is of the enum-compatible bigger type
      actualType = enumType.getCompatibleType();
    }
  }

  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  /**
   * С11 §6.7.2.2 (2) The expression that defines the value of an enumeration constant shall be an
   * integer constant expression that has a value representable as an int.
   *
   * <p>С11 §6.7.2.2 (3) The identifiers in an enumerator list are declared as constants that have
   * type int and may appear wherever such are permitted.
   *
   * <p>Thus, enumerator of any enumerated type has type <code>int</code> if its value is inside
   * <code>int</code> bounds. GCC allows enumerators with values out of <code>int</code> bounds
   * (breaks (2)). The type for such enumerator is the compatible integer type of the enum.
   *
   * @return <code>int</code> or the enum-compatible simple numeric type.
   */
  @Override
  public CSimpleType getType() {
    // actual type is null only if it is not int and has no enum type set
    checkState(actualType != null, "enumerator has no enum type");
    return actualType;
  }

  public BigInteger getValue() {
    return value;
  }

  @Override
  public String toASTString() {
    return getQualifiedName().replace("::", "__") + " = " + value;
  }

  @Override
  public <R, X extends Exception> R accept(CSimpleDeclarationVisitor<R, X> pV) throws X {
    return pV.visit(this);
  }

  @Override
  public <R, X extends Exception> R accept(CAstNodeVisitor<R, X> pV) throws X {
    return pV.visit(this);
  }
}
