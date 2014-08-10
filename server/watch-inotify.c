#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/inotify.h>
#include <poll.h>
#include <dirent.h>
#include <errno.h>
#include "simanod.h"
#include "watch.h"

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

void watch(int sock)
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
	struct pollfd fds[1];
	
	fds[0].fd = in;
	fds[0].events = POLLIN;
	fds[0].revents = 0;
	
	if (poll(fds, 1, -1) == -1) {
	    if (errno == EINTR)
		continue;
	    perror("poll");
	    exit(1);
	}
	
	if (fds[0].revents & POLLIN) {
	    char buf[sizeof(struct inotify_event) + NAME_MAX + 1];
	    
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
    }
}
