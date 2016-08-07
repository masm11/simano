package jp.ddo.masm11.simano;

import java.util.List;
import java.util.LinkedList;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class MainActivity extends AppCompatActivity {
    private class SimanoReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
	    String action = intent.getAction();
	    if (action.equals("jp.ddo.masm11.simano.STATE")) {
		boolean state = intent.getBooleanExtra("state", false);
		Log.d("state: state=%b", state);
		setState(state);
	    }
	}
    }
    
    private SimanoService service = null;
    private ServiceConnection sconn;
    private SimanoReceiver receiver;
    private ArrayAdapter<String> adapter;
    
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
	// registerReceiver(receiver, new IntentFilter("jp.ddo.masm11.simano.STATE"));
	IntentFilter filter = new IntentFilter();
	filter.addAction("jp.ddo.masm11.simano.STATE");
	registerReceiver(receiver, filter);
	
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
    
    @Override
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
	Intent intent = new Intent(this, PrefActivity.class);
	startActivityForResult(intent, 123);
    }
    
    private void setState(boolean state) {
	TextView text = (TextView) findViewById(R.id.state);
	text.setText(state ? "新着メールがあります" : "新着メールはありません");
    }
}
