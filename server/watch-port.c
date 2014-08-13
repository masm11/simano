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
#include <dirent.h>
#include <poll.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <port.h>
#include "simanod.h"
#include "watch.h"

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

static void get_stamps(struct file_obj *fobj)
{
    struct stat st;
    if (stat(fobj->fo_name, &st) == -1) {
	perror(fobj->fo_name);
	exit(1);
    }
    fobj->fo_atime = st.st_atim;
    fobj->fo_mtime = st.st_mtim;
    fobj->fo_ctime = st.st_ctim;
}

void watch(int sock)
{
    struct file_obj fobj_cur, fobj_new;
    
    char path_cur[1024], path_new[1024];
    
    snprintf(path_cur, sizeof path_cur, "%s/Maildir/cur", getenv("HOME"));
    fobj_cur.fo_name = path_cur;
    
    snprintf(path_new, sizeof path_new, "%s/Maildir/new", getenv("HOME"));
    fobj_new.fo_name = path_new;
    
    get_stamps(&fobj_cur);
    get_stamps(&fobj_new);
    int has_newmail = check(path_new, 1) || check(path_cur, 0);
    send_status(sock, has_newmail);
    
    int port_cur = port_create();
    int port_new = port_create();
    
    while (1) {
	if (port_associate(port_new, PORT_SOURCE_FILE, (uintptr_t) &fobj_new,
			FILE_MODIFIED, NULL) == -1) {
	    perror("port_associate");
	    exit(1);
	}
	if (port_associate(port_cur, PORT_SOURCE_FILE, (uintptr_t) &fobj_cur,
			FILE_MODIFIED, NULL) == -1) {
	    perror("port_associate");
	    exit(1);
	}
	
	struct pollfd fds[2];
	fds[0].fd = port_new;
	fds[0].events = POLLIN;
	fds[0].revents = 0;
	fds[1].fd = port_cur;
	fds[1].events = POLLIN;
	fds[1].revents = 0;
	
	if (poll(fds, 2, -1) == -1) {
	    if (errno == EINTR)
		continue;
	    perror("poll");
	    exit(1);
	}
	
	if (fds[0].revents & POLLIN) {
	    port_event_t ev;
	    if (port_get(port_new, &ev, NULL) == -1) {
		if (errno == EINTR)
		    continue;
		perror("port_get");
		exit(1);
	    }
	}
	if (fds[1].revents & POLLIN) {
	    port_event_t ev;
	    if (port_get(port_cur, &ev, NULL) == -1) {
		if (errno == EINTR)
		    continue;
		perror("port_get");
		exit(1);
	    }
	}
	
	get_stamps(&fobj_cur);
	get_stamps(&fobj_new);
	int new_hasnewmail = check(path_new, 1) || check(path_cur, 0);
	if (new_hasnewmail != has_newmail) {
	    has_newmail = new_hasnewmail;
	    send_status(sock, has_newmail);
	}
    }
}
