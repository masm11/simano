package jp.ddo.masm11.simano;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import android.util.Log;

class SimanoConnection implements Runnable {
    static interface StateListener {
	public void setState(boolean state);
    }
    static interface ErrorListener {
	public void setError(String msg);
    }
    
    private String hostname;
    private int port;
    private StateListener stateListener;
    private ErrorListener errorListener;
    
    SimanoConnection(String hostname, int port, StateListener stateListener, ErrorListener errorListener) {
	this.hostname = hostname;
	this.port = port;
	this.stateListener = stateListener;
	this.errorListener = errorListener;
    }
    
    @Override
    public void run() {
	SocketChannel sock = null;
	long lastWrite = 0;
	
	try {
	    sock = SocketChannel.open();
	    sock.configureBlocking(false);
	    
	    Log.i("conn", "connection start.");
	    if (!sock.connect(new InetSocketAddress(hostname, port))) {
		while (!sock.finishConnect())
		    Thread.sleep(100);
	    }
	    Log.i("conn", "connection done.");
	    
	    sock.socket().setSoTimeout(0);
	    
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
		    setError("Connection broken.");
		    break;
		}
		if (r > 0) {
		    rbuf.position(0);
		    char c = (char) rbuf.get();
		    if (c == '0') {
			Log.i("conn", "No new mail.");
			setState(false);
		    } else if (c == '1') {
			Log.i("conn", "You have new mails.");
			setState(true);
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
	} catch (InterruptedException e) {
	    Log.i("conn", "intr", e);
	} catch (Exception e) {
	    Log.e("conn", "exception.", e);
	    setError(e.toString());
	} finally {
	    if (sock != null) {
		try {
		    sock.close();
		} catch (IOException e) {
		    Log.e("conn", "close failed", e);
		}
	    }
	}
    }
    
    private void setState(boolean state) {
	Log.d("thread", "state: " + state);
	stateListener.setState(state);
    }
    private void setError(String msg) {
	Log.d("thread", "msg: " + msg);
	errorListener.setError(msg);
    }
}
