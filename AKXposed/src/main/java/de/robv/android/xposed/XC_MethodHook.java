package de.robv.android.xposed;

import java.lang.reflect.Member;

import de.robv.android.xposed.callbacks.IXUnhook;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Callback class for method hooks.
 *
 * <p>
 * Usually, anonymous subclasses of this class are created which override
 * {@link #beforeHookedMethod} and/or {@link #afterHookedMethod}.
 */
public abstract class XC_MethodHook extends XCallback {
	andhook.lib.xposed.XC_MethodHook ak_callback = null;

	@SuppressWarnings("all")
	static final class AK extends andhook.lib.xposed.XC_MethodHook {
		private final de.robv.android.xposed.XC_MethodHook callback;

		AK(final de.robv.android.xposed.XC_MethodHook callback) {
			this.callback = callback;
			callback.ak_callback = this;
		}

		@Override
		protected final void beforeHookedMethod(
				final andhook.lib.xposed.XC_MethodHook.MethodHookParam param)
				throws Throwable {
			final de.robv.android.xposed.XC_MethodHook.MethodHookParam xparam = new de.robv.android.xposed.XC_MethodHook.MethodHookParam(
					param);
			this.callback.beforeHookedMethod(xparam);
			xparam.writeback();
		}

		@Override
		protected final void afterHookedMethod(
				final andhook.lib.xposed.XC_MethodHook.MethodHookParam param)
				throws Throwable {
			final de.robv.android.xposed.XC_MethodHook.MethodHookParam xparam = new de.robv.android.xposed.XC_MethodHook.MethodHookParam(
					param);
			this.callback.afterHookedMethod(xparam);
			xparam.writeback();
		}
	}

	/**
	 * Creates a new callback with default priority.
	 */
	@SuppressWarnings("deprecation")
	public XC_MethodHook() {
		super();
	}

	/**
	 * Creates a new callback with a specific priority.
	 *
	 * <p class="note">
	 * Note that {@link #afterHookedMethod} will be called in reversed order,
	 * i.e. the callback with the highest priority will be called last. This
	 * way, the callback has the final control over the return value.
	 * {@link #beforeHookedMethod} is called as usual, i.e. highest priority
	 * first.
	 *
	 * @param priority
	 *            See {@link XCallback#priority}.
	 */
	public XC_MethodHook(int priority) {
		super(priority);
	}

	/**
	 * Called before the invocation of the method.
	 *
	 * <p>
	 * You can use {@link MethodHookParam#setResult} and
	 * {@link MethodHookParam#setThrowable} to prevent the original method from
	 * being called.
	 *
	 * <p>
	 * Note that implementations shouldn't call {@code super(param)}, it's not
	 * necessary.
	 *
	 * @param param
	 *            Information about the method call.
	 * @throws Throwable
	 *             Everything the callback throws is caught and logged.
	 */
	protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	}

	/**
	 * Called after the invocation of the method.
	 *
	 * <p>
	 * You can use {@link MethodHookParam#setResult} and
	 * {@link MethodHookParam#setThrowable} to modify the return value of the
	 * original method.
	 *
	 * <p>
	 * Note that implementations shouldn't call {@code super(param)}, it's not
	 * necessary.
	 *
	 * @param param
	 *            Information about the method call.
	 * @throws Throwable
	 *             Everything the callback throws is caught and logged.
	 */
	protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	}

	/**
	 * Wraps information about the method call and allows to influence it.
	 */
	public static final class MethodHookParam extends XCallback.Param {
		/** @hide */
		@SuppressWarnings("deprecation")
		public MethodHookParam() {
			super();
		}

		/** The hooked method/constructor. */
		public Member method;

		/**
		 * The {@code this} reference for an instance method, or {@code null}
		 * for static methods.
		 */
		public Object thisObject;

		/** Arguments to the method call. */
		public Object[] args;

		// private final andhook.lib.xposed.XC_MethodHook.MethodHookParam
		// ak_param;
		private Object result = null;
		private Throwable throwable = null;
		/* package */boolean returnEarly = false;

		/** Returns the result of the method call. */
		public Object getResult() {
			return z_ak_param().getResult();
		}

		/**
		 * Modify the result of the method call.
		 *
		 * <p>
		 * If called from {@link #beforeHookedMethod}, it prevents the call to
		 * the original method.
		 */
		public void setResult(Object result) {
			z_ak_param().setResult(result);
		}

		/** Returns the {@link Throwable} thrown by the method, or {@code null}. */
		public Throwable getThrowable() {
			return z_ak_param().getThrowable();
		}

		/** Returns true if an exception was thrown by the method. */
		public boolean hasThrowable() {
			return z_ak_param().hasThrowable();
		}

		/**
		 * Modify the exception thrown of the method call.
		 *
		 * <p>
		 * If called from {@link #beforeHookedMethod}, it prevents the call to
		 * the original method.
		 */
		public void setThrowable(Throwable throwable) {
			z_ak_param().setThrowable(throwable);
		}

		/**
		 * Returns the result of the method call, or throws the Throwable caused
		 * by it.
		 */
		public Object getResultOrThrowable() throws Throwable {
			return z_ak_param().getResultOrThrowable();
		}

		private andhook.lib.xposed.XC_MethodHook.MethodHookParam z_ak_param() {
			return (andhook.lib.xposed.XC_MethodHook.MethodHookParam) result;
		}

		MethodHookParam(
				final andhook.lib.xposed.XC_MethodHook.MethodHookParam param) {
			super();
			this.result = param;
			this.method = param.method;
			this.thisObject = param.thisObject;
			this.args = param.args;
		}

		void writeback() {
			final andhook.lib.xposed.XC_MethodHook.MethodHookParam ak_param = z_ak_param();
			ak_param.method = this.method;
			ak_param.thisObject = this.thisObject;
			ak_param.args = this.args;
			this.result = null;
		}
	}

	/**
	 * An object with which the method/constructor can be unhooked.
	 */
	public class Unhook implements IXUnhook<XC_MethodHook> {
		private final Member hookMethod;

		/* package */Unhook(Member hookMethod) {
			this.hookMethod = hookMethod;
		}

		/**
		 * Returns the method/constructor that has been hooked.
		 */
		public Member getHookedMethod() {
			return hookMethod;
		}

		@Override
		public XC_MethodHook getCallback() {
			return XC_MethodHook.this;
		}

		@SuppressWarnings("deprecation")
		@Override
		public void unhook() {
			XposedBridge.unhookMethod(hookMethod, XC_MethodHook.this);
		}
	}
}
