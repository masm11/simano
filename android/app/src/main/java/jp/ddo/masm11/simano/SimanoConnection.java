package jp.ddo.masm11.simano;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
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
		    
		    Log.i("connection start.");
		    if (!sock.connect(new InetSocketAddress(hostname, port))) {
			while (!sock.finishConnect())
			    Thread.sleep(100);
		    }
		    Log.i("connection done.");
		    
		    // ひどい作りやな…
		    ByteBuffer rbuf = ByteBuffer.allocate(1);
		    ByteBuffer wbuf = ByteBuffer.allocate(1);
		    while (true) {
			rbuf.position(0);
			rbuf.limit(1);
			int r = sock.read(rbuf);
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
			
			long now = System.currentTimeMillis();
			if (now - lastWrite >= 60 * 1000) {
			    Log.d("write keepalive.");
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
		    Log.e(e, "socketexception.");
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
    
    private void setEvent(Event ev) {
	Log.d("event: %s", ev.toString());
	eventListener.setEvent(ev);
    }
}
