package ak.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import andhook.lib.AndHook;
import android.app.LoadedApk;
import android.util.Log;

public final class XposedModules {
	private static final String TAG = "AKXposed";

	// /data/app/andhook.test-1/base.apk
	private ArrayList<String> modules = null;
	// andhook.test
	private ArrayList<String> enabled_modules = null;

	private static void logXposed(final String text) {
		de.robv.android.xposed.XposedBridge.log(text);
	}

	private static void logXposed(final Throwable t) {
		de.robv.android.xposed.XposedBridge.log(t);
	}

	private static ArrayList<String> readList(final String path) {
		final File f = new File(path);
		if (!f.exists()) {
			Log.e(TAG, "No such file or directory " + path);
			return null;
		}
		final ArrayList<String> s = new ArrayList<>();
		try {
			final BufferedReader reader = new BufferedReader(new FileReader(f));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("#") && !line.isEmpty()) {
					s.add(line);
				}
			}
			reader.close();
		} catch (final Exception e) {
			Log.e(TAG, "Error reading " + path, e);
		}
		return s.isEmpty() ? null : s;
	}

	public void preload() {
		if (new File(Xposed.BASE_DIR).exists()) {
			if (new File(Xposed.DISABLED_FILE).exists()) {
				Log.w(TAG, "Xposed is disabled, skipping...");
			} else {
				this.modules = readList(Xposed.MODULES_LIST_FILE);
				this.enabled_modules = readList(Xposed.ENABLED_MODULES_LIST_FILE);
				if (this.modules == null || this.enabled_modules == null) {
					Log.w(TAG, "No enabled xposed modules found!");
					this.modules = null;
					this.enabled_modules = null;
				} else if (this.modules.size() != this.enabled_modules.size()) {
					Log.e(TAG, "Unexpected number of modules!");
					this.modules = null;
					this.enabled_modules = null;
				}
			}
		} else {
			Log.w(TAG,
					"Xposed installer not found, all modules are disabled by default!");
		}
	}
	
	private static void makeDirWorldAccessible(final File sharedLibraryDir) {
		Log.v(TAG,
				"sharedLibraryDir = " + sharedLibraryDir.getAbsolutePath());
		Log.v(TAG,
				"sharedLibraryDir.canRead = " + sharedLibraryDir.canRead());
		
		Main.makeWorldAccessible(sharedLibraryDir.getAbsolutePath());
		final File[] files = sharedLibraryDir.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isFile())
					Main.makeWorldAccessible(file.getAbsolutePath());
			}
		}
	}

	public void load(final LoadedApk loadedApk) {
		if (this.modules == null || this.enabled_modules == null)
			return;

		for (int i = 0; i < enabled_modules.size(); ++i) {
			String path = modules.get(i);
			if (!new File(path).exists()) {
				// package updated?
				logXposed("Module apk " + path + " not exists!");
				if (path.contains("-1/"))
					path = path.replace("-1/", "-2/");
				else
					path = path.replace("-2/", "-1/");
			}

			/*
			 * final File sharedLibraryDir = new File(loadedApk.getDataDir(), "xlibs"); 
			 * NativeUtils.copyNativeBinaries(path, sharedLibraryDir);
			 */
			final File sharedLibraryDir = new File(loadedApk.getDataDirFile()
					.getParent(), enabled_modules.get(i) + "/lib");
			makeDirWorldAccessible(sharedLibraryDir);

			final PathClassLoader cl = new PathClassLoader(path,
					sharedLibraryDir.getAbsolutePath(),
					Xposed.getXposedClassLoader());
			final InputStream xposed_init = cl
					.getResourceAsStream("assets/xposed_init");
			if (xposed_init == null) {
				logXposed("module apk " + path
						+ " does not contain xposed_init, skipped!");
				continue;
			}

			final BufferedReader xposed_init_reader = new BufferedReader(
					new InputStreamReader(xposed_init));
			try {
				AndHook.stopDaemons();
				String entry_class;
				while ((entry_class = xposed_init_reader.readLine()) != null) {
					entry_class = entry_class.trim();
					if (entry_class.isEmpty() || entry_class.startsWith("#"))
						continue;

					Log.v(TAG, "Loading xposed module entry " + entry_class
							+ "...");
					try {
						de.robv.android.xposed.XposedInit.call(loadedApk,
								cl.loadClass(entry_class), path);
					} catch (final Throwable t) {
						logXposed(t);
					}
				}
				AndHook.startDaemons();
			} catch (final Throwable t) {
				logXposed(t);
			} finally {
				try {
					xposed_init_reader.close();
					xposed_init.close();
				} catch (final Throwable ignored) {
				}
			}
		}
	}
}
