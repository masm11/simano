#!/usr/bin/env ruby
# -*- coding: utf-8 -*-

require 'optparse'
require 'socket'
require 'gtk3'
require 'libnotify'
require 'resolv-replace'

@hostname = nil
@port = nil
@sock = nil
@icon = nil
@notify = nil
@last_write_time = Time.now
@newmail = 'xfce-newmail'
@nomail = 'xfce-nomail'

def popup(fmt, *args)
  msg = sprintf fmt, *args
  caller.each do |s|
    msg += "\n"
    msg += s
  end
  
  dialog = Gtk::MessageDialog.new(:parent => nil,
                                  :flags => :modal,
                                  :type => :info,
                                  :buttons_type => :ok,
                                  :message => msg)
  dialog.show
  dialog.run
  dialog.destroy
end

####

def update
  begin
    buf = @sock.read(1)
  rescue Errno::EINTR
    retry
  rescue => e
    disconnect_from_server
    popup("read: %s", e.message)
    connect_to_server
    return
  end
  
  if !buf || buf == ''
    disconnect_from_server
    popup('read: %s', '接続が切れました。')
    connect_to_server
    return
  end
  
  buf.each_char do |c|
    if c == '0'
      @icon.icon_name = @nomail
      if @notify
        @notify.close
      end
    else
      @icon.icon_name = @newmail
      unless @notify
        @notify = Libnotify.new(:summary => 'メール通知',
                                :icon_path => @newmail,
                                :body => '新着メールがあります。')
      end
      @notify.show!
    end
  end
end

def disconnect_from_server
  if @channel
    GLib::Source.remove @watch_id
    @channel.close
    @channel = nil
    @sock = nil
  end
end

def connect(host, port)
  dns = Resolv::DNS.open
  addrs = dns.getaddresses host
  dns.close
  raise "Unknown host: #{host}" if addrs.size == 0
  
  # IPv6 を先に。あとは文字列順。
  addrs.sort! do |a, b|
    astr = a.to_s
    bstr = b.to_s
    ret = 0
    if /\./ =~ astr && /:/ =~ bstr
      ret = 1
    elsif /:/ =~ astr && /\./ =~ bstr
      ret = -1
    else
      ret = astr <=> bstr
    end
    ret
  end
  
  ex = nil
  addrs.each do |addr|
    begin
      return TCPSocket.open addr.to_s, port
    rescue => e
      ex = e
    end
  end
  raise ex
end

def connect_to_server
  disconnect_from_server
  
  begin
    @sock = connect(@hostname, @port)
  rescue => e
    popup("open: %s", e.message)
    retry
  end

  @channel = GLib::IOChannel.new(@sock.to_i)
  
  @watch_id = @channel.add_watch(GLib::IOChannel::IN) do | channel, condition |
    update
    true
  end
end

####

def usage
  $stderr.print "usage: simano.rb -s <hostname> -p <port>\n"
  exit 1
end

opt = OptionParser.new

opt.on('-s VAL', '--server=VAL',  'サーバのホスト名 (必須)') {|v| @hostname = v }
opt.on('-p VAL', '--port=VAL',    'サーバのポート番号 (必須)') { |v| @port = v.to_i }
opt.on('-N VAL', '--newmail=VAL', 'メールがある時のアイコン名') { |v| @newmail = v }
opt.on('-n VAL', '--nomail=VAL',  'メールがない時のアイコン名') { |v| @nomail = v }

opt.parse!(ARGV)

if !@hostname or !@port
  usage
end

@icon = Gtk::StatusIcon.new
@icon.icon_name = 'xfce-nomail'
@icon.tooltip_text = "#{@hostname}:#{@port}"

# 60秒毎に1バイト送る。
# ただし、suspend/resume 時に接続が切れてるなら、
# さっさと教えて欲しいので、短めに callback して
# もらって、こっちで経過時間をチェックする。
GLib::Timeout.add_seconds(5) do
  now = Time.now
  if now - @last_write_time >= 60
    @last_write_time = now
    
    if @sock
      begin
        @sock.write('0')
      rescue Errno::EINTR
        retry
      rescue => e
        disconnect_from_server
        popup("write: %s", e.message)
        connect_to_server
      end
    end
  end
  
  true
end

connect_to_server

Gtk.main
