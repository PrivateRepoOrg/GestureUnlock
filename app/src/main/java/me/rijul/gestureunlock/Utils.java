package me.rijul.gestureunlock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by rijul on 2/3/16.
 */
public class Utils {
    public static final String SETTINGS_CHANGE_GESTURE = "settings_change_gesture";
    public static final String SETTINGS_RESTART_KEYGUARD = "settings_restart_keyguard";
    public static final String SETTINGS_GESTURE_BACKGROUND = "settings_gesture_background";
    public static final String SETTINGS_GESTURE_BACKGROUND_COLOR = "settings_gesture_background_color";
    public static final String SETTINGS_GESTURE_VISIBLE = "settings_gesture_visible";
    public static final String SETTINGS_GESTURE_CORRECT = "settings_gesture_correct";
    public static final String SETTINGS_GESTURE_ERROR = "settings_gesture_error";
    public static final String SETTINGS_GESTURE_DIALOG = "settings_gesture_dialog";
    public static final String SETTINGS_GESTURE_TEXT = "settings_gesture_text";
    public static final String SETTINGS_EMERGENCY_BUTTON = "settings_emergency_button";
    public static final String SETTINGS_EMERGENCY_TEXT = "settings_emergency_text";
    public static final String SETTINGS_IS_VIRGIN = "settings_is_virgin";
    public static final String SETTINGS_FAILSAFE = "settings_failsafe";
    public static final String SETTINGS_HIDE_LAUNCHER = "settings_hide_launcher";
    public static final String SETTINGS_SWITCH = "switch";
    public static final String SETTINGS_CUSTOM_SHORTCUTS = "settings_custom_shortcuts";
    public static final String SETTINGS_GESTURE_DIRECT_ENTRY = "settings_gesture_direct_entry";
    public static final String SETTINGS_GESTURE_COLOR_READY = "settings_gesture_color_ready";
    public static final String SETTINGS_GESTURE_COLOR_CORRECT = "settings_gesture_color_correct";
    public static final String SETTINGS_GESTURE_COLOR_WRONG = "settings_gesture_color_wrong";
    public static final String SETTINGS_GESTURE_FULLSCREEN = "settings_gesture_fullscreen";
    public static final String SETTINGS_PREDICTION_SCORE = "settings_prediction_score";
    public static final String SETTINGS_CHANGED = BuildConfig.APPLICATION_ID + "_SETTINGS_CHANGED";

    public static final String ABOUT_DISCLAIMER = "about_disclaimer";

    private static class killPackage extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String packageToKill = params[0];
            Process su = null;
            try {
                su = Runtime.getRuntime().exec("su");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (su!= null) {
                try {
                    DataOutputStream os = new DataOutputStream(su.getOutputStream());
                    os.writeBytes("pkill -f " + packageToKill + "\n");
                    os.flush();
                    os.writeBytes("exit\n");
                    os.flush();
                    su.waitFor();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    public static void restartKeyguard(Context ctx) {
        PackageManager packageManager = ctx.getPackageManager();
        String packageToKill;
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo("com.htc.lockscreen", 0);
            packageToKill = "com.htc.lockscreen";
        } catch (PackageManager.NameNotFoundException e) {
            packageToKill = "com.android.systemui";
        }
        (new killPackage()).execute(packageToKill);
    }

    public static Resources getResourcesForPackage(Context context, String packageName) {
        try {
            context = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
            return context.getResources();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Utils.class.getName(), "", e);
        }
        return null;
    }

    public static String getString(Context context, int id) {
        return getOwnResources(context).getString(id);
    }

    public static String getString(Context context, int id, Object... args) {
        return getOwnResources(context).getString(id, args);
    }

    public static Resources getOwnResources(Context context) {
        return getResourcesForPackage(context, BuildConfig.APPLICATION_ID);
    }
}
