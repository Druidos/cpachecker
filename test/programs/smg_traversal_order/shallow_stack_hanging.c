// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2024 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0



//Testing of call stack prioritization.
//Deep stack may find an error, but shallow stack will start infinite cycle
void g(void) {
    int b;
    free(&b); //error (invalid free)

}

int main(int argc, const char *argv[]) {
    int a = (int)(rand() * 8 - 4); //number between -4 and 3
    int i = 1;
    switch (a) {
        case 0:
            g();
        case 1:
            g();
        case 2:
            g();
        case 3:
            g();
        default:
            while (i)    //infinite cycle
                i++;
    }
    return 0;
}
