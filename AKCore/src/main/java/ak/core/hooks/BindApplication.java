package ak.core.hooks;

import ak.core.Xposed;
import ak.core.XposedModules;
import andhook.lib.xposed.XC_MethodHook;
import andhook.lib.xposed.XposedHelpers;
import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.CompatibilityInfo;
import android.os.UserHandle;
import android.util.Log;

public final class BindApplication extends XC_MethodHook {
	private static final String TAG = "AKBindApp";
	
	@Override
	protected void beforeHookedMethod(final MethodHookParam param) {
		try {
			final ApplicationInfo appInfo = (ApplicationInfo) XposedHelpers.getObjectField(param.args[0], "appInfo");
			final String processName = (String) XposedHelpers.getObjectField(param.args[0], "processName");
			Log.i(TAG, "onBindApplication called, processName = " + processName);
			Log.i(TAG, "myUserId = " + UserHandle.myUserId() + 
					", uid = " + appInfo.uid +
					", processName = " + appInfo.processName + 
					", packageName = " + appInfo.packageName +
					", className = " + appInfo.className +
					", nativeLibraryDir = " + appInfo.nativeLibraryDir +
					", dataDir = " + appInfo.dataDir);
			
			final ActivityThread activityThread = (ActivityThread) param.thisObject;
			XposedHelpers.setObjectField(activityThread, "mBoundApplication", param.args[0]);
			
			final CompatibilityInfo compatInfo = (CompatibilityInfo) XposedHelpers.getObjectField(param.args[0], "compatInfo");
			final LoadedApk loadedApk = activityThread.getPackageInfoNoCheck(appInfo, compatInfo);
			Xposed.init();
			if (appInfo.packageName.equalsIgnoreCase(Xposed.XPOSED_INSTALLER_PKG_NAME)) {
				Xposed.handleInstaller(loadedApk);
			} else {
				// ActivityThread.currentApplication() == null
				try {
					final Context context = activityThread.getSystemContext();
					final PackageManager pkgMgr = context.getPackageManager();
					final ApplicationInfo appMetaInfo = pkgMgr.getApplicationInfo(appInfo.packageName,
							PackageManager.GET_META_DATA);
					if (appMetaInfo.metaData != null && appMetaInfo.metaData.containsKey("xposedmodule")) {
						Xposed.handleModule(loadedApk);
					}
				} catch (final Throwable t) {
					Log.wtf(TAG, t);
				}
			}
			
			final XposedModules xp = new XposedModules();
			xp.preload();
			xp.load(loadedApk);
		} catch (final Throwable tr) {
			Log.wtf(TAG, tr);
		}
	}
}
