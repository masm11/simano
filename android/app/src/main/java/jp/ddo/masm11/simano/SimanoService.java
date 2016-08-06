package jp.ddo.masm11.simano;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Service;
import android.app.AlarmManager;
import android.os.IBinder;
import android.os.Binder;
import android.os.PowerManager;
import android.media.SoundPool;
import android.media.AudioAttributes;
import android.preference.PreferenceManager;
import java.util.LinkedList;

public class SimanoService extends Service {
    public static class AlarmReceiver extends BroadcastReceiver {
	private PowerManager.WakeLock wakelock = null;
	@Override
	public void onReceive(Context context, Intent intent) {
	    android.util.Log.i("SimanoService", "alarm!!");
	    
	    if (wakelock == null) {
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimanoService");
	    }
	    
	    Intent i = new Intent(context, SimanoService.class);
	    IBinder binder = peekService(context, i);
	    if (binder != null) {
		SimanoService service = ((SimanoService.SimanoBinder) binder).getService();
		wakelock.acquire(5 * 1000);
		service.alarm();
	    }
	}
    }
    
    private Thread thread;
    private SimanoConnection conn;
    private SoundPool soundPool;
    private int soundId;
    private boolean state;
    private String msg;
    private LinkedList<String> debugmsgs = new LinkedList<String>();
    
    public void onCreate() {
	Log.d("");
	
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
	Log.i("hostname=%s", hostname);
	Log.i("port=%d", port);
	setServer(hostname, port);
	
	Intent intent = new Intent(this, AlarmReceiver.class);
	PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);
	AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
	am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, 60 * 1000, sender);
    }
    
    public int onStartCommand(Intent intent, int flags, int startId) {
	Log.d("");
	return START_STICKY;
    }
    
    class SimanoBinder extends Binder {
	SimanoService getService() {
	    return SimanoService.this;
	}
    }
    private final IBinder binder = new SimanoBinder();
    
    public IBinder onBind(Intent intent) {
	Log.d("");
	return binder;
    }
    
    public boolean onUnbind(Intent intent) {
	Log.d("");
	return false;
    }
    
    public void onDestroy() {
	Log.d("");
	
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e(e, "join error");
	    }
	    thread = null;
	}
    }
    
    synchronized void requestBroadcast() {
	broadcastState(state);
	broadcastAllDebug();
    }
    
    synchronized void setServer(String hostname, int port) {
	Log.i("hostname=%s, port=%d.", hostname, port);
	
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e(e, "join error");
	    }
	    thread = null;
	}
	
	conn = new SimanoConnection(hostname, port, new SimanoConnection.EventListener() {
	    public void setEvent(SimanoConnection.Event ev) {
		Log.d("event: %s", ev.toString());
		
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
			soundPool.play(soundId, 0.5f, 0.5f, 0, 0, 1.0f);
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
	}, new SimanoConnection.DebugListener() {
	    public void addDebug(String msg) {
		synchronized (debugmsgs) {
		    debugmsgs.addLast(msg);
		    if (debugmsgs.size() > 100)
			debugmsgs.removeFirst();
		}
		broadcastDebug(msg);
	    }
	});
	conn.setDebugDir(getExternalCacheDir());
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
	Log.d("state=%b", state);
	Intent intent = new Intent("jp.ddo.masm11.simano.STATE");
	intent.putExtra("state", state);
	sendBroadcast(intent);
    }
    private void broadcastDebug(String msg) {
	Intent intent = new Intent("jp.ddo.masm11.simano.DEBUG");
	intent.putExtra("msg", msg);
	sendBroadcast(intent);
    }
    private void broadcastAllDebug() {
	synchronized (debugmsgs) {
	    for (String msg: debugmsgs)
		broadcastDebug(msg);
	}
    }
    private void alarm() {
	android.util.Log.d("SimanoService", "alarm");
	if (conn != null)
	    conn.alarm();
    }
}
