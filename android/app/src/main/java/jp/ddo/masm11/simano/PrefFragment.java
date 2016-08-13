package jp.ddo.masm11.simano;

import android.preference.PreferenceFragment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.os.Bundle;

public class PrefFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	addPreferencesFromResource(R.layout.activity_pref);
	
	EditTextPreference etp;
	etp = (EditTextPreference) findPreference("hostname");
	etp.setSummary(etp.getText());
	etp.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference pref, Object val) {
		pref.setSummary(val.toString());
		return true;
	    }
	});
	etp = (EditTextPreference) findPreference("port");
	etp.setSummary(etp.getText());
	etp.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
	    @Override
	    public boolean onPreferenceChange(Preference pref, Object val) {
		pref.setSummary(val.toString());
		return true;
	    }
	});
    }
}
