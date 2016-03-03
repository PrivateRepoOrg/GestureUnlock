package me.rijul.gestureunlock;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Created by rijul on 3/3/16.
 */
public class CustomShortcutActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, new CustomShortcutFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_add:
                ((CustomShortcutFragment) getFragmentManager().findFragmentById(R.id.fragment_container)).mProgressDialog.show();
                ((CustomShortcutFragment) getFragmentManager().findFragmentById(R.id.fragment_container)).
                        mPicker.pickShortcut(null, null, 0);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==RESULT_OK) {
            switch (requestCode) {
                case MainActivity.NEW_SHORTCUT:
                    Toast.makeText(CustomShortcutActivity.this, R.string.reboot_or_keyguard_restart, Toast.LENGTH_SHORT).show();
                    ((CustomShortcutFragment) getFragmentManager().findFragmentById(R.id.fragment_container)).loadShortcuts();
                    break;
                case ShortcutPickHelper.REQUEST_CREATE_SHORTCUT:
                case ShortcutPickHelper.REQUEST_PICK_APPLICATION:
                case ShortcutPickHelper.REQUEST_PICK_SHORTCUT:
                    ((CustomShortcutFragment) getFragmentManager().findFragmentById(R.id.fragment_container)).
                            mPicker.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
        else
            ((CustomShortcutFragment) getFragmentManager().findFragmentById(R.id.fragment_container)).mProgressDialog.dismiss();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_custom_shortcuts, menu);
        return true;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("requestCode", Integer.toString(requestCode));
        super.startActivityForResult(intent, requestCode);
    }
}
