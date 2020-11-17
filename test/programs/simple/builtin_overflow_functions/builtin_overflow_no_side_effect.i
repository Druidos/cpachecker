# 1 "builtin_overflow_no_side_effect.c"
# 1 "<built-in>"
# 1 "<command-line>"
# 31 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 32 "<command-line>" 2
# 1 "builtin_overflow_no_side_effect.c"
# 9 "builtin_overflow_no_side_effect.c"
# 1 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 1 3 4
# 34 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 3 4
# 1 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/syslimits.h" 1 3 4






# 1 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 1 3 4
# 194 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 3 4
# 1 "/usr/include/limits.h" 1 3 4
# 26 "/usr/include/limits.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/libc-header-start.h" 1 3 4
# 33 "/usr/include/x86_64-linux-gnu/bits/libc-header-start.h" 3 4
# 1 "/usr/include/features.h" 1 3 4
# 424 "/usr/include/features.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 1 3 4
# 427 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/wordsize.h" 1 3 4
# 428 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 2 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/long-double.h" 1 3 4
# 429 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 2 3 4
# 425 "/usr/include/features.h" 2 3 4
# 448 "/usr/include/features.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 1 3 4
# 10 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/gnu/stubs-64.h" 1 3 4
# 11 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 2 3 4
# 449 "/usr/include/features.h" 2 3 4
# 34 "/usr/include/x86_64-linux-gnu/bits/libc-header-start.h" 2 3 4
# 27 "/usr/include/limits.h" 2 3 4
# 183 "/usr/include/limits.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/posix1_lim.h" 1 3 4
# 160 "/usr/include/x86_64-linux-gnu/bits/posix1_lim.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/local_lim.h" 1 3 4
# 38 "/usr/include/x86_64-linux-gnu/bits/local_lim.h" 3 4
# 1 "/usr/include/linux/limits.h" 1 3 4
# 39 "/usr/include/x86_64-linux-gnu/bits/local_lim.h" 2 3 4
# 161 "/usr/include/x86_64-linux-gnu/bits/posix1_lim.h" 2 3 4
# 184 "/usr/include/limits.h" 2 3 4



# 1 "/usr/include/x86_64-linux-gnu/bits/posix2_lim.h" 1 3 4
# 188 "/usr/include/limits.h" 2 3 4
# 195 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 2 3 4
# 8 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/syslimits.h" 2 3 4
# 35 "/usr/lib/gcc/x86_64-linux-gnu/7/include-fixed/limits.h" 2 3 4
# 10 "builtin_overflow_no_side_effect.c" 2
# 24 "builtin_overflow_no_side_effect.c"
int main()
{

    {

        int a; long long c;
        if (!(__builtin_add_overflow_p(0x7fffffff + 1l, 0x7fffffff, a))) { goto ERROR; }
        if ((__builtin_add_overflow_p(0x7fffffff + 1l, -100, a))) { goto ERROR; }
        if ((__builtin_add_overflow_p(0x7fffffff, 1, c))) { goto ERROR; }
        if (!(__builtin_add_overflow_p(0x7fffffff, 1, (int)c))) { goto ERROR; }
    }


    {

        int a; long long c;
        if (!(__builtin_sub_overflow_p(
# 40 "builtin_overflow_no_side_effect.c" 3 4
       (-0x7fffffff - 1) 
# 40 "builtin_overflow_no_side_effect.c"
       - 1l, 0x7fffffff, a))) { goto ERROR; }
        if ((__builtin_sub_overflow_p(
# 41 "builtin_overflow_no_side_effect.c" 3 4
       (-0x7fffffff - 1) 
# 41 "builtin_overflow_no_side_effect.c"
       - 1l, -100, a))) { goto ERROR; }
        if ((__builtin_sub_overflow_p(
# 42 "builtin_overflow_no_side_effect.c" 3 4
       (-0x7fffffff - 1)
# 42 "builtin_overflow_no_side_effect.c"
       , 1, c))) { goto ERROR; }
    }


    {
        int a; long long c;
        if (!(__builtin_mul_overflow_p(0x7fffffff + 1l, 1, a))) { goto ERROR; }
        if (!(__builtin_mul_overflow_p(
# 49 "builtin_overflow_no_side_effect.c" 3 4
       (-0x7fffffff - 1) 
# 49 "builtin_overflow_no_side_effect.c"
       - 1l, -1, a))) { goto ERROR; }
        if ((__builtin_mul_overflow_p(0x7fffffff, 2, c))) { goto ERROR; }
    }

    return 0;

    ERROR:
    return 1;
}
