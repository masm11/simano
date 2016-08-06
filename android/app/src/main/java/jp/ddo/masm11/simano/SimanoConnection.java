package jp.ddo.masm11.simano;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.PrintWriter;
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
import java.text.SimpleDateFormat;
import java.util.Date;

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
    static interface DebugListener {
	public void addDebug(String msg);
    }
    
    private class KeepAlive implements Runnable {
	private SocketChannel sock;
	private boolean alarm = false;
	KeepAlive(SocketChannel sock) {
	    this.sock = sock;
	}
	public void run() {
	    try {
		ByteBuffer wbuf = ByteBuffer.allocate(1);
		wbuf.put((byte) '0');
		while (true) {
		    synchronized (this) {
			while (!alarm)
			    wait();
			alarm = false;
		    }
		    Log.d("write keepalive.");
		    addDebug("Write keepalive.");
		    wbuf.position(0);
		    sock.write(wbuf);
		}
	    } catch (Exception e) {
		Log.w(e, "keepalive");
		addDebug("Keepalive failed: %s", e.toString());
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
    
    private String hostname;
    private int port;
    private EventListener eventListener;
    private DebugListener debugListener;
    private SocketChannel sock = null;
    private KeepAlive ka = null;
    private boolean alarmed = false;
    
    SimanoConnection(String hostname, int port, EventListener eventListener, DebugListener debugListener) {
	this.hostname = hostname;
	this.port = port;
	this.eventListener = eventListener;
	this.debugListener = debugListener;
    }
    
    @Override
    public void run() {
	addDebug("Thread started.");
	try {
	    while (true) {
		sock = null;
		ka = null;
		Thread thread = null;
		
		try {
		    setEvent(Event.CONNECTING);
		    
		    sock = connectTo(hostname, port);
		    sock.socket().setSoTimeout(0);
		    
		    ka = new KeepAlive(sock);
		    thread = new Thread(ka);
		    thread.start();
		    
		    addDebug("Entering recv loop.");
		    ByteBuffer rbuf = ByteBuffer.allocate(1);
		    while (true) {
			rbuf.position(0);
			Log.d("read()...");
			int r = sock.read(rbuf);
			Log.d("read()... done. r=%d.", r);
			if (r == -1) {
			    // connection closed.
			    Log.i("connection closed.");
			    addDebug("Connection closed.");
			    break;
			}
			if (r > 0) {
			    rbuf.position(0);
			    char c = (char) rbuf.get();
			    addDebug("Read: '%c'.", c);
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
		    addDebug("%s", e.toString());
		} catch (ClosedByInterruptException e) {
		    Log.e(e, "closedbyintrexception.");
		    addDebug("%s", e.toString());
		    throw new InterruptedException();	// 間違ってる気がする。
		} catch (AsynchronousCloseException e) {
		    Log.e(e, "asyncclosedexception.");
		    addDebug("%s", e.toString());
		} catch (IOException e) {
		    Log.e(e, "ioexception.");
		    addDebug("%s", e.toString());
		} catch (UnresolvedAddressException e) {
		    Log.e(e, "unknown host.");
		    addDebug("%s", e.toString());
		} finally {
		    if (sock != null) {
			try {
			    setEvent(Event.CLOSING);
			    sock.close();
			} catch (IOException e) {
			    Log.e(e, "close failed");
			    addDebug("Close failed: %s", e.toString());
			}
			sock = null;
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
			thread = null;
		    }
		}
		
		Log.d("sleeping...");
		addDebug("Sleep 1min.");
		setEvent(Event.SLEEP);
		// 適当に寝る。
		synchronized (this) {
		    alarmed = false;
		    while (!alarmed)
			wait();
		}
		Log.d("sleeping... done.");
		addDebug("Sleep done.");
	    }
	} catch (InterruptedException e) {
	    Log.i(e, "intr");
	    addDebug("Interrupted.");
	}
	
	debugClose();
	
	setEvent(Event.FINISH);
	addDebug("Thread finished.");
    }
    
    private SocketChannel connectTo(String hostname, int port)
	    throws IOException {
	IOException last_e = null;
	
	Log.i("resolving.");
	addDebug("Resolve.");
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
		    addDebug("Try to connect to %s.", addr.toString());
		    
		    sock = SocketChannel.open();
		    sock.configureBlocking(true);
		    sock.connect(new InetSocketAddress(addr, port));
		    
		    Log.i("connection done.");
		    addDebug("Connection successfully done.");
		    return sock;
		} catch (IOException e) {
		    Log.w(e, "connection failed.");
		    addDebug("Connection failed: %s", e.toString());
		    
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
	
	addDebug("All connection try failed.");
	throw last_e;
    }
    
    private void setEvent(Event ev) {
	Log.d("event: %s", ev.toString());
	eventListener.setEvent(ev);
    }
    private void addDebug(String fmt, Object... args) {
	SimpleDateFormat df = new SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS");
	StringBuffer buf = new StringBuffer();
	buf.append(df.format(new Date()));
	// buf.append("\n");
	buf.append(" ");
	buf.append(String.format(fmt, args));
	// debugListener.addDebug(buf.toString());
	debugPrint(buf.toString());
    }
    
    void setDebugDir(File dir) {
	debugOpen(dir);
    }
    
    private PrintWriter debugOut = null;
    private void debugOpen(File dir) {
	try {
	    File file = new File(dir, "logcat.txt");
	    debugOut = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
	    debugPrint("=====================");
	} catch (IOException e) {
	    android.util.Log.e("SimanoConnection", "debugOpen: " + e.toString());
	}
    }
    private void debugPrint(String msg) {
	if (debugOut != null) {
	    synchronized (debugOut) {
		debugOut.println(msg);
		debugOut.flush();
	    }
	}
    }
    private void debugClose() {
	if (debugOut != null)
	    debugOut.close();
    }
    
    void alarm() {
	addDebug("Alarm.");
	synchronized (this) {
	    if (ka != null)
		ka.alarm();
	    
	    alarmed = true;
	    notify();
	}
    }
}
