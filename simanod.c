#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/select.h>
#include <dirent.h>
#include <sys/socket.h>
#include <netinet/in.h>
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
	fd_set rfds;
	
	FD_ZERO(&rfds);
	FD_SET(sock, &rfds);
	FD_SET(in, &rfds);
	
	int fdw = (sock > in ? sock : in) + 1;
	if (select(fdw, &rfds, NULL, NULL, NULL) == -1) {
	    if (errno == EINTR)
		continue;
	    perror("select");
	    exit(1);
	}
	
	if (FD_ISSET(in, &rfds)) {
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
	
	if (FD_ISSET(sock, &rfds)) {
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
    
    int sock = socket(AF_INET6, SOCK_STREAM, 0);
    if (sock == -1) {
	perror("socket");
	exit(1);
    }
    
    struct sockaddr_in6 sa;
    memset(&sa, 0, sizeof sa);
    sa.sin6_family = AF_INET6;
    sa.sin6_port = htons(port);
    if (bind(sock, (struct sockaddr *) &sa, sizeof sa) == -1) {
	perror("bind");
	exit(1);
    }
    
    if (listen(sock, 5) == -1) {
	perror("listen");
	exit(1);
    }
    
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
	socklen_t salen = sizeof sa;
	int s = accept(sock, (struct sockaddr *) &sa, &salen);
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
	    break;
	case 0:
	    close(sock);
	    service(s);
	    break;
	default:
	    close(s);
	    break;
	}
    }
    
    return 0;
}
