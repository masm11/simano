package jp.ddo.masm11.simano;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Button;
import android.util.Log;
import android.content.Intent;
import android.content.Context;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.app.NotificationManager;
import android.media.SoundPool;
import android.media.AudioAttributes;

public class MainActivity extends AppCompatActivity {
    private Thread thread;
    private SimanoConnection simano;
    private Handler handler;
    private boolean state;
    private String error;
    private SoundPool soundPool;
    private int soundId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.d("main", "onCreate start.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
	
	Button btn_pref = (Button) findViewById(R.id.pref);
	btn_pref.setOnClickListener(new OnClickListener() {
	    public void onClick(View v) {
		openPref();
	    }
	});

	Button btn_retry = (Button) findViewById(R.id.retry);
	btn_retry.setOnClickListener(new OnClickListener() {
	    public void onClick(View v) {
		retry();
	    }
	});
	
	AudioAttributes audioAttr = new AudioAttributes.Builder()
		.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED)
		.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
		.build();
	soundPool = new SoundPool.Builder()
		.setAudioAttributes(audioAttr)
		.setMaxStreams(1)
		.build();
	soundId = soundPool.load(this, R.raw.office_2, 1);
	
	handler = new Handler();
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	simano = new SimanoConnection(this, hostname, port);
	thread = new Thread(simano);
	thread.start();
	btn_pref.setText(hostname + ":" + port);
	Log.d("main", "onCreate end.");
    }
    
    @Override
    protected void onDestroy() {
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e("main", "onDestroy: join error", e);
	    }
	    thread = null;
	}
	
	super.onDestroy();
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	if (requestCode == 123) {
	    String hostname = PrefActivity.getHostname(this);
	    int port = PrefActivity.getPort(this);
	    
	    clearError();
	    if (thread != null) {
		try {
		    thread.interrupt();
		    thread.join();
		} catch (InterruptedException e) {
		    Log.e("main", "onActivityResult: join error", e);
		}
		thread = null;
	    }
	    simano = null;
	    
	    simano = new SimanoConnection(this, hostname, port);
	    thread = new Thread(simano);
	    thread.start();
	    Button btn_pref = (Button) findViewById(R.id.pref);
	    btn_pref.setText(hostname + ":" + port);
	}
	
	super.onActivityResult(requestCode, resultCode, data);
    }
    
    private void openPref() {
	Intent intent = new Intent(this, (Class<?>)PrefActivity.class);
	startActivityForResult(intent, 123);
    }
    
    private void retry() {
	Log.d("main", "retry");
	
	clearError();
	
	if (thread != null) {
	    try {
		thread.interrupt();
		thread.join();
	    } catch (InterruptedException e) {
		Log.e("main", "retry: join error", e);
	    }
	    thread = null;
	}
	simano = null;
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	simano = new SimanoConnection(this, hostname, port);
	thread = new Thread(simano);
	thread.start();
	Button btn_pref = (Button) findViewById(R.id.pref);
	btn_pref.setText(hostname + ":" + port);
    }
    
    void setState(final boolean state) {
	this.state = state;
	handler.post(new Runnable() {
	    @Override
	    public void run() {
		TextView text = (TextView) findViewById(R.id.state);
		text.setText(state ? "新着メールがあります" : "新着メールはありません");
		
		updateNotification();
		
		if (state)
		    soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
	    }
	});
    }
    void setError(final String msg) {
	this.error = msg;
	handler.post(new Runnable() {
	    @Override
	    public void run() {
		TextView text = (TextView) findViewById(R.id.errmsg);
		text.setText(msg);
		
		Button btn = (Button) findViewById(R.id.retry);
		btn.setVisibility(View.VISIBLE);
		
		updateNotification();
		
		soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
	    }
	});
    }
    private void clearError() {
	TextView text = (TextView) findViewById(R.id.errmsg);
	text.setText("");
	this.error = null;
	
	Button btn = (Button) findViewById(R.id.retry);
	btn.setVisibility(View.INVISIBLE);

	updateNotification();
    }
    
    private void updateNotification() {
	String msg = null;
	if (state)
	    msg = "新着メールがあります";
	if (error != null)
	    msg = error;
	
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
