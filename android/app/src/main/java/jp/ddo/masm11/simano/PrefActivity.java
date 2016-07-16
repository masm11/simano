package jp.ddo.masm11.simano;

import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class PrefActivity extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	addPreferencesFromResource(R.layout.activity_pref);
    }
    
    static String getHostname(Context ctx) {
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
	return settings.getString("hostname", "localhost");
    }
    
    static int getPort(Context ctx) {
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
	return Integer.valueOf(settings.getString("port", "0"));
    }
}
