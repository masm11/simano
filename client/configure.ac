#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.69])
AC_INIT([simano], [0.1], [masm@masm11.ddo.jp])
AC_CONFIG_SRCDIR([simano.c])
AM_INIT_AUTOMAKE
AC_CONFIG_HEADERS([config.h])

# Checks for programs.
AC_PROG_CC
AM_PROG_CC_C_O

# Checks for libraries.

# Checks for header files.
AC_CHECK_HEADERS([netdb.h stdlib.h string.h sys/socket.h])

# Checks for typedefs, structures, and compiler characteristics.

# Checks for library functions.
AC_CHECK_FUNCS([memset socket strerror])

AM_GLIB_GNU_GETTEXT
IT_PROG_INTLTOOL

PKG_CHECK_MODULES([GTK], [gtk+-2.0 libnotify], [], [AC_MSG_ERROR([Package requirements were not met.])])
PKG_CHECK_MODULES([XFCE4], [xfce4-session-2.0], [HAVE_XFCE4=1; CFLAGS="$CFLAGS -DHAVE_XFCE4"],[AC_MSG_RESULT(no)])
PKG_CHECK_MODULES([GNOME], [gnome-vfs-2.0], [HAVE_GNOME=1; CFLAGS="$CFLAGS -DHAVE_GNOME"],[AC_MSG_RESULT(no)])
if test "$HAVE_XFCE4" != 1 -a "$HAVE_GNOME" != 1; then
  AC_MSG_ERROR([You need at least one of xfce4 or gnome.])
fi
AC_SUBST(GETTEXT_PACKAGE, simano)

AC_CONFIG_FILES([
  Makefile
  po/Makefile.in
])
AC_OUTPUT
