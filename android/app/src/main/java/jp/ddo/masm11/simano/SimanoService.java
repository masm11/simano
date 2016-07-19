package jp.ddo.masm11.simano;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

public class SimanoService extends Service {
    
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
    
    void addServer(String hostname, int port) {
	Log.d("service", "addServer: hostname=" + hostname + ", port=" + port);
    }
}
