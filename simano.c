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
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <gtk/gtk.h>

static gchar *server = NULL;
static gint port = 0;

static int sock = -1;
static GIOChannel *channel = NULL;
static guint watch = 0;

static GtkStatusIcon *icon;

static void disconnect_from_server(void);
static void connect_to_server(void);

static void popup(const char *fmt, ...)
{
    char msg[1024];
    
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof msg, fmt, ap);
    va_end(ap);
    
    GtkWidget *dialog = gtk_dialog_new_with_buttons(
	    "Simple Mail Notifier", NULL,
	    GTK_DIALOG_MODAL,
	    "Retry", GTK_RESPONSE_ACCEPT,
	    NULL);
    gtk_widget_show(dialog);
    
    GtkWidget *label = gtk_label_new(msg);
    gtk_box_pack_start(GTK_BOX(GTK_DIALOG(dialog)->vbox), label, FALSE, FALSE, 0);
    gtk_widget_show(label);
    
    gtk_dialog_run(GTK_DIALOG(dialog));
    gtk_widget_destroy(dialog);
}

static void update(void)
{
    char buf[1];
    switch (read(sock, buf, 1)) {
    case -1:
	if (errno == EINTR)
	    return;
	disconnect_from_server();
	popup("read: %s", strerror(errno));
	connect_to_server();
	break;
    case 0:
	disconnect_from_server();
	popup("read: Connection broken.");
	connect_to_server();
	break;
    default:
	gtk_status_icon_set_visible(icon, buf[0] != '0');
	break;
    }
}

static gboolean watch_cb(GIOChannel *source, GIOCondition condition, gpointer data)
{
    update();
    return TRUE;
}

static gboolean timeout_cb(gpointer user_data)
{
    if (sock >= 0) {
	if (write(sock, "0", 1) == -1) {
	    if (errno != EINTR) {
		disconnect_from_server();
		popup("write: %s", strerror(errno));
		connect_to_server();
	    }
	}
    }
    
    return TRUE;
}

static void disconnect_from_server(void)
{
    if (watch != 0) {
	g_source_remove(watch);
	watch = 0;
    }
    if (channel != NULL) {
	g_io_channel_unref(channel);
	channel = NULL;
    }
    if (sock >= 0) {
	close(sock);
	sock = -1;
    }
}

static void connect_to_server(void)
{
 retry:
    disconnect_from_server();
    
    struct addrinfo hint, *res, *ai;
    
    memset(&hint, 0, sizeof hint);
    hint.ai_family = AF_UNSPEC;
    hint.ai_socktype = SOCK_STREAM;
    hint.ai_flags = 0;
    
    char svc[16];
    snprintf(svc, sizeof svc, "%d", port);
    
    int err = getaddrinfo(server, svc, &hint, &res);
    if (err != 0) {
	disconnect_from_server();
	popup("getaddrinfo: %s", gai_strerror(err));
	goto retry;
    }
    
    for (ai = res; ai != NULL; ai = ai->ai_next) {
	sock = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
	if (sock == -1)
	    continue;
	
	if (connect(sock, ai->ai_addr, ai->ai_addrlen) != -1)
	    break;
	
	close(sock);
	sock = -1;
    }
    
    freeaddrinfo(res);
    
    if (ai == NULL) {
	disconnect_from_server();
	popup("connect: Connection failed.");
	goto retry;
    }
    
    channel = g_io_channel_unix_new(sock);
    watch = g_io_add_watch(channel, G_IO_IN, watch_cb, NULL);
}

static GOptionEntry entries[] = {
    { "server", 's', 0, G_OPTION_ARG_STRING, &server, "server hostname", NULL },
    { "port",   'p', 0, G_OPTION_ARG_INT,    &port,   "server port",     NULL },
    { NULL },
};

static void usage(void)
{
    g_print("Usage: simano -s <server> -p <port>\n");
    exit(1);
}

int main(int argc, char **argv)
{
    if (!gtk_init_with_args(&argc, &argv, NULL, entries, NULL, NULL)) {
	g_print("Invalid option.\n");
	usage();
    }
    
    if (server == NULL || port == 0)
	usage();
    
    icon = gtk_status_icon_new_from_icon_name("xfce-newmail");
    gtk_status_icon_set_visible(icon, FALSE);
    
    g_timeout_add(60 * 1000, timeout_cb, NULL);
    
    connect_to_server();
    
    gtk_main();
    
    return 0;
}
