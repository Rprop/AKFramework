package de.robv.android.xposed;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * This class contains most of Xposed's central logic, such as initialization
 * and callbacks used by the native side. It also includes methods to add new
 * hooks.
 */
@SuppressWarnings("JniMissingFunction")
public final class XposedBridge {
	/**
	 * The system class loader which can be used to locate Android framework
	 * classes. Application classes cannot be retrieved from it.
	 *
	 * @see ClassLoader#getSystemClassLoader
	 */
	public static final ClassLoader BOOTCLASSLOADER = ClassLoader
			.getSystemClassLoader();

	/** @hide */
	public static final String TAG = "AKXposed";

	/** Use {@link #getXposedVersion()} instead. */
	public static int XPOSED_BRIDGE_VERSION = 100;

	/* package */static boolean isZygote = true;

	private static int runtime = 0;
	private static final int RUNTIME_DALVIK = 1;
	private static final int RUNTIME_ART = 2;

	/* package */static boolean disableHooks = false;

	// This field is set "magically" on MIUI.
	/* package */static long BOOT_START_TIME;

	private static final Object[] EMPTY_ARRAY = new Object[0];

	// built-in handlers
	private static final Map<Member, CopyOnWriteSortedSet<XC_MethodHook>> sHookedMethodCallbacks = new HashMap<>();
	/* package */static final CopyOnWriteSortedSet<XC_LoadPackage> sLoadedPackageCallbacks = new CopyOnWriteSortedSet<>();
	/* package */static final CopyOnWriteSortedSet<XC_InitPackageResources> sInitPackageResourcesCallbacks = new CopyOnWriteSortedSet<>();

	private native static boolean hadInitErrors();

	private static native int getRuntime();

	/* package */static native boolean startsSystemServer();

	/* package */static native String getStartClassName();

	/* package */native static boolean initXResourcesNative();

	/**
	 * Returns the currently installed version of the Xposed framework.
	 */
	public static int getXposedVersion() {
		return XPOSED_BRIDGE_VERSION;
	}

	/**
	 * Writes a message to the Xposed error log.
	 *
	 * <p class="warning">
	 * <b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging. If
	 * you want to write information/debug messages, use logcat.
	 *
	 * @param text
	 *            The log message.
	 */
	public static void log(String text) {
		Log.e(TAG, text);
		XposedInit.log(text);
	}

	/**
	 * Logs a stack trace to the Xposed error log.
	 *
	 * <p class="warning">
	 * <b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging. If
	 * you want to write information/debug messages, use logcat.
	 *
	 * @param t
	 *            The Throwable object for the stack trace.
	 */
	public static void log(Throwable t) {
		Log.wtf(TAG, t);
		XposedInit.log(Log.getStackTraceString(t));
	}

	public static XC_MethodHook.Unhook hookMethod(Member hookMethod,
			XC_MethodHook callback) {
		final andhook.lib.xposed.XC_MethodHook.Unhook uk = andhook.lib.xposed.XposedBridge
				.hookMethod(hookMethod, new XC_MethodHook.AK(callback));
		if (uk != null) {
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH)
				Log.v(TAG, "hook " + hookMethod.getDeclaringClass().getName()
						+ "@" + hookMethod.getName() + " successfully");
			return callback.new Unhook(uk.getHookedMethod());
		} else {
			Log.e(TAG, "failed to hook "
					+ hookMethod.getDeclaringClass().getName() + "@"
					+ hookMethod.getName());
			return null;
		}
	}

	@Deprecated
	public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
		andhook.lib.xposed.XposedBridge.unhookMethod(hookMethod,
				callback.ak_callback);
	}

	/**
	 * Hooks all methods with a certain name that were declared in the specified
	 * class. Inherited methods and constructors are not considered. For
	 * constructors, use {@link #hookAllConstructors} instead.
	 *
	 * @param hookClass
	 *            The class to check for declared methods.
	 * @param methodName
	 *            The name of the method(s) to hook.
	 * @param callback
	 *            The callback to be executed when the hooked methods are
	 *            called.
	 * @return A set containing one object for each found method which can be
	 *         used to unhook it.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass,
			String methodName, XC_MethodHook callback) {
		HashSet<XC_MethodHook.Unhook> unhooks = new HashSet<>();
		for (Member method : hookClass.getDeclaredMethods())
			if (method.getName().equals(methodName))
				unhooks.add(hookMethod(method, callback));
		return unhooks;
	}

	/**
	 * Hook all constructors of the specified class.
	 *
	 * @param hookClass
	 *            The class to check for constructors.
	 * @param callback
	 *            The callback to be executed when the hooked constructors are
	 *            called.
	 * @return A set containing one object for each found constructor which can
	 *         be used to unhook it.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public static Set<XC_MethodHook.Unhook> hookAllConstructors(
			Class<?> hookClass, XC_MethodHook callback) {
		HashSet<XC_MethodHook.Unhook> unhooks = new HashSet<>();
		for (Member constructor : hookClass.getDeclaredConstructors())
			unhooks.add(hookMethod(constructor, callback));
		return unhooks;
	}

	private static Object handleHookedMethod(Member method,
			int originalMethodId, Object additionalInfoObj, Object thisObject,
			Object[] args) throws Throwable {
		return null;
	}

	/**
	 * Adds a callback to be executed when an app ("Android package") is loaded.
	 *
	 * <p class="note">
	 * You probably don't need to call this. Simply implement
	 * {@link IXposedHookLoadPackage} in your module class and Xposed will take
	 * care of registering it as a callback.
	 *
	 * @param callback
	 *            The callback to be executed.
	 * @hide
	 */
	public static void hookLoadPackage(XC_LoadPackage callback) {
		synchronized (sLoadedPackageCallbacks) {
			sLoadedPackageCallbacks.add(callback);
		}
	}

	/**
	 * Adds a callback to be executed when the resources for an app are
	 * initialized.
	 *
	 * <p class="note">
	 * You probably don't need to call this. Simply implement
	 * {@link IXposedHookInitPackageResources} in your module class and Xposed
	 * will take care of registering it as a callback.
	 *
	 * @param callback
	 *            The callback to be executed.
	 * @hide
	 */
	public static void hookInitPackageResources(XC_InitPackageResources callback) {
		synchronized (sInitPackageResourcesCallbacks) {
			sInitPackageResourcesCallbacks.add(callback);
		}
	}

	/**
	 * Intercept every call to the specified method and call a handler function
	 * instead.
	 * 
	 * @param method
	 *            The method to intercept
	 */
	private native synchronized static void hookMethodNative(Member method,
			Class<?> declaringClass, int slot, Object additionalInfo);

	private native static Object invokeOriginalMethodNative(Member method,
			int methodId, Class<?>[] parameterTypes, Class<?> returnType,
			Object thisObject, Object[] args) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException;

	/**
	 * Basically the same as {@link Method#invoke}, but calls the original
	 * method as it was before the interception by Xposed. Also, access
	 * permissions are not checked.
	 *
	 * <p class="caution">
	 * There are very few cases where this method is needed. A common mistake is
	 * to replace a method and then invoke the original one based on dynamic
	 * conditions. This creates overhead and skips further hooks by other
	 * modules. Instead, just hook (don't replace) the method and call
	 * {@code param.setResult(null)} in {@link XC_MethodHook#beforeHookedMethod}
	 * if the original method should be skipped.
	 *
	 * @param method
	 *            The method to be called.
	 * @param thisObject
	 *            For non-static calls, the "this" pointer, otherwise
	 *            {@code null}.
	 * @param args
	 *            Arguments for the method call as Object[] array.
	 * @return The result returned from the invoked method.
	 * @throws NullPointerException
	 *             if {@code receiver == null} for a non-static method
	 * @throws IllegalAccessException
	 *             if this method is not accessible (see
	 *             {@link AccessibleObject})
	 * @throws IllegalArgumentException
	 *             if the number of arguments doesn't match the number of
	 *             parameters, the receiver is incompatible with the declaring
	 *             class, or an argument could not be unboxed or converted by a
	 *             widening conversion to the corresponding parameter type
	 * @throws InvocationTargetException
	 *             if an exception was thrown by the invoked method
	 */
	public static Object invokeOriginalMethod(Member method, Object thisObject,
			Object[] args) {
		return andhook.lib.xposed.XposedBridge.invokeOriginalMethod(method,
				thisObject, args);
	}

	/* package */static void setObjectClass(Object obj, Class<?> clazz) {
		if (clazz.isAssignableFrom(obj.getClass())) {
			throw new IllegalArgumentException("Cannot transfer object from "
					+ obj.getClass() + " to " + clazz);
		}
		setObjectClassNative(obj, clazz);
	}

	private static native void setObjectClassNative(Object obj, Class<?> clazz);

	/* package */static native void dumpObjectNative(Object obj);

	/* package */static Object cloneToSubclass(Object obj, Class<?> targetClazz) {
		if (obj == null)
			return null;

		if (!obj.getClass().isAssignableFrom(targetClazz))
			throw new ClassCastException(targetClazz + " doesn't extend "
					+ obj.getClass());

		return cloneToSubclassNative(obj, targetClazz);
	}

	private static native Object cloneToSubclassNative(Object obj,
			Class<?> targetClazz);

	private static native void removeFinalFlagNative(Class<?> clazz);

	/* package */static native void closeFilesBeforeForkNative();

	/* package */static native void reopenFilesAfterForkNative();

	/* package */static native void invalidateCallersNative(Member[] methods);

	/** @hide */
	public static final class CopyOnWriteSortedSet<E> {
		private transient volatile Object[] elements = EMPTY_ARRAY;

		@SuppressWarnings("UnusedReturnValue")
		public synchronized boolean add(E e) {
			int index = indexOf(e);
			if (index >= 0)
				return false;

			Object[] newElements = new Object[elements.length + 1];
			System.arraycopy(elements, 0, newElements, 0, elements.length);
			newElements[elements.length] = e;
			Arrays.sort(newElements);
			elements = newElements;
			return true;
		}

		@SuppressWarnings("UnusedReturnValue")
		public synchronized boolean remove(E e) {
			int index = indexOf(e);
			if (index == -1)
				return false;

			Object[] newElements = new Object[elements.length - 1];
			System.arraycopy(elements, 0, newElements, 0, index);
			System.arraycopy(elements, index + 1, newElements, index,
					elements.length - index - 1);
			elements = newElements;
			return true;
		}

		private int indexOf(Object o) {
			for (int i = 0; i < elements.length; i++) {
				if (o.equals(elements[i]))
					return i;
			}
			return -1;
		}

		public Object[] getSnapshot() {
			return elements;
		}
	}

	private static class AdditionalHookInfo {
		final CopyOnWriteSortedSet<XC_MethodHook> callbacks;
		final Class<?>[] parameterTypes;
		final Class<?> returnType;

		private AdditionalHookInfo(
				CopyOnWriteSortedSet<XC_MethodHook> callbacks,
				Class<?>[] parameterTypes, Class<?> returnType) {
			this.callbacks = callbacks;
			this.parameterTypes = parameterTypes;
			this.returnType = returnType;
		}
	}
}
