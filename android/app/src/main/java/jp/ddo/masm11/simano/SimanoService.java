package jp.ddo.masm11.simano;

import android.content.Context;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.app.NotificationManager;
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
		setNotification(state ? "新着メールがあります" : "新着メールはありません。");
	    }
	}, new SimanoConnection.ErrorListener() {
	    public void setError(String msg) {
		Log.d("service", "error: " + msg);
		setNotification(msg);
	    }
	});
	thread = new Thread(conn);
	thread.start();
    }
    
    private void setNotification(String msg) {
	if (msg != null) {
	    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
	    builder.setSmallIcon(R.drawable.ic_mail_outline_white_24dp);
	    builder.setContentTitle("Simano");
	    builder.setContentText(msg);
	    
	    Intent intent = new Intent(this, MainActivity.class);
	    TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
	    stackBuilder.addParentStack(MainActivity.class);
	    stackBuilder.addNextIntent(intent);
	    PendingIntent pending = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	    builder.setContentIntent(pending);
	    
	    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	    manager.notify(0, builder.build());
	} else {
	    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	    manager.cancel(0);
	}
    }
}
