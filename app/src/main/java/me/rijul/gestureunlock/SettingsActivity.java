package me.rijul.gestureunlock;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * Created by rijul on 2/3/16.
 */
public class SettingsActivity extends Activity {
    private static boolean MODULE_INACTIVE = true;
    BroadcastReceiver deadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidateOptionsMenu();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, new SettingsFragment()).commit();
        if (MODULE_INACTIVE) {
            View moduleActive = findViewById(R.id.module_inactive);
            moduleActive.setVisibility(View.VISIBLE);
            moduleActive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle(R.string.module_inactive_title)
                            .setMessage(R.string.module_inactive_message)
                            .create()
                            .show();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        Switch masterSwitch = ((Switch) menu.findItem(R.id.toolbar_master_switch).getActionView());
        masterSwitch.setChecked(!new SettingsHelper(SettingsActivity.this).isSwitchOff());
        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                new SettingsHelper(SettingsActivity.this).putBoolean("switch", isChecked);
            }
        });
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(deadReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(deadReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".DEAD"));
        try {
            if (android.provider.Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCK_PATTERN_ENABLED) == 0) {
                View patternActive = findViewById(R.id.pattern_inactive);
                patternActive.setVisibility(View.VISIBLE);
                patternActive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent("android.app.action.SET_NEW_PASSWORD");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                });
            } else
                findViewById(R.id.pattern_inactive).setVisibility(View.GONE);
        } catch (Settings.SettingNotFoundException e) {}
    }
}
