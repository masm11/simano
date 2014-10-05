#!/usr/bin/env ruby
# -*- coding: utf-8 -*-

require 'optparse'
require 'socket'
require 'gtk3'
require 'libnotify'

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
    popup('read: %s', 'Connection broken.')
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
        @notify = Libnotify.new(:summary => 'Mail',
                                :icon_path => @newmail,
                                :body => 'You have new mails.')
      end
      @notify.show!
    end
  end
end

def disconnect_from_server
  if @channel
    @channel.close
    @channel = nil
    @sock = nil
  end
end

def connect_to_server
  disconnect_from_server
  
  begin
    @sock = TCPSocket.open(@hostname, @port)
  rescue => e
    popup("open: %s", e.message)
    retry
  end

  @channel = GLib::IOChannel.new(@sock.to_i)
  
  @channel.add_watch(GLib::IOChannel::IN) do | channel, condition |
    update
  end
end

####

def usage
  $stderr.print "usage: simano.rb -s <hostname> -p <port>\n"
  exit 1
end

opt = OptionParser.new

opt.on('-s VAL', '--server=VAL',  'server hostname (mandatory)') {|v| @hostname = v }
opt.on('-p VAL', '--port=VAL',    'server port (mandatory)') { |v| @port = v.to_i }
opt.on('-N VAL', '--newmail=VAL', 'icon-name for new mail') { |v| @newmail = v }
opt.on('-n VAL', '--nomail=VAL',  'icon-name for no mail') { |v| @nomail = v }

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
