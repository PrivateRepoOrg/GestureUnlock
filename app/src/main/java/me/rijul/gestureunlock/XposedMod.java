package me.rijul.gestureunlock;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XModuleResources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ViewFlipper;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    public static final int RESET_WAIT_DURATION_CORRECT = 50;
    public static final int RESET_WAIT_DURATION_WRONG = 500;

    private XC_MethodHook mUpdateSecurityViewHook;
    private XC_MethodHook mShowSecurityScreenHook;
    private XC_MethodHook mStartAppearAnimHook;
    private XC_MethodHook mStartDisAppearAnimHook;
    private XC_MethodHook mOnPauseHook;
    private XC_MethodHook mOnSimStateChangedHook;
    private XC_MethodHook mOnPhoneStateChangedHook;
    private XC_MethodHook mShowTimeoutDialogHook;
    protected static KeyguardGestureView mKeyguardGestureView;
    private static SettingsHelper mSettingsHelper;
    private XC_MethodHook mOnResumeHook;
    private XC_MethodHook mOnScreenTurnedOnHook;
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mSettingsHelper!=null)
                mSettingsHelper.reloadSettings();
        }
    };
    private String modulePath;


    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        mSettingsHelper = new SettingsHelper();
        mSettingsHelper.reloadSettings();
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if ((resParam.packageName.contains("android.keyguard")) || (resParam.packageName.contains("com.android.systemui"))) {
            XModuleResources modRes = XModuleResources.createInstance(modulePath, resParam.res);
            if (mSettingsHelper.gestureFullscreen()) {
                XposedBridge.log("[GestureUnlock] Setting resources to fullscreen!");
                resParam.res.setReplacement(resParam.packageName, "dimen", "keyguard_security_height", modRes.fwd(R.dimen.replace_keyguard_security_max_height));
                resParam.res.setReplacement(resParam.packageName, "dimen", "keyguard_security_view_margin", modRes.fwd(R.dimen.replace_keyguard_security_view_margin));
                resParam.res.setReplacement(resParam.packageName, "dimen", "keyguard_security_width", modRes.fwd(R.dimen.replace_keyguard_security_max_height));
            }
        }
    }


    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedHelpers.setStaticBooleanField(XposedHelpers.findClass(BuildConfig.APPLICATION_ID + ".SettingsActivity", lpparam.classLoader),
                    "MODULE_INACTIVE", false);
        } else if (lpparam.packageName.equals("com.htc.lockscreen")) {
            createHooksIfNeeded("com.htc.lockscreen.keyguard");
            hookMethods("com.htc.lockscreen.keyguard", lpparam);
        } else if ((lpparam.packageName.contains("android.keyguard")) || (lpparam.packageName.contains("com.android.systemui"))) {
            XposedBridge.log("[GestureUnlock] Found keyguard for AOSPish devices");
            createHooksIfNeeded("com.android.keyguard");
            hookMethods("com.android.keyguard", lpparam);
        }
    }

    private void hookMethods(String packageName, LoadPackageParam lpparam) {
        Class <?> KeyguardHostView = XposedHelpers.findClass(packageName + ".KeyguardSecurityContainer", lpparam.classLoader);
        findAndHookMethod(KeyguardHostView, "startAppearAnimation", mStartAppearAnimHook);
        findAndHookMethod(KeyguardHostView, "startDisappearAnimation", Runnable.class, mStartDisAppearAnimHook);
        findAndHookMethod(KeyguardHostView, "onPause", mOnPauseHook);
        findAndHookMethod(KeyguardHostView, "onResume", int.class, mOnResumeHook);
        Class<?> KeyguardUpdateMonitorCallback = XposedHelpers.findClass(packageName + ".KeyguardUpdateMonitorCallback",
                lpparam.classLoader);
        if (packageName.equals("com.htc.lockscreen.keyguard")) {
            XposedBridge.log("[GestureUnlock] HTC Device");
            findAndHookMethod(KeyguardHostView, "showSecurityScreen", "com.htc.lockscreen.keyguard.KeyguardSecurityModel$SecurityMode",
                    mShowSecurityScreenHook);
        }
        else {
            findAndHookMethod(KeyguardHostView, "showSecurityScreen", "com.android.keyguard.KeyguardSecurityModel$SecurityMode",
                    mShowSecurityScreenHook);
        }
        XposedBridge.hookAllMethods(KeyguardUpdateMonitorCallback, "onSimStateChanged", mOnSimStateChangedHook);
        findAndHookMethod(KeyguardUpdateMonitorCallback, "onPhoneStateChanged", int.class, mOnPhoneStateChangedHook);

        //marshmallow vs lollipop
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            findAndHookMethod(KeyguardHostView, "showTimeoutDialog", int.class, mShowTimeoutDialogHook);
            findAndHookMethod(KeyguardHostView, "updateSecurityView", View.class, mUpdateSecurityViewHook);
        }
        else {
            findAndHookMethod(KeyguardHostView, "showTimeoutDialog", mShowTimeoutDialogHook);
            findAndHookMethod(KeyguardHostView, "updateSecurityView", View.class, boolean.class, mUpdateSecurityViewHook);
        }
        try  {
            Class<?> keyguardViewManager = XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager",
                    lpparam.classLoader);
            XposedBridge.hookAllMethods(keyguardViewManager, "onScreenTurnedOn", mOnScreenTurnedOnHook);
        } catch (NoSuchMethodError e) {
            XposedBridge.log(e);
        }
    }

    private void createHooksIfNeeded(final String keyguardPackageName) {

        mOnSimStateChangedHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (mKeyguardGestureView != null) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                        mKeyguardGestureView.updateEmergencyCallButton();
                    }
                    else {
                        mKeyguardGestureView.updateEmergencyCallButton(0);
                    }
                    param.setResult(null);
                }
            }
        };

        mShowTimeoutDialogHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if ((mKeyguardGestureView != null) && (mSettingsHelper != null)) {
                    if (mSettingsHelper.shouldDisableDialog())
                        param.setResult(null);
                }
            }
        };

        mOnPhoneStateChangedHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (mKeyguardGestureView!=null) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                        mKeyguardGestureView.updateEmergencyCallButton();
                    } else {
                        mKeyguardGestureView.updateEmergencyCallButton((int) param.args[0]);
                        param.setResult(null);
                    }
                }
            }
        };

        mUpdateSecurityViewHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                View view = (View) param.args[0];
                if (view instanceof KeyguardGestureView) {
                    KeyguardGestureView unlockView = (KeyguardGestureView) view;
                    unlockView.setKeyguardCallback(XposedHelpers.getObjectField(param.thisObject, "mCallback"));
                    unlockView.setLockPatternUtils(XposedHelpers.getObjectField(param.thisObject, "mLockPatternUtils"));
                    try {
                        Boolean isBouncing = (Boolean) param.args[1];
                        if (isBouncing)
                            unlockView.showBouncer(0);
                        else
                            unlockView.hideBouncer(0);
                        XposedHelpers.setObjectField(param.thisObject, "mIsBouncing", isBouncing);
                    }
                    catch (Exception ignored) {
                    }
                    param.setResult(null);
                }
            }
        };

        mShowSecurityScreenHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                Context mContext = ((FrameLayout) param.thisObject).getContext();
                mContext.registerReceiver(broadcastReceiver, new IntentFilter(Utils.SETTINGS_CHANGED));
                if (mSettingsHelper.isDisabled() || mSettingsHelper == null) {
                    //find whichever view is enabled
                    View pinView = (View) callMethod(param.thisObject, "getSecurityView", param.args[0]);
                    ViewGroup.LayoutParams layoutParams = pinView.getLayoutParams();
                    //set width and height
                    layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, mContext.getResources().getDisplayMetrics());
                    layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, mContext.getResources().getDisplayMetrics());
                    pinView.setLayoutParams(layoutParams);
                    mKeyguardGestureView = null;
                    return;
                }
                Object securityMode = param.args[0];
                Class<?> SecurityMode = XposedHelpers.findClass(keyguardPackageName + ".KeyguardSecurityModel$SecurityMode",
                        param.thisObject.getClass().getClassLoader());
                Object patternMode = XposedHelpers.getStaticObjectField(SecurityMode, "Pattern");
                if (!patternMode.equals(securityMode)) {
                    //find whichever view is enabled
                    View pinView = (View) callMethod(param.thisObject, "getSecurityView", param.args[0]);
                    ViewGroup.LayoutParams layoutParams = pinView.getLayoutParams();
                    //set width and height
                    layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, mContext.getResources().getDisplayMetrics());
                    layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, mContext.getResources().getDisplayMetrics());
                    pinView.setLayoutParams(layoutParams);
                    mKeyguardGestureView = null;
                    return;
                }
                Object mCurrentSecuritySelection = XposedHelpers.getObjectField(param.thisObject, "mCurrentSecuritySelection");
                if (securityMode == mCurrentSecuritySelection) {
                    param.setResult(null);
                    return;
                }
                View oldView = (LinearLayout) callMethod(param.thisObject, "getSecurityView", mCurrentSecuritySelection);
                mKeyguardGestureView = new KeyguardGestureView(mContext,param,mSettingsHelper,keyguardPackageName);
                View newView = mKeyguardGestureView;

                FrameLayout layout = (FrameLayout) param.thisObject;
                int disableSearch = XposedHelpers.getStaticIntField(View.class, "STATUS_BAR_DISABLE_SEARCH");
                layout.setSystemUiVisibility((layout.getSystemUiVisibility() & ~disableSearch));

                // pause old view, and ignore requests from it
                if (oldView != null) {
                    Object mNullCallback = getObjectField(param.thisObject, "mNullCallback");
                    callMethod(oldView, "onPause");
                    callMethod(oldView, "setKeyguardCallback", mNullCallback);
                }

                View pinView = (View) callMethod(param.thisObject, "getSecurityView", patternMode);
                ViewGroup.LayoutParams layoutParams = pinView.getLayoutParams();
                if (mSettingsHelper.gestureFullscreen())  {
                    XposedBridge.log("[GestureUnlock] Setting view to fullscreen!");
                    Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    Point size = new Point();
                    display.getSize(size);
                    int rotation = display.getRotation();
                    if (rotation==Surface.ROTATION_0 || rotation== Surface.ROTATION_180) {
                        layoutParams.height = size.y;
                        layoutParams.width = size.x;
                    } else {
                        layoutParams.height = size.x;
                        layoutParams.width = size.y;
                    }
                } else {
                    layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, mContext.getResources().getDisplayMetrics());
                    layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, mContext.getResources().getDisplayMetrics());
                }
                newView.setLayoutParams(layoutParams);

                //show new view, and set a callback for it
                Object mCallback = getObjectField(param.thisObject, "mCallback");
                //callMethod(newView, "onResume", KeyguardSecurityView.VIEW_REVEALED);
                //callMethod(newView, "setKeyguardCallback", mCallback);
                mKeyguardGestureView.onResume(KeyguardSecurityView.VIEW_REVEALED);
                mKeyguardGestureView.setKeyguardCallback(mCallback);

                // add the view to the viewflipper and show it
                ViewFlipper mSecurityViewContainer = (ViewFlipper) getObjectField(param.thisObject, "mSecurityViewFlipper");
                mSecurityViewContainer.addView(newView);
                final int childCount = mSecurityViewContainer.getChildCount();

                for (int i = 0; i < childCount; i++) {
                    if (mSecurityViewContainer.getChildAt(i) instanceof KeyguardGestureView) {
                        mSecurityViewContainer.setDisplayedChild(i);
                        mSecurityViewContainer.getChildAt(i).requestFocus();
                        break;
                    }
                }

                // set that knock code is currently selected
                XposedHelpers.setObjectField(param.thisObject, "mCurrentSecuritySelection", securityMode);
                param.setResult(null);
            }
        };

        mStartAppearAnimHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (isPattern(keyguardPackageName,param))
                    if (mKeyguardGestureView!=null) {
                        mKeyguardGestureView.startAppearAnimation();
                        param.setResult(null);
                    }
            }
        };

        mStartDisAppearAnimHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (isPattern(keyguardPackageName,param))
                    if (mKeyguardGestureView!=null) {
                        param.setResult(mKeyguardGestureView.startDisappearAnimation((Runnable) param.args[0]));
                    }
            }
        };

        mOnPauseHook= new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (isPattern(keyguardPackageName,param))
                    if (mKeyguardGestureView!=null) {
                        mKeyguardGestureView.onPause();
                        param.setResult(null);
                    }
            }
        };

        mOnResumeHook= new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (mSettingsHelper.isDisabled())
                    return;
                if (isPattern(keyguardPackageName,param))
                    if (mKeyguardGestureView!=null) {
                        mKeyguardGestureView.onResume((int) param.args[0]);
                        param.setResult(null);
                    }
            }
        };

        mOnScreenTurnedOnHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if ((mSettingsHelper.isDisabled()) || (!mSettingsHelper.directlyShowGestureEntry()))
                    return;
                if (mKeyguardGestureView==null)
                    return;
                if (mKeyguardGestureView.isInCall())
                    return;
                XposedHelpers.callMethod(param.thisObject, "showBouncer");
            }
        };
    }

    private boolean isPattern(String keyguardPackageName, XC_MethodHook.MethodHookParam param) {
        Class<?> SecurityMode = XposedHelpers.findClass(keyguardPackageName + ".KeyguardSecurityModel$SecurityMode",
                param.thisObject.getClass().getClassLoader());
        Object patternMode = XposedHelpers.getStaticObjectField(SecurityMode, "Pattern");
        return  (patternMode.equals(XposedHelpers.getObjectField(param.thisObject, "mCurrentSecuritySelection")));
    }
}

