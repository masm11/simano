package jp.ddo.masm11.simano;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.ByteBuffer;

class SimanoConnection implements Runnable {
    static enum Event {
	CONNECTING,
	NO_MAIL,
	NEW_MAIL,
	CLOSING,
	SLEEP,
	FINISH,
    }
    
    static interface EventListener {
	public void setEvent(Event ev);
    }
    
    private class KeepAlive implements Runnable {
	private SocketChannel sock;
	KeepAlive(SocketChannel sock) {
	    this.sock = sock;
	}
	public void run() {
	    try {
		ByteBuffer wbuf = ByteBuffer.allocate(1);
		wbuf.put((byte) '0');
		while (true) {
		    Log.d("write keepalive.");
		    wbuf.position(0);
		    sock.write(wbuf);
		    Thread.sleep(60 * 1000);
		}
	    } catch (Exception e) {
		Log.w(e, "keepalive");
	    } finally {
		/* 万が一こっちだけの原因で終了しちゃった場合、
		 * 親スレッドが残ってしまうので、
		 * sock を閉じて親を起こす。
		 */
		try {
		    sock.close();
		} catch (Exception e) {
		    Log.w(e, "close failed.");
		}
	    }
	}
    }
    
    private String hostname;
    private int port;
    private EventListener eventListener;
    
    SimanoConnection(String hostname, int port, EventListener eventListener) {
	this.hostname = hostname;
	this.port = port;
	this.eventListener = eventListener;
    }
    
    @Override
    public void run() {
	try {
	    while (true) {
		SocketChannel sock = null;
		KeepAlive ka = null;
		Thread thread = null;
		
		try {
		    setEvent(Event.CONNECTING);
		    
		    sock = connectTo(hostname, port);
		    sock.socket().setSoTimeout(0);
		    
		    ka = new KeepAlive(sock);
		    thread = new Thread(ka);
		    thread.start();
		    
		    ByteBuffer rbuf = ByteBuffer.allocate(1);
		    while (true) {
			rbuf.position(0);
Log.d("read()...");
			int r = sock.read(rbuf);
Log.d("read()... done. r=%d.", r);
			if (r == -1) {
			    // connection closed.
			    Log.i("connection closed.");
			    break;
			}
			if (r > 0) {
			    rbuf.position(0);
			    char c = (char) rbuf.get();
			    if (c == '0') {
				Log.i("No new mail.");
				setEvent(Event.NO_MAIL);
			    } else if (c == '1') {
				Log.i("You have new mails.");
				setEvent(Event.NEW_MAIL);
			    }
			}
		    }
		} catch (SocketException e) {
		    Log.e(e, "socketexception.");
		} catch (ClosedByInterruptException e) {
		    Log.e(e, "closedbyintrexception.");
		    throw new InterruptedException();	// 間違ってる気がする。
		} catch (AsynchronousCloseException e) {
		    Log.e(e, "asyncclosedexception.");
		} catch (IOException e) {
		    Log.e(e, "ioexception.");
		} catch (UnresolvedAddressException e) {
		    Log.e(e, "unknown host.");
		} finally {
		    if (sock != null) {
			try {
			    setEvent(Event.CLOSING);
			    sock.close();
			} catch (IOException e) {
			    Log.e(e, "close failed");
			}
			sock = null;
		    }
		    
		    if (thread != null) {
			try {
			    thread.interrupt();
			    thread.join();
			} catch (InterruptedException e) {
			    Log.w(e, "join failed.");
			}
			thread = null;
		    }
		}
		
		Log.d("sleeping...");
		setEvent(Event.SLEEP);
		Thread.sleep(60 * 1000);
		Log.d("sleeping... done.");
	    }
	} catch (InterruptedException e) {
	    Log.i(e, "intr");
	}
	setEvent(Event.FINISH);
    }
    
    private SocketChannel connectTo(String hostname, int port)
	    throws IOException {
	IOException last_e = null;
	
	Log.i("resolving.");
	InetAddress[] addrs = InetAddress.getAllByName(hostname);
	Log.i("resolving done.");
	
	for (InetAddress addr: addrs)
	    Log.d("addr: %s", addr.toString());
	
	for (boolean use_v6: new boolean[] { true, false } ) {
	    for (InetAddress addr: addrs) {
		boolean is_v6 = addr instanceof Inet6Address;
		if (is_v6 != use_v6)
		    continue;
		
		SocketChannel sock = null;
		try {
		    Log.i("connecting to %s", addr.toString());
		    
		    sock = SocketChannel.open();
		    sock.configureBlocking(true);
		    sock.connect(new InetSocketAddress(addr, port));
		    
		    Log.i("connection done.");
		    return sock;
		} catch (IOException e) {
		    Log.w(e, "connection failed.");
		    
		    last_e = e;
		    if (sock != null) {
			try {
			    sock.close();
			} catch (Exception ee) {
			    Log.w(ee, "close failed.");
			}
		    }
		}
	    }
	}
	
	throw last_e;
    }
    
    private void setEvent(Event ev) {
	Log.d("event: %s", ev.toString());
	eventListener.setEvent(ev);
    }
}
