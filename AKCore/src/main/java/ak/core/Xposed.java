package ak.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Properties;

import andhook.lib.HookHelper;
import andhook.lib.xposed.XC_MethodHook;
import andhook.lib.xposed.XposedHelpers;
import android.app.LoadedApk;
import android.content.pm.ApplicationInfo;
import android.os.Build.VERSION;
import android.util.Log;

public final class Xposed extends XC_MethodHook {
	private static final String TAG = "AKXposed";
	static final int XPOSED_VERSION_INT = 100;
	public static final String XPOSED_INSTALLER_PKG_NAME = "de.robv.android.xposed.installer";
	private static final String ANDROID_DATA_DIR = "/data/";
	private static final String DEVICE_PROTECTED_DIR = ANDROID_DATA_DIR
			+ "user_de/0/";
	private static final String CREDENTIAL_PROTECTED_DIR = ANDROID_DATA_DIR
			+ "data/";
	static final String BASE_DIR = (VERSION.SDK_INT >= 24 ? DEVICE_PROTECTED_DIR
			: CREDENTIAL_PROTECTED_DIR)
			+ XPOSED_INSTALLER_PKG_NAME + "/";
	static final String MODULES_LIST_FILE = BASE_DIR + "conf/modules.list";
	static final String ENABLED_MODULES_LIST_FILE = BASE_DIR
			+ "conf/enabled_modules.list";
	static final String DISABLED_FILE = BASE_DIR + "conf/disabled";
	static final String LOG_FILE = BASE_DIR + "log/error.log";

	@SuppressWarnings("deprecation")
	private static void writeXposedProperties(final File propertyFile,
			final boolean retry) {
		final Properties properties = new Properties();
		properties.put("version", String.valueOf(XPOSED_VERSION_INT));
		properties.put("minsdk", "50");
		properties.put("arch", android.os.Build.CPU_ABI);
		properties.put("maxsdk", String.valueOf(XPOSED_VERSION_INT));

		FileOutputStream f = null;
		try {
			f = new FileOutputStream(propertyFile);
			properties.store(f, null);
		} catch (final IOException e) {
			propertyFile.delete();
			if (retry)
				writeXposedProperties(propertyFile, false);
		} finally {
			if (f != null)
				try {
					f.close();
				} catch (final IOException ignored) {
				}
		}
	}

	public static void init() {
		de.robv.android.xposed.XposedInit.init(XPOSED_VERSION_INT, LOG_FILE,
				new File(BASE_DIR, "shared_prefs/").getAbsolutePath());
	}

	static ClassLoader getXposedClassLoader() {
		return de.robv.android.xposed.XposedInit.class.getClassLoader();
	}

	public static void handleModule(final LoadedApk loadedApk) {
		Log.v(TAG, "handleModule " + loadedApk);
		try {
			final ClassLoader cl = loadedApk.getClassLoader();
			final Field parent = HookHelper.findFieldHierarchically(cl.getClass(), "parent");
			parent.setAccessible(true);
			parent.set(cl, getXposedClassLoader());
			Log.v(TAG, "handleModule " + parent.get(cl));
		} catch (final Throwable t) {
			Log.wtf(TAG, t);
		}
	}

	public static void handleInstaller(final LoadedApk loadedApk) {
		final ClassLoader cl = loadedApk.getClassLoader();
		final ApplicationInfo appInfo = loadedApk.getApplicationInfo();
		final File xposedProp = new File(appInfo.dataDir, "xposed_prop");
		if (!xposedProp.exists()) {
			writeXposedProperties(xposedProp, true);
		}

		final Class<?> xposedApp = XposedHelpers.findClass(
				XPOSED_INSTALLER_PKG_NAME + ".XposedApp", cl);
		try {
			final String[] xposed_prop_files = (String[]) XposedHelpers
					.getStaticObjectField(xposedApp, "XPOSED_PROP_FILES");
			final String xposedPropPath = xposedProp.getPath();
			for (int i = 0; i < xposed_prop_files.length; ++i) {
				xposed_prop_files[i] = xposedPropPath;
			}
		} catch (final Throwable ignored) {
		}

		XposedHelpers.findAndHookMethod(xposedApp, "getActiveXposedVersion",
				new Xposed());
		XposedHelpers.findAndHookMethod(xposedApp, "getInstalledXposedVersion",
				new Xposed());

		// unnecessary, just for test
		try {
			XposedHelpers.findAndHookMethod(
					"android/support/design/widget/Snackbar", cl, "setText",
					CharSequence.class, new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(
								final MethodHookParam param) {
							final String text = param.args[0].toString();
							if (text.equalsIgnoreCase("Xposed Framework will be enabled on next reboot")) {
								param.args[0] = (CharSequence) "Xposed Framework is enabled";
							} else if (text
									.equalsIgnoreCase("Xposed Framework will be disabled on next reboot")) {
								param.args[0] = (CharSequence) "Xposed Framework is disabled";
							}
						}
					});
		} catch (final Throwable ignored) {
		}
	}

	@Override
	protected void beforeHookedMethod(final MethodHookParam param) {
		param.setResult(XPOSED_VERSION_INT);
	}
}
