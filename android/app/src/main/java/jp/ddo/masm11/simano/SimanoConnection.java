package jp.ddo.masm11.simano;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.ByteBuffer;
import android.util.Log;

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
		long lastWrite = 0;
		
		try {
		    setEvent(Event.CONNECTING);
		    sock = SocketChannel.open();
		    sock.configureBlocking(false);
		    sock.socket().setSoTimeout(0);
		    
		    Log.i("conn", "connection start.");
		    if (!sock.connect(new InetSocketAddress(hostname, port))) {
			while (!sock.finishConnect())
			    Thread.sleep(100);
		    }
		    Log.i("conn", "connection done.");
		    
		    // ひどい作りやな…
		    ByteBuffer rbuf = ByteBuffer.allocate(1);
		    ByteBuffer wbuf = ByteBuffer.allocate(1);
		    while (true) {
			rbuf.position(0);
			rbuf.limit(1);
			int r = sock.read(rbuf);
			if (r == -1) {
			    // connection closed.
			    Log.i("conn", "conn closed.");
			    break;
			}
			if (r > 0) {
			    rbuf.position(0);
			    char c = (char) rbuf.get();
			    if (c == '0') {
				Log.i("conn", "No new mail.");
				setEvent(Event.NO_MAIL);
			    } else if (c == '1') {
				Log.i("conn", "You have new mails.");
				setEvent(Event.NEW_MAIL);
			    }
			}
			
			long now = System.currentTimeMillis();
			if (now - lastWrite >= 60 * 1000) {
			    Log.d("conn", "write keepalive.");
			    wbuf.position(0);
			    wbuf.limit(1);
			    wbuf.put((byte) '0');
			    wbuf.position(0);
			    sock.write(wbuf);
			    
			    lastWrite = now;
			}
			
			Thread.sleep(100);
		    }
		} catch (SocketException e) {
		    Log.e("conn", "socketexception.", e);
		} catch (IOException e) {
		    Log.e("conn", "ioexception.", e);
		} catch (UnresolvedAddressException e) {
		    Log.e("conn", "unknown host.", e);
		} finally {
		    if (sock != null) {
			try {
			    setEvent(Event.CLOSING);
			    sock.close();
			} catch (IOException e) {
			    Log.e("conn", "close failed", e);
			}
			sock = null;
		    }
		}
		
		Log.d("conn", "sleeping...");
		setEvent(Event.SLEEP);
		Thread.sleep(60 * 1000);
		Log.d("conn", "sleeping... done.");
	    }
	} catch (InterruptedException e) {
	    Log.i("conn", "intr", e);
	}
	setEvent(Event.FINISH);
    }
    
    private void setEvent(Event ev) {
	Log.d("thread", "event: " + ev);
	eventListener.setEvent(ev);
    }
}
