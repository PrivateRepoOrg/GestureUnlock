package me.rijul.gestureunlock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * Created by rijul on 2/3/16.
 */
public class MainActivity extends Activity {
    public static final int CHANGE_GESTURE = 1, NEW_SHORTCUT = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (getCallingActivity()==null) {
            if (new SettingsHelper(this).isVirgin()) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
            } else {
                getFragmentManager().beginTransaction().replace(R.id.fragment_container, new MainFragment()).commit();
            }
        } else {
            MainFragment mainFragment = new MainFragment();
            mainFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mainFragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getCallingActivity()==null) {
                    finish();
                }
                else {
                    Intent returnIntent = getIntent();
                    setResult(Activity.RESULT_CANCELED, returnIntent);
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
