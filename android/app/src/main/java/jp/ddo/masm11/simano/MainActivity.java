package jp.ddo.masm11.simano;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.content.Context;
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
	    if (action.equals("jp.ddo.masm11.simano.APP_STATE")) {
		String appState = intent.getStringExtra("jp.ddo.masm11.simano.APP_STATE");
		Log.d("appState: appState=%s", appState);
		setAppState(appState);
	    }
	}
    }
    
    private SimanoReceiver receiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.setLogDir(getExternalCacheDir());
	Log.d("start.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
	
	Button btn_pref = (Button) findViewById(R.id.pref);
	assert btn_pref != null;
	btn_pref.setOnClickListener(new OnClickListener() {
	    @Override
	    public void onClick(View v) {
		openPref();
	    }
	});
	
	receiver = new SimanoReceiver();
	IntentFilter filter = new IntentFilter();
	filter.addAction("jp.ddo.masm11.simano.STATE");
	filter.addAction("jp.ddo.masm11.simano.APP_STATE");
	registerReceiver(receiver, filter);
	
	Intent intent = new Intent(this, SimanoService.class);
	intent.setAction("jp.ddo.masm11.simano.BCAST_REQ");
	startService(intent);
	
	String hostname = PrefActivity.getHostname(this);
	int port = PrefActivity.getPort(this);
	btn_pref.setText(getResources().getString(R.string.server_format, hostname, port));
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
	    boolean useIPv4Only = PrefActivity.getUseIPv4Only(this);
	    
	    Intent intent = new Intent(this, SimanoService.class);
	    intent.setAction("jp.ddo.masm11.simano.SET_SERVER");
	    intent.putExtra("jp.ddo.masm11.simano.HOSTNAME", hostname);
	    intent.putExtra("jp.ddo.masm11.simano.PORT", port);
	    intent.putExtra("jp.ddo.masm11.simano.USE_IPV4_ONLY", useIPv4Only);
	    startService(intent);
	    
	    Button btn_pref = (Button) findViewById(R.id.pref);
	    assert btn_pref != null;
	    btn_pref.setText(getResources().getString(R.string.server_format, hostname, port));
	}
	
	super.onActivityResult(requestCode, resultCode, data);
    }
    
    private void openPref() {
	Intent intent = new Intent(this, PrefActivity.class);
	startActivityForResult(intent, 123);
    }
    
    private void setState(boolean state) {
	TextView text = (TextView) findViewById(R.id.state);
	assert text != null;
	text.setText(state ? R.string.new_mail : R.string.no_mail);
    }
    
    private void setAppState(String appState) {
	TextView text = (TextView) findViewById(R.id.app_state);
	assert text != null;
	int res_id = 0;
	switch (appState) {
	case "CONNECTING":
	    res_id = R.string.app_connecting;
	    break;
	case "READY":
	    res_id = R.string.app_ready;
	    break;
	case "NO_MAIL":
	    break;
	case "NEW_MAIL":
	    break;
	case "CLOSING":
	    res_id = R.string.app_closing;
	    break;
	case "SLEEP":
	    res_id = R.string.app_sleep;
	    break;
	case "FINISH":
	    res_id = R.string.app_finish;
	    break;
	}
	if (res_id != 0)
	    text.setText(res_id);
    }
}
