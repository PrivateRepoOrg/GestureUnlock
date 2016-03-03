package me.rijul.gestureunlock;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Created by rijul on 3/3/16.
 */
public class AboutFragment extends PreferenceFragment {
    private static final String FACEBOOK_RIJUL = "rijul.ahuja";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (key.equals(Utils.ABOUT_DISCLAIMER)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.about_disclaimer);
            final TextView message = new TextView(getActivity());
            message.setText(R.string.about_disclaimer_message);
            message.setGravity(Gravity.CENTER);
            builder.setView(message);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });
            Dialog dialog = builder.create();
            dialog.show();
            return true;
        } else if (key.equals(Utils.ABOUT_RIJUL)) {
            openFacebook(FACEBOOK_RIJUL);
            return true;
        }
        return false;
    }

    private void openFacebook(String profile) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("fb://profile/" + profile)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/" + profile)));
        }
    }
}
