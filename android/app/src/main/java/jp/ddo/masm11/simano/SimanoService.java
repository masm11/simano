package jp.ddo.masm11.simano;

import android.content.Context;
import android.content.ComponentName;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.media.SoundPool;
import android.media.AudioAttributes;
import android.util.Log;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SimanoService extends Service {
    private Thread thread;
    private SimanoConnection conn;
    private SoundPool soundPool;
    private int soundId;
    private boolean state;
    private String msg;
    
    public void onCreate() {
	Log.d("service", "onCreate");
	
	AudioAttributes audioAttr = new AudioAttributes.Builder()
		.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED)
		.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
		.build();
	soundPool = new SoundPool.Builder()
		.setAudioAttributes(audioAttr)
		.setMaxStreams(1)
		.build();
	soundId = soundPool.load(this, R.raw.office_2, 1);
	
	PreferenceManager.setDefaultValues(this, R.layout.activity_pref, false);
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
	String hostname = settings.getString("hostname", "localhost");
	int port = Integer.valueOf(settings.getString("port", "0"));
	Log.i("service", "onCreate: hostname=" + hostname);
	Log.i("service", "onCreate: port=" + port);
	setServer(hostname, port);
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
	
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e("service", "onDestroy: join error", e);
	    }
	    thread = null;
	}
    }
    
    synchronized void requestBroadcast() {
	broadcastState(state);
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
	
	conn = new SimanoConnection(hostname, port, new SimanoConnection.EventListener() {
	    public void setEvent(SimanoConnection.Event ev) {
		Log.d("service", "event: " + ev);
		
		switch (ev) {
		case CONNECTING:
		    break;
		case NO_MAIL:
		    setNotification(null);
		    broadcastState(false);
		    state = false;
		    break;
		case NEW_MAIL:
		    setNotification("新着メールがあります");
		    broadcastState(true);
		    if (!state)
			soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
		    state = true;
		    break;
		case CLOSING:
		    break;
		case SLEEP:
		    break;
		case FINISH:
		    break;
		}
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
	    
/* [Eメール] が crash。
	    Intent intent = new Intent(Intent.ACTION_VIEW);
	    intent.setType("message/rfc822");
*/
/* au の [Eメール] が起動。
	    Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL);
*/
/* [Eメール] が crash。
	    Intent intent = new Intent(Intent.ACTION_MAIN);
	    intent.setType("message/rfc822");
*/
/* au の [Eメール] が起動。
	    Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
*/
/* [Eメール] が crash。
	    Intent intent = Intent.makeMainActivity(new ComponentName("com.sonymobile.email", "com.sonymobile.email.activity.MessageFileView"));
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
*/
	    Intent intent = Intent.makeMainActivity(new ComponentName("com.sonymobile.email", "com.sonymobile.email.activity.EmailActivity"));
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

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
    
    private void broadcastState(boolean state) {
	Log.d("service", "broadcastState: state=" + state);
	Intent intent = new Intent("jp.ddo.masm11.simano.STATE");
	intent.putExtra("state", state);
	sendBroadcast(intent);
    }
}
