#!/usr/bin/env ruby
# -*- coding: utf-8 -*-

require 'socket'
require 'rb-inotify'

@curstat = nil
def send_status(sock, newstat)
  if @curstat == nil || @curstat != newstat
    @curstat = newstat
    sock.write(@curstat ? '1' : '0')
  end
end

def checkdir(path, re)
  Dir.open(path) do |dir|
    dir.each do |name|
      next if name == '.'
      next if name == '..'
      return true if re =~ name
    end
  end
  return false
end

def checkdirs(sock)
  if checkdir("#{ENV['HOME']}/Maildir/new", /./)
    send_status(sock, true)
    return
  end
  if checkdir("#{ENV['HOME']}/Maildir/cur", /:2,[^S]*$/)
    send_status(sock, true)
    return
  end
  send_status(sock, false)
end

def service(sock)
  Thread.new do
    while true
      begin
        buf = sock.read(1)
        if !buf || buf == ''
          exit 0
        end
      rescue Errno::EINTR
        retry
      end
    end
  end

  checkdirs sock
  
  notifier = INotify::Notifier.new
  notifier.watch("#{ENV['HOME']}/Maildir/new", :create, :delete, :move) do
    checkdirs sock
  end
  notifier.watch("#{ENV['HOME']}/Maildir/cur", :create, :delete, :move) do
    checkdirs sock
  end
  notifier.run
end

####

port = 0

if ARGV[0] == '-p'
  port = ARGV[1].to_i
end

if port == 0
  $stderr.print "-p is mandatory.\n"
  exit 1
end

# fixme: ipv6 対応
sock = TCPServer.open("<any>", port)

while true
  begin
    s = sock.accept
  rescue Errno::EINTR
    retry
  end
  
  fork do
    sock.close
    service(s);
    exit 0
  end
  
  s.close
end

#
