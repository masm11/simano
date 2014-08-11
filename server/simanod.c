/*
    Simple Mail Notification
    Copyright (C) 2014 Yuuki Harano

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <poll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <pthread.h>
#include "watch.h"
#include "simanod.h"

void send_status(int sock, int has_newmail)
{
    char data[1];
    data[0] = has_newmail ? '1' : '0';
    write(sock, data, 1);
}

static void *recv_keepalive(void *parm)
{
    int sock = *(int *) parm;
    
    while (1) {
	char buf[32];
	switch (read(sock, buf, sizeof buf)) {
	case 0:
	    exit(1);
	case -1:
	    if (errno != EINTR) {
		perror("read");
		exit(1);
	    }
	    break;
	}
    }
    
    return NULL;
}

static void service(int sock)
{
    pthread_t thr;
    pthread_attr_t attr;
    memset(&attr, 0, sizeof attr);
    int err = pthread_create(&thr, &attr, recv_keepalive, &sock);
    if (err != 0) {
	fprintf(stderr, "pthread_create: %s\n", strerror(err));
	exit(1);
    }
    
    watch(sock);
}

static void usage(void)
{
    fprintf(stderr, "usage: simanod [-d] -p <port>\n");
    fprintf(stderr, "  -d         debug mode\n");
    fprintf(stderr, "  -p <port>  port number\n");
    exit(1);
}

int main(int argc, char **argv)
{
    const char *port = NULL;
    int opt;
    int debug = 0;
    
    while ((opt = getopt(argc, argv, "p:d")) != -1) {
	switch (opt) {
	case 'p':
	    port = optarg;
	    break;
	case 'd':
	    debug = 1;
	    break;
	default:
	    usage();
	}
    }
    
    if (port == NULL)
	usage();
    
    /**/
    
    int *socks = malloc(1);
    int nsocks = 0;
    
    struct addrinfo hints;
    struct addrinfo *res;
    
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE;
    int err = getaddrinfo(NULL, port, &hints, &res);
    if (err != 0) {
	fprintf(stderr, "getaddrinfo(): %s\n", gai_strerror(err));
	exit(1);
    }
    
    for (struct addrinfo *ai = res; ai != NULL; ai = ai->ai_next) {
	int sock = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
	if (sock == -1) {
	    perror("socket");
	    continue;
	}
	
	if (ai->ai_family == AF_INET6) {
	    int opt = 1;
	    if (setsockopt(sock, IPPROTO_IPV6, IPV6_V6ONLY, &opt, sizeof opt) == -1)
		perror("setsockopt");
	}
	
	if (bind(sock, ai->ai_addr, ai->ai_addrlen) == -1) {
	    perror("bind");
	    close(sock);
	    continue;
	}
	
	if (listen(sock, 5) == -1) {
	    perror("listen");
	    close(sock);
	    continue;
	}
	
	socks = realloc(socks, (nsocks + 1) * sizeof *socks);
	if (socks == NULL) {
	    fprintf(stderr, "realloc: out of memory.\n");
	    exit(1);
	}
	socks[nsocks++] = sock;
    }
    if (nsocks == 0) {
	fprintf(stderr, "No socket available.\n");
	exit(1);
    }
    
    /**/
    
    struct sigaction act;
    
    memset(&act, 0, sizeof act);
    act.sa_handler = SIG_DFL;
    act.sa_flags = SA_NOCLDWAIT;
    sigaction(SIGCHLD, &act, NULL);
    
    memset(&act, 0, sizeof act);
    act.sa_handler = SIG_IGN;
    sigaction(SIGPIPE, &act, NULL);
    
    if (!debug)
	daemon(0, 0);
    
    while (1) {
	struct pollfd fds[nsocks];
	
	for (int i = 0; i < nsocks; i++) {
	    fds[i].fd = socks[i];
	    fds[i].events = POLLIN;
	    fds[i].revents = 0;
	}
	
	if (poll(fds, nsocks, -1) == -1) {
	    if (errno == EINTR)
		continue;
	    perror("poll");
	    exit(1);
	}
	
	for (int i = 0; i < nsocks; i++) {
	    if (fds[i].revents & POLLIN) {
		int s = accept(socks[i], NULL, NULL);
		if (s == -1) {
		    if (errno != EINTR) {
			perror("accept");
			exit(1);
		    }
		    
		    continue;
		}
		
		switch (fork()) {
		case -1:
		    perror("fork");
		    close(s);
		    break;
		case 0:
		    for (int i = 0; i < nsocks; i++)
			close(socks[i]);
		    service(s);
		    break;
		default:
		    close(s);
		    break;
		}
	    }
	}
    }
    
    return 0;
}
