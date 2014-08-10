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
#include <dirent.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <sys/inotify.h>

#define MAILDIR "Maildir"

static int check(const char *path, int isnew)
{
    DIR *dir;
    
    if ((dir = opendir(path)) == NULL) {
	perror(path);
	return 0;
    }
    
    struct dirent *ep;
    int found = 0;
    while ((ep = readdir(dir)) != NULL) {
	if (isnew) {
	    if (strcmp(ep->d_name, "..") != 0 && strcmp(ep->d_name, ".") != 0)
		found = 1;
	} else {
	    char *p = strstr(ep->d_name, ":2,");
	    if (p != NULL) {
		if (strchr(p, 'S') == NULL)
		    found = 1;
	    }
	}
    }
    
    closedir(dir);
    
    return found;
}

static void send_status(int sock, int has_newmail)
{
    char data[1];
    data[0] = has_newmail ? '1' : '0';
    write(sock, data, 1);
}

static void service(int sock)
{
    char path[1024];
    
    snprintf(path, sizeof path, "%s/%s", getenv("HOME"), MAILDIR);
    
    int in = inotify_init();
    if (in == -1) {
	perror("inotify_init");
	exit(1);
    }
    
    char newdir[1024];
    snprintf(newdir, sizeof newdir, "%s/new", path);
    if (inotify_add_watch(in, newdir, IN_CREATE|IN_DELETE|IN_MOVE) == -1) {
	perror(newdir);
	exit(1);
    }
    
    char curdir[1024];
    snprintf(curdir, sizeof curdir, "%s/cur", path);
    if (inotify_add_watch(in, curdir, IN_CREATE|IN_DELETE|IN_MOVE) == -1) {
	perror(curdir);
	exit(1);
    }
    
    int has_newmail = check(newdir, 1) || check(curdir, 0);
    
    send_status(sock, has_newmail);
    
    while (1) {
	struct pollfd fds[2];
	
	fds[0].fd = sock;
	fds[0].events = POLLIN;
	fds[0].revents = 0;
	fds[1].fd = in;
	fds[1].events = POLLIN;
	fds[1].revents = 0;
	
	if (poll(fds, 2, -1) == -1) {
	    if (errno == EINTR)
		continue;
	    perror("poll");
	    exit(1);
	}
	
	if (fds[1].revents & POLLIN) {
	    char buf[1024];
	    
	    switch (read(in, buf, sizeof buf)) {
	    case -1:
		if (errno != EINTR) {
		    perror("read");
		    exit(1);
		}
		break;
		
	    case 0:
		exit(0);
		
	    default:;
		int new_hasnewmail = check(newdir, 1) || check(curdir, 0);
		if (new_hasnewmail != has_newmail) {
		    has_newmail = new_hasnewmail;
		    send_status(sock, has_newmail);
		}
	    }
	}
	
	if (fds[0].revents & POLLIN) {
	    char buf[1024];
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
    }
}

int main(int argc, char **argv)
{
    if (argc != 3) {
	fprintf(stderr, "usage: simanod -p <port>\n");
	exit(1);
    }
    int port = atoi(argv[2]);
    
    /**/
    
    int *socks = malloc(1);
    int nsocks = 0;
    
    struct addrinfo hints;
    struct addrinfo *res;
    
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE;
    int err = getaddrinfo(NULL, argv[2], &hints, &res);
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
