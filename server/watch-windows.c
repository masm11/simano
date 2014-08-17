#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <sys/cygwin.h>
#include <w32api/windows.h>
#include "simanod.h"
#include "check.h"
#include "watch.h"

void watch(int sock)
{
    char path_new[1024], path_cur[1024];
    
    snprintf(path_new, sizeof path_new, "%s/%s/new", getenv("HOME"), MAILDIR);
    snprintf(path_cur, sizeof path_cur, "%s/%s/cur", getenv("HOME"), MAILDIR);

    char path_win_new[1024], path_win_cur[1024];
    if (cygwin_conv_path(CCP_POSIX_TO_WIN_A, path_new, path_win_new, sizeof path_win_new) == -1) {
	perror("cygwin_conv_path");
	exit(1);
    }
    if (cygwin_conv_path(CCP_POSIX_TO_WIN_A, path_cur, path_win_cur, sizeof path_win_cur) == -1) {
	perror("cygwin_conv_path");
	exit(1);
    }

    HANDLE ch_new, ch_cur;
    ch_new = FindFirstChangeNotification(path_win_new, FALSE,
		FILE_NOTIFY_CHANGE_FILE_NAME);
    if (ch_new == INVALID_HANDLE_VALUE) {
	DWORD e = GetLastError();
	fprintf(stderr, "%s failed (0x%x).\n", path_win_new, e);
	exit(1);
    }
    ch_cur = FindFirstChangeNotification(path_win_cur, FALSE,
		FILE_NOTIFY_CHANGE_FILE_NAME);
    if (ch_cur == INVALID_HANDLE_VALUE) {
	DWORD e = GetLastError();
	fprintf(stderr, "%s failed (0x%x).\n", path_win_cur, e);
	exit(1);
    }

    int has_newmail = check(path_new, 1) || check(path_cur, 0);
    send_status(sock, has_newmail);

    while (1) {
	int changed = 0;
	HANDLE hs[2];
	hs[0] = ch_new;
	hs[1] = ch_cur;
	DWORD r = WaitForMultipleObjects(2, hs, FALSE, INFINITE);
	if (r == WAIT_OBJECT_0) {
	    if (FindNextChangeNotification(ch_new) != 0)
		changed = 1;
	} else if (r == WAIT_OBJECT_0 + 1) {
	    if (FindNextChangeNotification(ch_cur) != 0)
		changed = 1;
	} else {
	    fprintf(stderr, "WaitForMultipleObjects failed.\n");
	    exit(1);
	}
	if (changed) {
	    int new_hasnewmail = check(path_new, 1) || check(path_cur, 0);
	    if (new_hasnewmail != has_newmail) {
		has_newmail = new_hasnewmail;
		send_status(sock, has_newmail);
	    }
	}
    }
}
