package jp.ddo.masm11.simano;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.content.Context;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.app.NotificationManager;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.IBinder;

public class MainActivity extends AppCompatActivity {
    private class SimanoReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
	    String action = intent.getAction();
	    if (action.equals("jp.ddo.masm11.simano.STATE")) {
		Log.d("state: state=%b", intent.getBooleanExtra("state", false));
		setState(intent.getBooleanExtra("state", false));
	    }
	}
    }
    
    private boolean state;
    private SimanoService service = null;
    private ServiceConnection sconn;
    private SimanoReceiver receiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.d("start.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
	
	Button btn_pref = (Button) findViewById(R.id.pref);
	btn_pref.setOnClickListener(new OnClickListener() {
	    public void onClick(View v) {
		openPref();
	    }
	});
	
	startService(new Intent(this, SimanoService.class));
	
	sconn = new ServiceConnection() {
	    public void onServiceConnected(ComponentName name, IBinder binder) {
		Log.d("");
		service = ((SimanoService.SimanoBinder) binder).getService();
		service.requestBroadcast();
	    }
	    public void onServiceDisconnected(ComponentName name) {
		Log.d("");
	    }
	};
	
	receiver = new SimanoReceiver();
	registerReceiver(receiver, new IntentFilter("jp.ddo.masm11.simano.STATE"));
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	btn_pref.setText(hostname + ":" + port);
	Log.d("end.");
    }
    
    @Override
    protected void onStart() {
	super.onStart();
	
	bindService(new Intent(this, SimanoService.class), sconn, 0);
    }
    
    @Override
    protected void onStop() {
	unbindService(sconn);
	
	super.onStop();
    }
    
    @Override
    protected void onDestroy() {
	unregisterReceiver(receiver);
	
	super.onDestroy();
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	if (requestCode == 123) {
	    String hostname = PrefActivity.getHostname(this);
	    int port = PrefActivity.getPort(this);
	    
	    service.setServer(hostname, port);
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
	Log.d("");
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	service.setServer(hostname, port);
	Button btn_pref = (Button) findViewById(R.id.pref);
	btn_pref.setText(hostname + ":" + port);
    }
    
    private void setState(boolean state) {
	this.state = state;
	
	TextView text = (TextView) findViewById(R.id.state);
	text.setText(state ? "新着メールがあります" : "新着メールはありません");
    }
}
