#
#
#

SUBDIRS = po

bin_PROGRAMS = simano

simano_SOURCES = simano.c

simano_CFLAGS = $(GTK_CFLAGS) $(NOTIFY_CFLAGS) -DLOCALEDIR=\"$(localedir)\"
simano_LDADD = $(GTK_LIBS) $(NOTIFY_LIBS)
