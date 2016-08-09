package jp.ddo.masm11.simano;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

class Log {
    static void d(String fmt, Object... args) {
	common(android.util.Log.DEBUG, null, fmt, args);
    }
    
    static void d(Throwable e, String fmt, Object... args) {
	common(android.util.Log.DEBUG, e, fmt, args);
    }
    
    static void i(String fmt, Object... args) {
	common(android.util.Log.INFO, null, fmt, args);
    }
    
    static void i(Throwable e, String fmt, Object... args) {
	common(android.util.Log.INFO, e, fmt, args);
    }
    
    static void w(String fmt, Object... args) {
	common(android.util.Log.WARN, null, fmt, args);
    }
    
    static void w(Throwable e, String fmt, Object... args) {
	common(android.util.Log.WARN, e, fmt, args);
    }
    
    static void e(String fmt, Object... args) {
	common(android.util.Log.ERROR, null, fmt, args);
    }
    
    static void e(Throwable e, String fmt, Object... args) {
	common(android.util.Log.ERROR, e, fmt, args);
    }
    
    private static void common(int priority, Throwable e, String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	StringBuffer buf = new StringBuffer();
	buf.append(stkinf[1]);
	buf.append("(): ");
	buf.append(String.format(fmt, args));
	if (e != null) {
	    buf.append('\n');
	    buf.append(android.util.Log.getStackTraceString(e));
	}
	String msg = buf.toString();
	
	android.util.Log.println(priority, klass, msg);
	log_to_file(priority, klass, msg);
    }
    
    private static PrintWriter writer = null;
    private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    synchronized private static void log_to_file(int priority, String tag, String msg) {
	if (writer == null) {
	    try {
		writer = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/Android/data/jp.ddo.masm11.simano/cache/log.txt", true)));
		writer.println("================");
		writer.flush();
	    } catch (IOException e) {
		android.util.Log.e("Log", "ioexception", e);
	    }
	}
	if (writer != null) {
	    String time = formatter.format(new Date());
	    writer.println(time + " " + tag + ": " + msg);
	    writer.flush();
	}
    }
    
    private static String[] getStackInfo() {
	StackTraceElement[] elems = Thread.currentThread().getStackTrace();
	
	return new String[] { elems[5].getClassName().replace("jp.ddo.masm11.simano.", ""),
			      elems[5].getMethodName() };
    }
}
