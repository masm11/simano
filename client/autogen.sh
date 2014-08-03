#!/bin/sh
# Run this to generate all the initial makefiles, etc.

srcdir=`dirname $0`
test -z "$srcdir" && srcdir=.
abs_srcdir=`(cd $srcdir && pwd)`

PKG_NAME="simano"

#(test -f $srcdir/configure.in \
#  && test -f $srcdir/README \
#  && test -d $srcdir/xslt) || {
#    echo -n "**Error**: Directory "\`$srcdir\'" does not look like the"
#    echo " top-level $PKG_NAME directory"
#    exit 1
#}

if [ ! -x ./gnome-autogen.sh ]; then
    echo "No gnome-autogen.sh"
    exit 1
fi

# tools/ has gnome-doc-utils.m4 which is necessary to bootstrap g-d-u
ACLOCAL_FLAGS="-I $abs_srcdir/tools $ACLOCAL_FLAGS"

REQUIRED_AUTOMAKE_VERSION=1.9 USE_GNOME2_MACROS=1 . ./gnome-autogen.sh
