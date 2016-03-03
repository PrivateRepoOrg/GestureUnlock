package me.rijul.gestureunlock;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.Toast;

/**
 * Created by rijul on 2/3/16.
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        if (!new SettingsHelper(getActivity()).isVirgin())
            findPreference(Utils.SETTINGS_CHANGE_GESTURE).setTitle(R.string.change_gesture);
        else
            findPreference(Utils.SETTINGS_CUSTOM_SHORTCUTS).setEnabled(false);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (key.equals(Utils.SETTINGS_CHANGE_GESTURE)) {
            startActivityForResult(new Intent(getActivity(), MainActivity.class), MainActivity.CHANGE_GESTURE);
            return true;
        } else if (key.equals(Utils.SETTINGS_RESTART_KEYGUARD)) {
            Utils.restartKeyguard(getActivity());
            return true;
        }
        else if (key.equals(Utils.SETTINGS_HIDE_LAUNCHER)) {
            if (((CheckBoxPreference) preference).isChecked()) {
                ComponentName componentName = new ComponentName(getActivity(), "me.rijul.knockcode.MainActivity-Alias");
                getActivity().getPackageManager().setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } else {
                ComponentName componentName = new ComponentName(getActivity(), "me.rijul.knockcode.MainActivity-Alias");
                getActivity().getPackageManager().setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
            Toast.makeText(getActivity(), "A reboot or launcher restart may be required to apply the changes", Toast.LENGTH_SHORT).show();
            return true;
        } else if ((key.equals(Utils.SETTINGS_CUSTOM_SHORTCUTS) || (key.equals(Utils.SETTINGS_ABOUT))))
                return false;
        Toast.makeText(getActivity(), "A reboot or keyguard restart is required to apply the changes", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("requestCode", Integer.toString(requestCode));
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==MainActivity.RESULT_OK) {
            findPreference(Utils.SETTINGS_CUSTOM_SHORTCUTS).setEnabled(true);
            findPreference(Utils.SETTINGS_CHANGE_GESTURE).setTitle("Change your gesture");
            if (!new SettingsHelper(getActivity()).isSwitchOff())
                Toast.makeText(getActivity(), "A reboot or keyguard restart is required to apply the changes", Toast.LENGTH_SHORT).show();
        }
    }
}
