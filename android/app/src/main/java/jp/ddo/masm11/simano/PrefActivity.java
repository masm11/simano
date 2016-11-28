package jp.ddo.masm11.simano;

import android.support.v7.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class PrefActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	getFragmentManager().beginTransaction()
		.replace(android.R.id.content, new PrefFragment())
		.commit();
    }
    
    static String getHostname(Context ctx) {
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
	return settings.getString("hostname", "localhost");
    }
    
    static int getPort(Context ctx) {
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
	return Integer.valueOf(settings.getString("port", "0"));
    }
    
    static boolean getUseIPv4Only(Context ctx) {
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
	return Boolean.valueOf(settings.getBoolean("use_ipv4_only", false));
    }
}
