#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.69])
AC_INIT([simanod], [0.1], [masm@masm11.ddo.jp])
AC_CONFIG_SRCDIR([simanod.c])
AC_CONFIG_HEADERS([config.h])
AM_INIT_AUTOMAKE

AC_CANONICAL_HOST
case "$host_os" in
linux-gnu)
  ;;
*)
  AC_MSG_ERROR([$host_os is not supported.])
  ;;
esac

# Checks for programs.
AC_PROG_CC

# Checks for libraries.
AC_CACHE_CHECK(for inotify_init, my_cv_inotify_init,
AC_TRY_LINK([#include <sys/inotify.h>],
            [inotify_init()],
            [my_cv_inotify_init=yes],
            [my_cv_inotify_init=no]))
if test $my_cv_inotify_init != yes; then
  AC_MSG_ERROR(inotify required.)
fi

# Checks for header files.
AC_CHECK_HEADERS([netinet/in.h stdlib.h string.h sys/socket.h unistd.h])

# Checks for typedefs, structures, and compiler characteristics.

# Checks for library functions.
AC_FUNC_FORK
AC_CHECK_FUNCS([memset socket strchr strstr])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
