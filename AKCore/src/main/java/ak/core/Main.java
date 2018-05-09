package ak.core;

import ak.core.hooks.*;
import andhook.lib.xposed.XposedHelpers;
import android.app.ActivityThread;
import android.util.Log;

public final class Main {
	private static final String TAG = "AKMain";

	/**
	 * This gets called after the VM has been created, but before we run any
	 * code.
	 */
	public static void onVmCreated() {
		Log.v(TAG, "onVmCreated called, classloader = " + Main.class.getClassLoader());
		
		XposedHelpers.findAndHookMethod(ActivityThread.class,
				"handleBindApplication",
				"android.app.ActivityThread$AppBindData", new BindApplication());
	}
	
	/**
     * Called by the zygote prior to every fork. Each call to {@code preFork}
     * is followed by a matching call to {@link #postForkChild()} on the child
     * process and {@link #postForkParent()} on the parent process.
     */
    public static void preFork() {
    	Log.v(TAG, "preFork called");
    }

    /**
     * Called by the zygote in the parent process after every fork.
     */
    public static void postForkParent() {
    	Log.v(TAG, "postForkParent called");
    }
    
    /**
     * Called by the zygote in the child process after every fork.
     */
    public static void postForkChild() {
    	Log.v(TAG, "postForkChild called");
    }

	/**
	 * Used to register native methods from the framework.
	 */
	public static native void registerFrameworkNatives(final ClassLoader loader);
	
	
	/**
	 * Used to change the access permissions to file system objects (files and directories) to 665.
	 */
	public static native void makeWorldAccessible(final String path);
}
