all:
	@echo Use make simano or make simanod.

clean:
	rm -f simano simanod

simano: simano.c
	cc -O2 -Wall `pkg-config --cflags gtk+-2.0 libnotify` -o simano simano.c `pkg-config --libs gtk+-2.0 libnotify`

simanod: simanod.c
	cc -O2 -Wall -o simanod simanod.c
