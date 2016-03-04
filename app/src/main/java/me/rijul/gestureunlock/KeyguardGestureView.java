package me.rijul.gestureunlock;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.Prediction;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by rijul on 2/3/16.
 */
public class KeyguardGestureView extends LinearLayout implements LockGestureView.OnLockGestureListener, KeyguardSecurityView, View.OnLongClickListener {
    private LockGestureView mLockGestureView;
    private int mTotalFailedPatternAttempts = 0;
    private int mFailedPatternAttemptsSinceLastTimeout = 0;
    private int mLongPress = 0;
    private Object mCallback;
    private TextView mTextView;
    private Button mEmergencyButton;
    private SettingsHelper mSettingsHelper;
    private String mKeyguardPackageName;
    private XC_MethodHook.MethodHookParam mParam;
    public Object mKeyguardUpdateMonitor;
    private Object mLockPatternUtils;
    private AppearAnimationUtils mAppearAnimationUtils;
    private DisappearAnimationUtils mDisappearAnimationUtils;
    private int mDisappearYTranslation;
    private GestureLibrary mGestureStore = GestureLibraries.fromFile(new File("/data/data/" + BuildConfig.APPLICATION_ID + "/files", "gu_gestures" ));

    private Runnable mCancelGestureRunnable = new Runnable() {
        public void run() {
            mLockGestureView.clearGesture();
            mLockGestureView.setGestureVisible(!mSettingsHelper.shouldHideGesture());
            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onSettingsReloaded();
        }
    };

    private Runnable mUnlockRunnable = new Runnable() {
        @Override
        public void run() {
            unlock();
        }
    };

    public KeyguardGestureView(Context context, XC_MethodHook.MethodHookParam param,
                               SettingsHelper settingsHelper, String keyguardPackageName) {
        super(context);
        setOrientation(VERTICAL);
        mParam = param;
        mKeyguardPackageName = keyguardPackageName;
        mKeyguardUpdateMonitor = XposedHelpers.callStaticMethod(XposedHelpers.findClass(mKeyguardPackageName +
                ".KeyguardUpdateMonitor", mParam.thisObject.getClass().getClassLoader()), "getInstance", getContext());
        try {
            mLockPatternUtils = XposedHelpers.getObjectField(mParam.thisObject, "mLockPatternUtils");
        } catch (NoSuchFieldError e) {
            mLockPatternUtils = XposedHelpers.getObjectField(mParam.thisObject, "mLockUtils");
        }
        mAppearAnimationUtils = new AppearAnimationUtils(getContext());
        mDisappearAnimationUtils = new DisappearAnimationUtils(getContext(),
                125, 0.6f /* translationScale */,
                0.45f /* delayScale */, AnimationUtils.loadInterpolator(
                getContext(), android.R.interpolator.fast_out_linear_in));
        Resources res = Utils.getResourcesForPackage(getContext(), getContext().getPackageName());
        mDisappearYTranslation = res.getDimensionPixelSize(res.getIdentifier(
                "disappear_y_translation", "dimen", getContext().getPackageName()));
        setSettingsHelper(settingsHelper);
        mGestureStore.load();
    }

    private void setSettingsHelper(SettingsHelper settingsHelper) {
        mSettingsHelper = settingsHelper;
        setUpViews();
        onSettingsReloaded();
    }

    private void setUpViews() {
        removeAllViews();
        setUpTextView();
        setUpGestureView();
        setUpEmergencyButton();
    }

    private void setUpTextView() {
        if (mTextView==null)
            mTextView = new TextView(getContext());
        mTextView.setGravity(Gravity.CENTER);
        LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin += (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        layoutParams.bottomMargin += (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        mTextView.setLayoutParams(layoutParams);
        mTextView.setText(Utils.getString(getContext(), R.string.enter_gesture_begin));
        addView(mTextView);
    }

    private void setUpGestureView() {
        if (mLockGestureView==null)
            mLockGestureView = new LockGestureView(getContext());
        mLockGestureView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mLockGestureView.setOrientation(VERTICAL);
        mLockGestureView.setOnGestureListener(this);
        mLockGestureView.setOnLongClickListener(this);
        addView(mLockGestureView);
    }

    private void setUpEmergencyButton() {
        if (mEmergencyButton==null)
            mEmergencyButton = new Button(getContext());
        Resources res = Utils.getResourcesForPackage(getContext(), getContext().getPackageName());
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        mEmergencyButton.setLayoutParams(llp);
        float textSize = res.getDimensionPixelSize(
                res.getIdentifier("kg_status_line_font_size", "dimen", getContext().getPackageName()));
        mEmergencyButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.textColorSecondary, outValue, true);
        int[] textSizeAttr = new int[] {android.R.attr.textColorSecondary};
        TypedArray a = getContext().obtainStyledAttributes(outValue.data, textSizeAttr);
        int textColor = a.getColor(0, -1);
        a.recycle();
        mEmergencyButton.setTextColor(textColor);
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        mEmergencyButton.setBackgroundResource(outValue.resourceId);
        mEmergencyButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeEmergencyCallAction();
            }
        });
        addView(mEmergencyButton);
    }

    public void setKeyguardCallback(Object paramKeyguardSecurityCallback) {
        mCallback = paramKeyguardSecurityCallback;
    }

    public void takeEmergencyCallAction() {
        if (mLockPatternUtils==null)
            mLockPatternUtils = XposedHelpers.getObjectField(mParam.thisObject, "mLockPatternUtils");
        XposedHelpers.callMethod(mCallback, "userActivity");
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            if (isInCall()) {
                resumeCall();
            }
            else {
                XposedHelpers.callMethod(mKeyguardUpdateMonitor, "reportEmergencyCallAction", true);

                XposedHelpers.callMethod(getContext(), "startActivityAsUser", new Intent()
                                .setAction("com.android.phone.EmergencyDialer.DIAL")
                                .setPackage("com.android.phone")
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                        XposedHelpers.newInstance(
                                XposedHelpers.findClass("android.os.UserHandle", mParam.thisObject.getClass().getClassLoader()),
                                (int) XposedHelpers.callMethod(mKeyguardUpdateMonitor, "getCurrentUser")));
            }
        }
        else {
            if ((boolean) XposedHelpers.callMethod(mLockPatternUtils, "isInCall")) {
                XposedHelpers.callMethod(mLockPatternUtils, "resumeCall");
            } else {
                XposedHelpers.callMethod(mKeyguardUpdateMonitor, "reportEmergencyCallAction", true);
                Intent intent = new Intent("com.android.phone.EmergencyDialer.DIAL");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                XposedHelpers.callMethod(getContext(), "startActivityAsUser", intent,
                        XposedHelpers.newInstance(
                                XposedHelpers.findClass("android.os.UserHandle", mParam.thisObject.getClass().getClassLoader()),
                                (int) XposedHelpers.callMethod(mLockPatternUtils, "getCurrentUser")));
            }
        }
    }

    private void resumeCall() {
        getTelecommManager().showInCallScreen(false);
    }

    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);
    }

    public void updateEmergencyCallButton(int phoneState) {
        if (mEmergencyButton==null)
            return;
        if (mSettingsHelper.hideEmergencyButton())
            return;
        boolean enabled = false;
        if ((boolean) XposedHelpers.callMethod(mLockPatternUtils, "isInCall")) {
            enabled = true; // always show "return to call" if phone is off-hook
        } else if ((boolean) XposedHelpers.callMethod(mLockPatternUtils, "isEmergencyCallCapable")) {
            boolean simLocked;
            try {
                simLocked = ((boolean) XposedHelpers.callMethod(mKeyguardUpdateMonitor, "isSimLocked"));
            } catch (NoSuchFieldError e) {
                simLocked = ((boolean) XposedHelpers.callMethod(mKeyguardUpdateMonitor, "isSimPinVoiceSecure"));
            }
            if (simLocked) {
                // Some countries can't handle emergency calls while SIM is locked.
                enabled = (boolean) XposedHelpers.callMethod(mLockPatternUtils, "isEmergencyCallEnabledWhileSimLocked");
            } else {
                // True if we need to show a secure screen (pin/pattern/SIM pin/SIM puk);
                // hides emergency button on "Slide" screen if device is not secure.
                enabled = (boolean) XposedHelpers.callMethod(mLockPatternUtils, "isSecure") ||
                        getContext().getResources().getBoolean(getContext().getResources().
                                getIdentifier("config_showEmergencyButton", "bool", getContext().getPackageName()));
            }
        }
        if (getContext().getResources().getBoolean(getContext().getResources().
                getIdentifier("icccardexist_hide_emergencybutton", "bool", getContext().getPackageName()))) {
            enabled = false;
        }
        XposedHelpers.callMethod(mLockPatternUtils, "updateEmergencyCallButtonState", mEmergencyButton, enabled, false);
        if (mSettingsHelper.hideEmergencyText()) {
            mEmergencyButton.setText("");
        }
    }

    public void updateEmergencyCallButton() {
        if (mEmergencyButton==null)
            return;
        if (mSettingsHelper.hideEmergencyButton())
            return;
        mEmergencyButton.setVisibility(View.VISIBLE);
        if (mSettingsHelper.hideEmergencyText()) {
            mEmergencyButton.setText("");
            return;
        }
        Resources res2 = Resources.getSystem();
        boolean visible = false;
        if (res2.getBoolean(res2.getIdentifier("config_voice_capable", "bool", "android"))) {
            if (isInCall())
                visible = true;
            else {
                final boolean simLocked = (boolean) XposedHelpers.callMethod(mKeyguardUpdateMonitor, "isSimPinVoiceSecure");
                visible = simLocked ? res2.getBoolean(res2.getIdentifier("config_voice_capable", "bool", "android")) : true;
            }
        }
        if (visible) {
            mEmergencyButton.setVisibility(View.VISIBLE);
            int id;
            if (isInCall())
                id = res2.getIdentifier("lockscreen_return_to_call", "string", "android");
            else
                id = res2.getIdentifier("lockscreen_emergency_call", "string", "android");
            mEmergencyButton.setText(res2.getString(id));
        }
        else
            mEmergencyButton.setVisibility(View.GONE);
    }

    public void onSettingsReloaded() {
        mSettingsHelper.reloadSettings();
        mGestureStore.load();
        if (mTextView!=null)
            mTextView.setVisibility(mSettingsHelper.shouldShowText() ? View.VISIBLE : View.GONE);
        if (mEmergencyButton!=null)
            mEmergencyButton.setVisibility(mSettingsHelper.hideEmergencyButton() ? View.GONE : View.VISIBLE);
        if (mLockGestureView!=null) {
            mLockGestureView.setGestureVisible(!mSettingsHelper.shouldHideGesture());
            if (mSettingsHelper.showGestureBackground())
                mLockGestureView.setBackgroundColor(0x4C000000);
            mLockGestureView.updateColors(mSettingsHelper);
        }
        invalidate();
    }

    @Override
    public void onGestureStart() {
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
        XposedHelpers.callMethod(mCallback, "userActivity");
        mLockGestureView.removeCallbacks(mCancelGestureRunnable);
    }

    @Override
    public void onGestureCleared() {

    }

    @Override
    public void onGestureDetected(Gesture gesture) {
        if (gesture.getLength()<MainFragment.LENGTH_THRESHOLD) {
            mLockGestureView.clearGesture();
            return;
        }
        mLongPress = 0;
        XposedHelpers.callMethod(mCallback, "userActivity");
        mTextView.setText("");
        String name = returnGestureName(gesture);
        if (name==null) {
            mLockGestureView.setGestureVisible(mSettingsHelper.showGestureError());
            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Wrong);
            mLockGestureView.postDelayed(mCancelGestureRunnable, mSettingsHelper.showGestureError() ? 300 : 0);
            mTotalFailedPatternAttempts++;
            mFailedPatternAttemptsSinceLastTimeout++;
            reportFailedUnlockAttempt();
            if (mFailedPatternAttemptsSinceLastTimeout >= 5) {
                handleAttemptLockout(setLockoutAttemptDeadline());
            }
            mTextView.setText(Utils.getString(getContext(), R.string.incorrect_gesture));
        } else if (name.equals("master password")) {
            mTextView.setText(Utils.getString(getContext(), R.string.correct_gesture));
            mLockGestureView.setGestureVisible(mSettingsHelper.showGestureCorrect());
            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Correct);
            mLockGestureView.postDelayed(mCancelGestureRunnable, mSettingsHelper.showGestureCorrect() ? 300 : 0);
            mLockGestureView.postDelayed(mUnlockRunnable, mSettingsHelper.showGestureCorrect() ? 300 : 0);
        } else {
            try {
                mTextView.setText(name.split("\\|")[1]);
                mLockGestureView.setGestureVisible(mSettingsHelper.showGestureCorrect());
                mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Correct);
                mLockGestureView.postDelayed(mCancelGestureRunnable, mSettingsHelper.showGestureCorrect() ? 300 : 0);
                Intent launchIntent = Intent.parseUri(name.split("\\|")[0], 0);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(launchIntent);
                mLockGestureView.postDelayed(mUnlockRunnable, mSettingsHelper.showGestureCorrect() ? 300 : 0);
            } catch (URISyntaxException e) {
                XposedBridge.log("[KnockCode] URI syntax invalid : " + name);
            } catch (ActivityNotFoundException e) {
                XposedBridge.log("[KnockCode] Activity not found : " + name);
            }
        }
    }

    private void handleAttemptLockout(long paramLong) {
        long l = SystemClock.elapsedRealtime();
        onAttemptLockoutStart();
        CountDownTimer mCountdownTimer = new CountDownTimer(paramLong - l, 1000L) {
            public void onFinish() {
                onAttemptLockoutEnd();
            }

            public void onTick(long millisUntilFinished) {
                int secs = (int) (millisUntilFinished / 1000L);
                mTextView.setText(getEnablingInSecs(secs));
            }
        }
                .start();
    }

    private String getEnablingInSecs(int secs) {
        return Utils.getString(getContext(), R.string.device_disabled, secs);
    }

    protected void onAttemptLockoutEnd() {
        mFailedPatternAttemptsSinceLastTimeout = 0;
        mLockGestureView.setEnabled(true);
        mTextView.setText("");
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
    }

    protected void onAttemptLockoutStart() {
        mLockGestureView.setEnabled(false);
    }

    private long setLockoutAttemptDeadline() {
        if (mLockPatternUtils==null)
            mLockPatternUtils = XposedHelpers.getObjectField(mParam.thisObject, "mLockPatternUtils");

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            return (Long) XposedHelpers.callMethod(mLockPatternUtils, "setLockoutAttemptDeadline",
                    (int) XposedHelpers.callMethod(XposedHelpers.getObjectField(
                            mParam.thisObject, "mUpdateMonitor"), "getCurrentUser"), 30000);
        }
        else {
            return (Long) XposedHelpers.callMethod(mLockPatternUtils, "setLockoutAttemptDeadline");
        }
    }

    private void reportFailedUnlockAttempt() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            XposedHelpers.callMethod(mCallback, "reportUnlockAttempt", false,
                    (mFailedPatternAttemptsSinceLastTimeout >= 5) ? ((mSettingsHelper.shouldDisableDialog()) ? 0 : 30000) : 0);
        }
        else {
            XposedHelpers.callMethod(mCallback, "reportUnlockAttempt", false);
        }
    }

    private void unlock() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            XposedHelpers.callMethod(mCallback, "reportUnlockAttempt", true, 0);
        }
        else {
            XposedHelpers.callMethod(mCallback, "reportUnlockAttempt", true);
        }
        XposedHelpers.callMethod(mCallback, "dismiss", true);
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
    }

    private String returnGestureName(Gesture gesture) {
        float minScore = mSettingsHelper.getMinPredictionScore();
        List<Prediction> predictions = mGestureStore.recognize(gesture);
        for(Prediction prediction : predictions) {
            if (prediction.score > minScore) {
                if (prediction.name.contains("|"))
                    return prediction.name;
                else
                    return "master password";
            }
        }
        return null;
    }

    @Override
    public Object getCallback() {
        return mCallback;
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
        if (mFailedPatternAttemptsSinceLastTimeout>=5)
            return;
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
        mTextView.setText("");
        mLongPress = 0;
        try {
            getContext().unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException e) {}
    }

    @Override
    public void onResume(int paramInt) {
        if (mFailedPatternAttemptsSinceLastTimeout>=5)
            return;
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
        mTextView.setText("");
        mLongPress = 0;
        getContext().registerReceiver(mBroadcastReceiver, new IntentFilter(Utils.SETTINGS_CHANGED));
    }

    @Override
    public void reset() {
        mLockGestureView.clearGesture();
    }

    @Override
    public void setLockPatternUtils(Object paramLockPatternUtils) {
        mLockPatternUtils = paramLockPatternUtils;
    }

    @Override
    public void showUsabilityHint() {

    }

    @Override
    public void startAppearAnimation() {
        enableClipping(false);
        setAlpha(1f);
        setTranslationY(mAppearAnimationUtils.getStartTranslation());
        animate()
                .setDuration(500)
                .setInterpolator(mAppearAnimationUtils.getInterpolator())
                .translationY(0);
        mAppearAnimationUtils.startAnimation(new View[]{mTextView, mLockGestureView, mEmergencyButton},
                new Runnable() {
                    @Override
                    public void run() {
                        enableClipping(true);
                    }
                });
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        enableClipping(false);
        animate()
                .alpha(0f)
                .translationY(mDisappearYTranslation)
                .setInterpolator(mDisappearAnimationUtils.getInterpolator())
                .setDuration(100)
                .withEndAction(finishRunnable);
        return true;
    }

    private void enableClipping(boolean enable) {
        setClipToPadding(enable);
        setClipChildren(enable);
    }

    @Override
    public void showBouncer(int duration) {

    }

    @Override
    public void hideBouncer(int duration) {

    }

    @Override
    public boolean onLongClick(View v) {
        XposedHelpers.callMethod(mCallback, "userActivity");
        ++mLongPress;
        if (mLongPress>1)
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        if (mLongPress==3)
            if (mSettingsHelper.failSafe()) {
                Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".KILL");
                getContext().sendBroadcast(intent);
                getContext().registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        System.exit(0);
                    }
                }, new IntentFilter(BuildConfig.APPLICATION_ID + ".DEAD"));
            }
        return true;
    }
}
