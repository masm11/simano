package jp.ddo.masm11.simano;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import android.util.Log;

class SimanoThread implements Runnable {
    private String hostname;
    private int port;
    private MainActivity activity;
    
    private static class SimanoWriter implements Runnable {
	private Socket sock;
	SimanoWriter(Socket sock) {
	    this.sock = sock;
	}
	public void run() {
	    try {
		OutputStream os = sock.getOutputStream();
		while (true) {
		    os.write('0');
		    Thread.sleep(60 * 1000);
		}
	    } catch (Exception e) {
		Log.w("thread", "writer: error", e);
	    }
	}
    }
    
    SimanoThread(MainActivity activity, String hostname, int port) {
	this.activity = activity;
	this.hostname = hostname;
	this.port = port;
    }
    
    @Override
    public void run() {
	Socket sock = null;
	Thread writer = null;
	
	Log.i("thread", "hostname=" + hostname + ", port=" + port);
	try {
	    sock = new Socket(hostname, port);
	    
	    writer = new Thread(new SimanoWriter(sock));
	    writer.start();
	    
	    while (true) {
		InputStream is = sock.getInputStream();
		int c = is.read();
		if (c == -1) {
		    // connection closed.
		    Log.i("thread", "connection closed.");
		    setError("Connection broken.");
		    break;
		}
		if (c == '0') {
		    // no mail.
		    Log.i("thread", "no mail.");
		    setState(false);
		} else if (c == '1') {
		    // new mail.
		    Log.i("thread", "new mail.");
		    setState(true);
		}
	    }
	} catch (Exception e) {
	    Log.w("thread", "error", e);
	    setError(e.toString());
	} finally {
	    if (writer != null) {
		writer.interrupt();
		try {
		    writer.join();
		} catch (InterruptedException e) {
		    Log.e("thread", "writer join error", e);
		}
		writer = null;
	    }
	    if (sock != null) {
		try {
		    sock.close();
		} catch (Exception e) {
		}
		sock = null;
	    }
	}
    }
    
    private void setState(boolean state) {
	Log.d("thread", "state: " + state);
	activity.setState(state);
    }
    private void setError(String msg) {
	Log.d("thread", "msg: " + msg);
	activity.setError(msg);
    }
}
