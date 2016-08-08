package jp.ddo.masm11.simano;

class Log {
    static void d(String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.d(klass, method + "(): " + msg);
    }
    
    static void d(Throwable e, String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.d(klass, method + "(): " + msg, e);
    }
    
    static void i(String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.i(klass, method + "(): " + msg);
    }
    
    static void i(Throwable e, String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.i(klass, method + "(): " + msg, e);
    }
    
    static void w(String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.w(klass, method + "(): " + msg);
    }
    
    static void w(Throwable e, String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.w(klass, method + "(): " + msg, e);
    }
    
    static void e(String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.e(klass, method + "(): " + msg);
    }
    
    static void e(Throwable e, String fmt, Object... args) {
	String[] stkinf = getStackInfo();
	String klass = stkinf[0];
	String method = stkinf[1];
	String msg = String.format(fmt, args);
	android.util.Log.e(klass, method + "(): " + msg, e);
    }
    
    static String[] getStackInfo() {
	StackTraceElement[] elems = Thread.currentThread().getStackTrace();
/*
	for (int i = 0; i < elems.length; i++) {
	    StackTraceElement e = elems[i];
	    android.util.Log.i("simano", "" + i + ": class: " + e.getClassName());
	    android.util.Log.i("simano", "" + i + ": file: " + e.getFileName());
	    android.util.Log.i("simano", "" + i + ": line: " + e.getLineNumber());
	    android.util.Log.i("simano", "" + i + ": method: " + e.getMethodName());
	}
*/
	return new String[] { elems[4].getClassName().replace("jp.ddo.masm11.simano.", ""),
			      elems[4].getMethodName() };
    }
}
