#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.69])
AC_INIT([simanod], [0.1], [masm@masm11.ddo.jp])
AC_CONFIG_SRCDIR([simanod.c])
AC_CONFIG_HEADERS([config.h])
AM_INIT_AUTOMAKE

AC_CANONICAL_HOST

# Checks for programs.
AC_PROG_CC_C99
if test "x$ac_cv_prog_cc_c99" = "xno"; then
    AC_MSG_ERROR(You need C99 compiler.)
fi

case "$host_os" in
linux-gnu)
  AC_CHECK_FUNC(inotify_init,
                [use_inotify=yes],
                [use_inotify=no])
  ;;
*)
  use_inotify=no
  ;;
esac

AM_CONDITIONAL([USE_INOTIFY], [test x$use_inotify = xyes])

# Checks for libraries.

case "$host_os" in
solaris*)
  # for getaddrinfo().
  LIBS="$LIBS -lsocket -lnsl"
  ;;
esac

# AC_SEARCH_LIBS(inotify_init, inotify,,
#                [AC_MSG_ERROR(inotify required.)])
AC_SEARCH_LIBS(pthread_create, pthread,,
               [AC_MSG_ERROR(pthread required.)])

# Checks for header files.
AC_CHECK_HEADERS([netinet/in.h stdlib.h string.h sys/socket.h unistd.h])

# Checks for typedefs, structures, and compiler characteristics.

# Checks for library functions.
AC_FUNC_FORK
AC_CHECK_FUNCS([memset socket strchr strstr getaddrinfo])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
