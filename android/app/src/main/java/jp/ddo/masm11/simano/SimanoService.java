package jp.ddo.masm11.simano;

import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.Service;
import android.app.AlarmManager;
import android.app.TaskStackBuilder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;

public class SimanoService extends Service {
    public static class AlarmReceiver extends BroadcastReceiver {
	private PowerManager.WakeLock wakelock = null;
	@Override
	public void onReceive(Context context, Intent intent) {
	    Log.i("");
	    
	    if (wakelock == null) {
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimanoService");
	    }
	    
	    wakelock.acquire(5 * 1000);
	    
	    Intent i = new Intent(context, SimanoService.class);
	    i.setAction("jp.ddo.masm11.simano.ALARM");
	    context.startService(i);
	}
    }
    
    private Thread thread;
    private SimanoConnection conn;
    private boolean state;
    private String msg;
    private File stateFile;
    
    public void onCreate() {
	Log.d("");
	
	stateFile = new File(getFilesDir(), "state.flg");
	Log.d("stateFile: %s", stateFile.toString());
	loadState();
	
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
	if (intent != null) {
	    String action = intent.getAction();
	    if (action != null) {
		if (action.equals("jp.ddo.masm11.simano.ALARM"))
		    alarm();
		else if (action.equals("jp.ddo.masm11.simano.BCAST_REQ"))
		    broadcastState(state);
		else if (action.equals("jp.ddo.masm11.simano.SET_SERVER")) {
		    String hostname = intent.getStringExtra("jp.ddo.masm11.simano.HOSTNAME");
		    int port = intent.getIntExtra("jp.ddo.masm11.simano.PORT", 0);
		    setServer(hostname, port);
		} else if (action.equals("jp.ddo.masm11.simano.DEBUG")) {
		    setNotification("Test");
		}
	    }
	}
	return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
	return null;
    }
    
    @Override
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
    
    private void setServer(String hostname, int port) {
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
		    if (state) {
			setNotification(null);
			broadcastState(false);
			state = false;
			saveState();
		    }
		    break;
		case NEW_MAIL:
		    if (!state) {
			setNotification("新着メールがあります");
			broadcastState(true);
			state = true;
			saveState();
		    }
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
	NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	
	if (msg != null) {
	    Intent intent = Intent.makeMainActivity(new ComponentName("com.sonymobile.email", "com.sonymobile.email.activity.EmailActivity"));
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    
	    PendingIntent pending = TaskStackBuilder.create(this)
		    .addNextIntentWithParentStack(intent)
		    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	    
	    Notification notification = new Notification.Builder(this)
		    .setSmallIcon(R.drawable.ic_mail_outline_white_24dp)
		    .setContentTitle("Simano")
		    .setContentText(msg)
		    .setContentIntent(pending)
		    .setDefaults(Notification.DEFAULT_SOUND)
		    .build();
	    
	    manager.notify(0, notification);
	} else {
	    manager.cancel(0);
	}
    }
    
    private void broadcastState(boolean state) {
	Log.d("state=%b", state);
	Intent intent = new Intent("jp.ddo.masm11.simano.STATE");
	intent.putExtra("state", state);
	sendBroadcast(intent);
    }
    
    private void alarm() {
	Log.d("");
	if (conn != null)
	    conn.alarm();
    }
    
    /* Service の lifecycle を越えて state を保持する。
     * じゃないと、メールがある時に Service を落とされて、再起動して
     * メールがまだあると、その時点で Notification の音が鳴ってしまうため。
     * 保存方法としては、boolean でいいので、ファイルが存在するかどうか
     * で保存する。
     */
    private void saveState() {
	try {
	    Log.d("state=%b", state);
	    if (state)
		stateFile.createNewFile();
	    else
		stateFile.delete();
	    Log.d("OK.");
	} catch (IOException e) {
	    Log.w(e, "file error");
	} catch (Exception e) {
	    Log.w(e, "file error");
	}
    }
    private void loadState() {
	Log.d("");
	try {
	    state = stateFile.exists();
	} catch (Exception e) {
	    Log.w(e, "file error");
	}
	Log.d("state=%b", state);
    }
}
