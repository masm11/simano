#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.69])
AC_INIT([simanod], [0.1], [masm@masm11.ddo.jp])
AC_CONFIG_SRCDIR([simanod.c])
AC_CONFIG_HEADERS([config.h])
AM_INIT_AUTOMAKE

# Checks for programs.
AC_PROG_CC

# Checks for libraries.
AC_SEARCH_LIBS(inotify_init, inotify,,
               [AC_MSG_ERROR(inotify required.)])

# Checks for header files.
AC_CHECK_HEADERS([netinet/in.h stdlib.h string.h sys/socket.h unistd.h])

# Checks for typedefs, structures, and compiler characteristics.

# Checks for library functions.
AC_FUNC_FORK
AC_CHECK_FUNCS([memset socket strchr strstr])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
