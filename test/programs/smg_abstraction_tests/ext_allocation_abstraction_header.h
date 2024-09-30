// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#define NULL (void*)0

typedef long unsigned int size_t;
extern void *malloc (size_t __size);
extern void *ext_allocation ();

enum { abstraction_sequence_length = 2 };