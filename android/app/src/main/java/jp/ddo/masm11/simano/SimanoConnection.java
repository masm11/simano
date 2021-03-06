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
    enum Event {
	CONNECTING,
	READY,
	NO_MAIL,
	NEW_MAIL,
	CLOSING,
	SLEEP,
	FINISH,
    }
    
    interface EventListener {
	void setEvent(Event ev);
    }
    
    private class KeepAlive implements Runnable {
	private final SocketChannel sock;
	private boolean alarm = false;
	KeepAlive(SocketChannel sock) {
	    this.sock = sock;
	}
	@Override
	public void run() {
	    try {
		ByteBuffer wbuf = ByteBuffer.allocate(1);
		wbuf.put((byte) '0');
		//noinspection InfiniteLoopStatement
		while (true) {
		    synchronized (this) {
			while (!alarm)
			    wait();
			alarm = false;
		    }
		    Log.d("write keepalive.");
		    wbuf.position(0);
		    sock.write(wbuf);
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
	
	void alarm() {
	    synchronized (this) {
		alarm = true;
		notify();
	    }
	}
    }
    
    private final String hostname;
    private final int port;
    private boolean useIPv4Only;
    private final EventListener eventListener;
    private KeepAlive ka = null;
    private boolean alarmed = false;
    
    SimanoConnection(String hostname, int port, boolean useIPv4Only, EventListener eventListener) {
	this.hostname = hostname;
	this.port = port;
	this.useIPv4Only = useIPv4Only;
	this.eventListener = eventListener;
    }
    
    @Override
    public void run() {
	Log.i("Thread started.");
	try {
	    //noinspection InfiniteLoopStatement
	    while (true) {
		SocketChannel sock = null;
		Thread thread = null;
		ka = null;
		
		try {
		    setEvent(Event.CONNECTING);
		    
		    sock = connectTo(hostname, port, useIPv4Only);
		    sock.socket().setSoTimeout(0);
		    
		    ka = new KeepAlive(sock);
		    thread = new Thread(ka);
		    thread.start();
		    
		    setEvent(Event.READY);
		    Log.i("Entering recv loop.");
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
			    Log.d("Read: '%c'.", c);
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
		    setEvent(Event.CLOSING);
		    if (sock != null) {
			try {
			    sock.close();
			} catch (IOException e) {
			    Log.e(e, "close failed");
			}
		    }
		    
		    synchronized (this) {
			ka = null;
		    }
		    if (thread != null) {
			try {
			    thread.interrupt();
			    thread.join();
			} catch (InterruptedException e) {
			    Log.w(e, "join failed.");
			}
		    }
		}
		
		Log.d("Sleeping 1min...");
		setEvent(Event.SLEEP);
		// 適当に寝る。
		synchronized (this) {
		    alarmed = false;
		    while (!alarmed)
			wait();
		}
		Log.d("Sleeping... done.");
	    }
	} catch (InterruptedException e) {
	    Log.i(e, "intr");
	}
	
	setEvent(Event.FINISH);
	Log.i("Thread finished.");
    }
    
    private SocketChannel connectTo(String hostname, int port, boolean useIPv4Only)
	    throws IOException {
	IOException last_e = null;
	
	Log.i("Resolving.");
	InetAddress[] addrs = InetAddress.getAllByName(hostname);
	Log.i("Resolving done.");
	
	for (InetAddress addr: addrs)
	    Log.d("addr: %s", addr.toString());
	
	for (boolean use_v6: new boolean[] { true, false } ) {
	    if (use_v6 && useIPv4Only)
		continue;
	    
	    for (InetAddress addr: addrs) {
		boolean is_v6 = addr instanceof Inet6Address;
		if (is_v6 != use_v6)
		    continue;
		
		SocketChannel sock = null;
		try {
		    Log.i("Connecting to %s", addr.toString());
		    
		    sock = SocketChannel.open();
		    sock.configureBlocking(true);
		    sock.connect(new InetSocketAddress(addr, port));
		    
		    Log.i("Connection done.");
		    return sock;
		} catch (IOException e) {
		    Log.w(e, "Connection failed.");
		    
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
	
	Log.w("All connection try failed.");
	throw last_e;
    }
    
    private void setEvent(Event ev) {
	Log.d("event: %s", ev.toString());
	eventListener.setEvent(ev);
    }
    
    void alarm() {
	Log.d("Alarm.");
	synchronized (this) {
	    if (ka != null)
		ka.alarm();
	    
	    alarmed = true;
	    notify();
	}
    }
}
