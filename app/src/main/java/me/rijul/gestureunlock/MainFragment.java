package me.rijul.gestureunlock;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.Prediction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by rijul on 2/3/16.
 */
@SuppressWarnings("ConstantConditions")
public class MainFragment extends Fragment implements LockGestureView.OnLockGestureListener, View.OnClickListener {

    public static final float LENGTH_THRESHOLD = 60.0f;
    private int mRequestCode = -1;
    private LockGestureView mLockGestureView;
    private Runnable mFinishRunnable, mClearGestureRunnable;
    private boolean mConfirmationMode = false;
    private Gesture mChosenGesture;
    private GestureLibrary mGestureStore;
    private File mGestureStoreFile;
    private String mUri = null, mName = null;

    @Override
    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_gesture, container, false);
        mLockGestureView = (LockGestureView) rootView.findViewById(R.id.gestures_overlay);
        mLockGestureView.setOnGestureListener(this);
        mFinishRunnable = new Runnable() {
            @Override
            public void run() {
                mGestureStore.save();
                mGestureStoreFile.setReadable(true, false);
                if (mRequestCode==-1)
                    startActivity(new Intent(getActivity(), SettingsActivity.class));
                else
                    getActivity().setResult(MainActivity.RESULT_OK, getActivity().getIntent());
                if ((mRequestCode==MainActivity.CHANGE_GESTURE) && (!new SettingsHelper(getActivity()).isSwitchOff()))
                    Utils.restartKeyguard(getActivity());
                getActivity().finish();
            }
        };
        mClearGestureRunnable = new Runnable() {
            @Override
            public void run() {
                mLockGestureView.clearGesture();
            }
        };
        if (new SettingsHelper(getActivity()).isVirgin()) {
            try {
                FileOutputStream fos = getActivity().openFileOutput("gu_gestures", Context.MODE_WORLD_WRITEABLE);
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mGestureStoreFile = new File("/data/data/" + BuildConfig.APPLICATION_ID + "/files", "gu_gestures");
        mGestureStoreFile.setReadable(true, false);
        mGestureStore = GestureLibraries.fromFile(mGestureStoreFile);
        mGestureStore.load();
        rootView.findViewById(R.id.next_button).setOnClickListener(this);
        rootView.findViewById(R.id.retry_button).setOnClickListener(this);
        rootView.findViewById(R.id.retry_button).setEnabled(false);
        try {
            mRequestCode = Integer.parseInt(getArguments().getString("requestCode"));
        } catch (NullPointerException e) {
            mRequestCode = -1;
        }
        //Activity or Fragment is started to login into the app
        if (mRequestCode==-1) {
            ((TextView) rootView.findViewById(android.R.id.hint)).setText(R.string.enter_gesture_begin);
            rootView.findViewById(R.id.bottom_buttons).setVisibility(View.GONE);
        } else {
            ActionBar actionBar = getActivity().getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (mRequestCode==MainActivity.CHANGE_GESTURE) {
                ((TextView) rootView.findViewById(android.R.id.hint)).setText(R.string.enter_new_gesture);
            } else {
                Bundle bundle = getArguments();
                mUri = bundle.getString("uri");
                mName = bundle.getString("name");
                ((TextView) rootView.findViewById(android.R.id.hint)).setText(mName);
            }

        }
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.retry_button:
                reset();
                break;
            case R.id.next_button:
                if (mChosenGesture==null)
                    return;
                if (!mConfirmationMode) {
                    if (mRequestCode==MainActivity.NEW_SHORTCUT) {
                        String friendlyName = shortcutAlreadyExists(mChosenGesture);
                        if (friendlyName!=null) {
                            Toast.makeText(getActivity(), getString(R.string.gesture_conflict, friendlyName), Toast.LENGTH_SHORT).show();
                            reset();
                        } else {
                            mConfirmationMode = true;
                            String gestureName = mUri + "|" + mName;
                            if (mGestureStore.getGestures(gestureName)!=null)
                                mGestureStore.removeEntry(gestureName);
                            mGestureStore.addGesture(gestureName, mChosenGesture);
                            getView().findViewById(R.id.retry_button).setEnabled(true);
                            getView().findViewById(R.id.next_button).setEnabled(false);
                            ((TextView) getView().findViewById(android.R.id.hint)).setText(getString(R.string.confirm_shortcut, mName));
                            mLockGestureView.clearGesture();
                            mChosenGesture = null;
                        }
                    } else if (mRequestCode==MainActivity.CHANGE_GESTURE) {
                        String friendlyName = shortcutAlreadyExists(mChosenGesture);
                        if ((friendlyName!=null) && (!friendlyName.equals("master password"))) {
                            Toast.makeText(getActivity(), getString(R.string.gesture_conflict, friendlyName), Toast.LENGTH_SHORT).show();
                            reset();
                        } else {
                            if (mGestureStore.getGestures("master") != null)
                                mGestureStore.removeEntry("master");
                            mGestureStore.addGesture("master", mChosenGesture);
                            mConfirmationMode = true;
                            getView().findViewById(R.id.retry_button).setEnabled(true);
                            getView().findViewById(R.id.next_button).setEnabled(false);
                            ((TextView) getView().findViewById(android.R.id.hint)).setText(R.string.confirm_gesture);
                            mLockGestureView.clearGesture();
                            mChosenGesture = null;
                            if (!new SettingsHelper(getActivity()).isSwitchOff())
                                Toast.makeText(getActivity(), R.string.keyguard_autorestart, Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    if (mRequestCode==MainActivity.CHANGE_GESTURE) {
                        if (gestureMatch(mChosenGesture, "master")) {
                            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Correct);
                            mLockGestureView.postDelayed(mFinishRunnable, 300);
                            (new SettingsHelper(getActivity())).setNotVirgin();
                            mGestureStore.addGesture("master", mChosenGesture);
                        } else {
                            getView().findViewById(R.id.next_button).setEnabled(false);
                            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Wrong);
                            mChosenGesture = null;
                            mLockGestureView.postDelayed(mClearGestureRunnable, 300);
                        }
                    } else {
                        if (gestureMatch(mChosenGesture, mUri + "|" + mName)) {
                            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Correct);
                            mLockGestureView.postDelayed(mFinishRunnable, 300);
                            Toast.makeText(getActivity(), R.string.reboot_or_keyguard_restart, Toast.LENGTH_SHORT).show();
                        } else {
                            getView().findViewById(R.id.next_button).setEnabled(false);
                            mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Wrong);
                            mChosenGesture = null;
                            mLockGestureView.postDelayed(mClearGestureRunnable, 300);
                        }
                    }
                }
        }
    }

    private boolean gestureMatch(Gesture gesture, String gestureName) {
        ArrayList<Prediction> predictions = mGestureStore.recognize(gesture);
        if (predictions.size() > 0) {
            Prediction prediction = predictions.get(0);
            if (prediction.score > 2) {
                if (prediction.name.equals(gestureName)) {
                    Gesture foundGesture = mGestureStore.getGestures(gestureName).get(0);
                    if (foundGesture.getStrokesCount() == gesture.getStrokesCount())
                        return true;
                    else
                        return false;
                }
            }
        }
        return false;
    }

    private void reset() {
        if (mConfirmationMode) {
            //Second gesture entry is not done, so reset to first
            if (mChosenGesture==null) {
                mConfirmationMode = false;
                resetToFirst();
            }
            else if (mRequestCode==MainActivity.CHANGE_GESTURE)
                ((TextView) getView().findViewById(android.R.id.hint)).setText(R.string.confirm_gesture);
            else
                ((TextView) getView().findViewById(android.R.id.hint)).setText(getString(R.string.confirm_shortcut, mName));
        }
        else
            resetToFirst();
        mLockGestureView.clearGesture();
        getView().findViewById(R.id.next_button).setEnabled(false);
        mChosenGesture = null;
    }

    private void resetToFirst() {
        if (mRequestCode==MainActivity.CHANGE_GESTURE)
            ((TextView) getView().findViewById(android.R.id.hint)).setText(R.string.enter_new_gesture);
        else
            ((TextView) getView().findViewById(android.R.id.hint)).setText(mName);
        getView().findViewById(R.id.retry_button).setEnabled(false);
    }

    @Override
    public void onGestureStart() {
        mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Ready);
        mLockGestureView.removeCallbacks(mClearGestureRunnable);
        getView().findViewById(R.id.next_button).setEnabled(false);
        getView().findViewById(R.id.retry_button).setEnabled(false);
    }

    @Override
    public void onGestureCleared() {
        mLockGestureView.removeCallbacks(mClearGestureRunnable);
    }

    @Override
    public void onGestureDetected(Gesture gesture) {
        if (gesture.getLength()<LENGTH_THRESHOLD) {
            mLockGestureView.clearGesture();
            mChosenGesture = null;
            return;
        }
        mChosenGesture = gesture;
        getView().findViewById(R.id.next_button).setEnabled(true);
        getView().findViewById(R.id.retry_button).setEnabled(true);
        if (mRequestCode==-1) {
            if (gestureMatch(mChosenGesture, "master")) {
                mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Correct);
                mLockGestureView.postDelayed(mFinishRunnable, 300);
            } else {
                mLockGestureView.setDisplayMode(LockGestureView.DisplayMode.Wrong);
                mChosenGesture = null;
                mLockGestureView.postDelayed(mClearGestureRunnable, 300);
            }
        }
    }

    private String shortcutAlreadyExists(Gesture gesture) {
        List<Prediction> predictions = mGestureStore.recognize(gesture);
        for(Prediction prediction : predictions) {
            if (prediction.score>=2.0) {
                if (prediction.name.contains("|"))
                    return prediction.name.split("\\|")[1];
                else
                    return "master password";
            }
        }
        return null;
    }
}