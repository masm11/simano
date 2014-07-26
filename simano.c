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

static void refresh_connection(void);

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
	popup("read: %s", strerror(errno));
	refresh_connection();
	break;
    case 0:
	popup("read: Connection broken.");
	refresh_connection();
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
		popup("write: %s", strerror(errno));
		refresh_connection();
	    }
	}
    }
    
    return TRUE;
}

static void refresh_connection(void)
{
 retry:
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
    
    struct addrinfo hint, *ai;
    
    memset(&hint, 0, sizeof hint);
    hint.ai_family = AF_UNSPEC;
    hint.ai_socktype = SOCK_STREAM;
    hint.ai_flags = 0;
    
    char svc[16];
    snprintf(svc, sizeof svc, "%d", port);
    
    int err = getaddrinfo(server, svc, &hint, &ai);
    if (err != 0) {
	popup("getaddrinfo: %s", gai_strerror(err));
	goto retry;
    }
    
    sock = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
    if (sock == -1) {
	popup("socket: %s", strerror(errno));
	goto retry;
    }
    
    if (connect(sock, ai->ai_addr, ai->ai_addrlen) == -1) {
	popup("connect: %s", strerror(errno));
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
    
    g_timeout_add(60 * 1000, timeout_cb, NULL);
    
    refresh_connection();
    
    gtk_main();
    
    return 0;
}
