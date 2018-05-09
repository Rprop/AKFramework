package de.robv.android.xposed;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import android.app.LoadedApk;
import android.os.Environment;
import android.os.Build.VERSION;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit {
	static String XPOSED_PREF_DATA_PATH = null;
	private static String XPOSED_LOG_PATH = null;
	private static BufferedWriter writer = null;

	// @Keep
	static synchronized void log(final String text) {
		if (XPOSED_LOG_PATH != null) {
			if (writer == null) {
				try {
					writer = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream(XPOSED_LOG_PATH, true)));
				} catch (final Exception ignored) {
					return;
				}
			}
			try {
				writer.write(text.trim() + "\n");
				writer.flush();
			} catch (final Exception ignored) {
				try {
					writer.close();
				} catch (final Exception e) {
				}
				writer = null;
			}
		}
	}

	// @Keep
	public static void init(final int xposed_version, final String log_path, final String prefs_path) {
		XposedBridge.XPOSED_BRIDGE_VERSION = xposed_version;
		try {
			SELinuxHelper.initOnce();
			SELinuxHelper.initForProcess("app");
		} catch (final Throwable ignored) {
		}
		
		XPOSED_LOG_PATH = log_path;
		XPOSED_PREF_DATA_PATH = prefs_path;
	}

	private static native void makeWorldAccessible(final String path);
	
	private static File makeWorldAccessible(final File file) {
		makeWorldAccessible(file.getParentFile().getAbsolutePath());
		makeWorldAccessible(file.getAbsolutePath());
		return file;
	}
	
	static File getSharedPreferences(final String packageName, final String prefFileName) {
		final String path = packageName + "/shared_prefs/" + prefFileName + ".xml";
		final File file = makeWorldAccessible(new File(Environment.getDataDirectory(), "data/" + path));
		if (!file.canRead() && VERSION.SDK_INT >= 24) {
			return makeWorldAccessible(new File(Environment.getDataDirectory(), "user_de/0/" + path));
		}
		return file;
	}
	
	// @Keep
	public static void call(final LoadedApk loadedApk,
			final Class<?> entry_class, final String modulePath)
			throws Throwable {
		if (!IXposedMod.class.isAssignableFrom(entry_class)) {
			final String e = "class " + entry_class + " cannot be cast to "
					+ IXposedMod.class;
			log(e);
			throw new ClassCastException(e);
		}

		final Object inst = entry_class.newInstance();
		if (inst instanceof IXposedHookZygoteInit) {
			final IXposedHookZygoteInit.StartupParam param = new IXposedHookZygoteInit.StartupParam();
			param.modulePath = modulePath;
			param.startsSystemServer = false;
			((IXposedHookZygoteInit) inst).initZygote(param);
		}

		if (inst instanceof IXposedHookLoadPackage) {
			final XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage> xc_callbacks = new XposedBridge.CopyOnWriteSortedSet<>();
			xc_callbacks.add(new IXposedHookLoadPackage.Wrapper(
					(IXposedHookLoadPackage) inst));
			try {
				final Object[] existingCallbacks = XposedBridge.sLoadedPackageCallbacks
						.getSnapshot();
				for (final Object cb : existingCallbacks) {
					xc_callbacks.add((XC_LoadPackage) cb);
				}
			} catch (final Exception ignored) {
			}

			final XC_LoadPackage.LoadPackageParam param = new XC_LoadPackage.LoadPackageParam(xc_callbacks);
			param.appInfo = loadedApk.getApplicationInfo();
			param.packageName = param.appInfo.packageName;
			param.classLoader = loadedApk.getClassLoader();
			param.isFirstApplication = true;
			param.processName = param.appInfo.processName;
			XC_LoadPackage.callAll(param);
		}

		// @TODO: support resource hook
		/*
		 * if (inst instanceof IXposedHookInitPackageResources) {
		 * 
		 * }
		 */
	}
}
