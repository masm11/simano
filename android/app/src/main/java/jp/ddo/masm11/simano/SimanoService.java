package jp.ddo.masm11.simano;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

public class SimanoService extends Service {
    private Thread thread;
    private SimanoConnection conn;
    
    public void onCreate() {
	Log.d("service", "onCreate");
    }
    
    public int onStartCommand(Intent intent, int flags, int startId) {
	Log.d("service", "onStartCommand");
	return START_STICKY;
    }
    
    class SimanoBinder extends Binder {
	SimanoService getService() {
	    return SimanoService.this;
	}
    }
    private final IBinder binder = new SimanoBinder();
    
    public IBinder onBind(Intent intent) {
	Log.d("service", "onBind");
	return binder;
    }
    
    public boolean onUnbind(Intent intent) {
	Log.d("service", "onUnbind");
	return false;
    }
    
    public void onDestroy() {
	Log.d("service", "onDestroy");
    }
    
    synchronized void setServer(String hostname, int port) {
	Log.i("service", "setServer: hostname=" + hostname + ", port=" + port);
	
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e("service", "setServer: join error", e);
	    }
	    thread = null;
	}
	
	conn = new SimanoConnection(hostname, port, new SimanoConnection.StateListener() {
	    public void setState(boolean state) {
		Log.d("service", "state: " + state);
	    }
	}, new SimanoConnection.ErrorListener() {
	    public void setError(String msg) {
		Log.d("service", "error: " + msg);
	    }
	});
	thread = new Thread(conn);
	thread.start();
    }
}
