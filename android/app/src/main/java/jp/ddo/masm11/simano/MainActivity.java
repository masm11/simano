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
		boolean state = intent.getBooleanExtra("jp.ddo.masm11.simano.STATE", false);
		Log.d("state: state=%b", state);
		setState(state);
	    }
	}
    }
    
    private SimanoReceiver receiver;
    private ArrayAdapter<String> adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.d("start.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
	
	Button btn_pref = (Button) findViewById(R.id.pref);
	btn_pref.setOnClickListener(new OnClickListener() {
	    @Override
	    public void onClick(View v) {
		openPref();
	    }
	});
	
	Button btn_dbg = (Button) findViewById(R.id.debug);
	btn_dbg.setOnClickListener(new OnClickListener() {
	    @Override
	    public void onClick(View v) {
		Intent intent = new Intent(MainActivity.this, SimanoService.class);
		intent.setAction("jp.ddo.masm11.simano.DEBUG");
		startService(intent);
	    }
	});
	
	receiver = new SimanoReceiver();
	// registerReceiver(receiver, new IntentFilter("jp.ddo.masm11.simano.STATE"));
	IntentFilter filter = new IntentFilter();
	filter.addAction("jp.ddo.masm11.simano.STATE");
	registerReceiver(receiver, filter);
	
	Intent intent = new Intent(this, SimanoService.class);
	intent.setAction("jp.ddo.masm11.simano.BCAST_REQ");
	startService(intent);
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	btn_pref.setText(hostname + ":" + port);
	Log.d("end.");
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
	    
	    Intent intent = new Intent(this, SimanoService.class);
	    intent.setAction("jp.ddo.masm11.simano.SET_SERVER");
	    intent.putExtra("jp.ddo.masm11.simano.HOSTNAME", hostname);
	    intent.putExtra("jp.ddo.masm11.simano.PORT", port);
	    startService(intent);
	    
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
