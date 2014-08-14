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
#include <libintl.h>
#include "gettext.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <locale.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <glib/gi18n.h>
#include <gtk/gtk.h>
#ifdef HAVE_NOTIFY
#include <libnotify/notify.h>
#endif

static gchar *server = NULL;
static gint port = 0;
static gchar *newmail = 
#ifdef HAVE_GNOME
	"mail-unread";
#else
#ifdef HAVE_XFCE4
	"xfce-newmail";
#endif
#endif

static gchar *nomail = 
#ifdef HAVE_GNOME
	"mail-read";
#else
#ifdef HAVE_XFCE4
	"xfce-nomail";
#endif
#endif

static int sock = -1;
static GIOChannel *channel = NULL;
static guint watch = 0;

static GtkStatusIcon *icon;
#ifdef HAVE_NOTIFY
static NotifyNotification *notification;
#endif

static void disconnect_from_server(void);
static void connect_to_server(void);

/** メッセージ dialog を作って popup する。
 * メッセージは可変長引数。
 * dialog の下部には Retry ボタンがある。
 */
static void popup(const char *fmt, ...)
{
    char msg[1024];
    
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof msg, fmt, ap);
    va_end(ap);
    
    GtkWidget *dialog = gtk_dialog_new_with_buttons(
	    _("Simple Mail Notifier"), NULL,
	    GTK_DIALOG_MODAL,
	    _("Retry"), GTK_RESPONSE_ACCEPT,
	    NULL);
    gtk_widget_show(dialog);
    
    GtkWidget *label = gtk_label_new(msg);
    gtk_box_pack_start(GTK_BOX(gtk_dialog_get_content_area(GTK_DIALOG(dialog))), label, FALSE, FALSE, 0);
    gtk_widget_show(label);
    
    gtk_dialog_run(GTK_DIALOG(dialog));
    gtk_widget_destroy(dialog);
}

/** ネットワークからの読み出し状況によって処理する。
 * 読み出しエラーの場合、EINTR なら戻る。きっとすぐまた来る。
 *   それ以外の場合は、接続を切って dialog を popup して retry。
 * 0 バイトの場合、接続が切れた。
 *   接続を切って dialog を popup して retry。
 * それ以外の場合は正常。読めた文字に対応する処理を行う。
 *   gtk-status-icon のアイコンを更新し、notification を
 *   表示する。
 */
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
	popup("read: %s", _("Connection broken."));
	connect_to_server();
	break;
    default:
	if (buf[0] == '0') {
	    gtk_status_icon_set_from_icon_name(icon, nomail);
#ifdef HAVE_NOTIFY
	    notify_notification_close(notification, NULL);
#endif
	} else {
	    gtk_status_icon_set_from_icon_name(icon, newmail);
#ifdef HAVE_NOTIFY
	    notify_notification_show(notification, NULL);
#endif
	}
	break;
    }
}

/** ソケットから(0バイト以上)読み出せる状況にある場合に呼ばれる。
 *  実際に処理は update() に移譲。
 */
static gboolean watch_cb(GIOChannel *source, GIOCondition condition, gpointer data)
{
    update();
    return TRUE;
}

/** タイマからの呼び出し関数。
 * 1バイト書き込んで、エラーがなければ良し、エラーがあれば、
 * EINTR なら無視。そうでなければサーバから切断し、dialog を
 * popup し、retry する。
 */
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

/** サーバからの接続処理を行ってに引き受ける。
 * 関連するリソースを破棄する。
 */
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

/** サーバに接続する。
 * 念の為切断処理も入れているが、あまり意味はないだろう。
 * 名前解決ができなかった場合や、接続ができなかった場合は、
 * dialog に popup して retry を行う。
 * 成功したなら、その他のリソースも確保する。
 */
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
	popup("connect: %s", _("Connection failed."));
	goto retry;
    }
    
    channel = g_io_channel_unix_new(sock);
    watch = g_io_add_watch(channel, G_IO_IN, watch_cb, NULL);
}

static GOptionEntry entries[] = {
    { "server",  's', 0, G_OPTION_ARG_STRING, &server,  N_("server hostname"),        NULL },
    { "port",    'p', 0, G_OPTION_ARG_INT,    &port,    N_("server port"),            NULL },
    { "newmail", 'N', 0, G_OPTION_ARG_STRING, &newmail, N_("icon-name for new mail"), NULL },
    { "nomail",  'n', 0, G_OPTION_ARG_STRING, &nomail,  N_("icon-name for no mail"),  NULL },
    { NULL },
};

static void usage(void)
{
    g_print("Usage: simano -s <server> -p <port>\n");
    exit(1);
}

int main(int argc, char **argv)
{
    setlocale(LC_ALL, "");
    bindtextdomain(PACKAGE, LOCALEDIR);
    bind_textdomain_codeset(PACKAGE, "UTF-8");
    textdomain(PACKAGE);
    
    if (!gtk_init_with_args(&argc, &argv, NULL, entries, NULL, NULL)) {
	g_print(_("Invalid option.\n"));
	usage();
    }
    
    if (server == NULL || port == 0)
	usage();
    
#ifdef HAVE_NOTIFY
    if (!notify_init("simano")) {
	g_print(_("Notification init error.\n"));
	usage();
    }
#endif
    
    icon = gtk_status_icon_new_from_icon_name(nomail);
    gtk_status_icon_set_tooltip_text(icon,
	    g_strdup_printf("%s:%d", server, port));
    
#ifdef HAVE_NOTIFY
#ifdef NOTIFY_CHECK_VERSION
#if NOTIFY_CHECK_VERSION(0, 7, 0)
#define NEW_ARG_3
#endif
#endif
#ifdef NEW_ARG_3
    notification = notify_notification_new(_("Mail"), _("You have new mails."), newmail);
#else
    notification = notify_notification_new(_("Mail"), _("You have new mails."), newmail, NULL);
#endif
#undef NEW_ARG_3
#endif
    
    g_timeout_add(60 * 1000, timeout_cb, NULL);
    
    connect_to_server();
    
    gtk_main();
    
    return 0;
}
