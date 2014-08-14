bin_PROGRAMS = simanod

if USE_INOTIFY
WATCH = watch-inotify.c
else
if USE_PORT
WATCH = watch-port.c
else
WATCH = watch-poll.c
endif
endif

simanod_SOURCES = simanod.c simanod.h $(WATCH) watch.h check.c check.h
