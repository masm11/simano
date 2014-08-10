bin_PROGRAMS = simanod

if USE_INOTIFY
WATCH = watch-inotify.c
else
WATCH = watch-poll.c
endif

simanod_SOURCES = simanod.c $(WATCH)
