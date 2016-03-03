package me.rijul.gestureunlock;

import java.util.Set;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

public class SettingsHelper {
    private XSharedPreferences mXPreferences = null;
    private SharedPreferences mPreferences = null;

    public SettingsHelper() {
        mXPreferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
        mXPreferences.makeWorldReadable();
        reloadSettings();
    }

    public SettingsHelper(Context context) {
        mPreferences = getWritablePreferences(context);
    }

    public SharedPreferences.Editor edit() {return mPreferences.edit();}

    public void reloadSettings() {
        mXPreferences.reload();
    }

    public void putBoolean(String key, boolean value) {
        edit().putBoolean(key, value).apply();
    }

    public void setNotVirgin() {putBoolean(Utils.SETTINGS_IS_VIRGIN, false);}

    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    public static SharedPreferences getWritablePreferences(Context context) {
        return context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences", Context.MODE_WORLD_READABLE);
    }

    private String getString(String key, String defaultValue) {
        String returnResult = defaultValue;
        if (mPreferences != null) {
            returnResult = mPreferences.getString(key, defaultValue);
        } else if (mXPreferences != null) {
            returnResult = mXPreferences.getString(key, defaultValue);
        }
        return returnResult;
    }

    public float getFloat(String key, float defaultValue) {
        float returnResult = defaultValue;
        if (mPreferences != null) {
            returnResult = mPreferences.getFloat(key, defaultValue);
        } else if (mXPreferences != null) {
            returnResult = mXPreferences.getFloat(key, defaultValue);
        }
        return returnResult;
    }

    public int getInt(String key, int defaultValue) {
        int returnResult = defaultValue;
        if (mPreferences != null) {
            returnResult = mPreferences.getInt(key, defaultValue);
        } else if (mXPreferences != null) {
            returnResult = mXPreferences.getInt(key, defaultValue);
        }
        return returnResult;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        boolean returnResult = defaultValue;
        if (mPreferences != null) {
            returnResult = mPreferences.getBoolean(key, defaultValue);
        } else if (mXPreferences != null) {
            returnResult = mXPreferences.getBoolean(key, defaultValue);
        }
        return returnResult;
    }

    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        Set<String> returnResult = defaultValue;
        if (mPreferences != null) {
            returnResult = mPreferences.getStringSet(key, defaultValue);
        } else if (mXPreferences != null) {
            returnResult = mXPreferences.getStringSet(key, defaultValue);
        }
        return returnResult;
    }

    public boolean isVirgin() {return getBoolean(Utils.SETTINGS_IS_VIRGIN, true); }

    public boolean hideEmergencyButton() {return !getBoolean(Utils.SETTINGS_EMERGENCY_BUTTON, true); }

    public boolean hideEmergencyText() {return !getBoolean(Utils.SETTINGS_EMERGENCY_TEXT, true); }

    public boolean shouldShowText() {return getBoolean(Utils.SETTINGS_GESTURE_TEXT, true); }

    public boolean shouldHideGesture() {return !getBoolean(Utils.SETTINGS_GESTURE_VISIBLE, true); }

    public boolean shouldDisableDialog() {return !getBoolean(Utils.SETTINGS_GESTURE_DIALOG, true); }

    public boolean failSafe() {return getBoolean(Utils.SETTINGS_FAILSAFE, true); }

    public boolean showGestureBackground() {return getBoolean(Utils.SETTINGS_GESTURE_BACKGROUND, true); }

    public boolean showGestureCorrect() {return getBoolean(Utils.SETTINGS_GESTURE_CORRECT, true); }

    public boolean showGestureError() {return getBoolean(Utils.SETTINGS_GESTURE_ERROR, true); }

    public boolean isDisabled() {
        return isVirgin() || isSwitchOff();
    }

    public boolean isSwitchOff() {return !getBoolean(Utils.SETTINGS_SWITCH, false); }
}
