package jp.ddo.masm11.simano;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
	try {
	    if (intent != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
		Intent i = new Intent(context, SimanoService.class);
		i.setAction("jp.ddo.masm11.simano.BOOT");
		context.startService(i);
	    }
	} catch (Throwable e) {	// 念の為
	}
    }
}
