package me.rijul.gestureunlock;

import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rijul on 3/3/16.
 */
public class CustomShortcutFragment extends ListFragment implements ShortcutPickHelper.OnPickListener {
    private static final int MENU_ID_DELETE = 1, MENU_ID_EDIT = 2;
    public ShortcutPickHelper mPicker;
    public ProgressDialog mProgressDialog;
    private GesturesLoadTask mTask;
    private final Comparator<NamedGesture> mSorter = new Comparator<NamedGesture>() {
        public int compare(NamedGesture object1, NamedGesture object2) {
            return object1.name.compareTo(object2.name);
        }
    };
    private File mGestureStoreFile = new File("/data/data/" + BuildConfig.APPLICATION_ID + "/files", "gu_gestures");
    private GestureLibrary mGestureStore = GestureLibraries.fromFile(mGestureStoreFile);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_list, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mPicker = new ShortcutPickHelper(getActivity(), this);
        getListView().setAdapter(new GestureAdapter(getActivity()));
        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getActivity().getString(R.string.loading));
        registerForContextMenu(getListView());
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.showContextMenu();
            }
        });
        loadShortcuts();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==MainActivity.RESULT_OK) {
            switch (requestCode) {
                case MainActivity.NEW_SHORTCUT:
                    mProgressDialog.dismiss();
                    loadShortcuts();
                    break;
                case ShortcutPickHelper.REQUEST_CREATE_SHORTCUT:
                case ShortcutPickHelper.REQUEST_PICK_APPLICATION:
                case ShortcutPickHelper.REQUEST_PICK_SHORTCUT:
                    mPicker.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
        else
            mProgressDialog.dismiss();
    }


    public void loadShortcuts() {
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            mTask.cancel(true);
        }
        mTask = (GesturesLoadTask) new GesturesLoadTask().execute();
    }

    @Override
    public void shortcutPicked(String uri, String friendlyName, boolean isApplication) {
        if (TextUtils.isEmpty(uri) || TextUtils.isEmpty(friendlyName)) {
            return;
        }
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.putExtra("uri", uri);
        intent.putExtra("name", friendlyName);
        startActivityForResult(intent, MainActivity.NEW_SHORTCUT);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("requestCode", Integer.toString(requestCode));
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.setHeaderTitle(((NamedGesture) info.targetView.getTag()).name);
        menu.add(0, MENU_ID_EDIT, 0, R.string.edit);
        menu.add(0, MENU_ID_DELETE, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)
                item.getMenuInfo();
        final NamedGesture shortcut = (NamedGesture) menuInfo.targetView.getTag();
        switch (item.getItemId()) {
            case MENU_ID_EDIT:
                editShortcut(shortcut);
                break;
            case MENU_ID_DELETE:
                deleteShortcut(shortcut);
                return true;
        }

        return super.onContextItemSelected(item);
    }

    private void editShortcut(NamedGesture gesture) {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.putExtra("uri", gesture.uri);
        intent.putExtra("name", gesture.name);
        startActivityForResult(intent, MainActivity.NEW_SHORTCUT);
    }

    private void deleteShortcut(NamedGesture namedGesture) {
        mGestureStore.load();
        mGestureStore.removeEntry(namedGesture.uri + "|" + namedGesture.name + "|");
        mGestureStore.save();
        getActivity().sendBroadcast(new Intent(Utils.SETTINGS_CHANGED));
        mGestureStoreFile.setReadable(true, false);
        GestureAdapter gestureAdapter = ((GestureAdapter) getListView().getAdapter());
        gestureAdapter.setNotifyOnChange(false);
        gestureAdapter.remove(namedGesture);
        gestureAdapter.sort(mSorter);
        gestureAdapter.notifyDataSetChanged();
        loadShortcuts();
    }


    private class NamedGesture {
        Gesture gesture;
        String name;
        String uri;
    }

    private class GestureAdapter extends ArrayAdapter<NamedGesture> {
        private final LayoutInflater mInflater;
        private final Map<Long, Drawable> mThumbnails = Collections.synchronizedMap(new HashMap<Long, Drawable>());

        public GestureAdapter(Context context) {
            super(context, 0);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void addBitmap(Long id, Bitmap bitmap) {
            mThumbnails.put(id, new BitmapDrawable(bitmap));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.shortcut_item, parent, false);
            }
            final NamedGesture namedGesture = getItem(position);
            final TextView label = (TextView) convertView;
            label.setTag(namedGesture);
            label.setText(namedGesture.name);
            label.setCompoundDrawablesWithIntrinsicBounds(mThumbnails.get(namedGesture.gesture.getID()),
                    null, null, null);
            return convertView;
        }
    }

    private class GesturesLoadTask extends AsyncTask<Void, NamedGesture, Integer> {
        private int mThumbnailSize;
        private int mThumbnailInset;
        private int mPathColor;
        public static final int STATUS_CANCELLED = -1;
        public static final int STATUS_OK = 0;
        public static final int STATUS_ERROR = -2;
        private GestureAdapter mAdapter;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
            final Resources resources = getResources();
            mPathColor = resources.getColor(R.color.colorPrimary);
            mThumbnailInset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, resources.getDisplayMetrics());;
            mThumbnailSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, resources.getDisplayMetrics());;;
            mAdapter = (GestureAdapter) getListView().getAdapter();
            mAdapter.setNotifyOnChange(false);
            mAdapter.clear();
        }

        @Override
        protected Integer doInBackground(Void... params) {

            if (isCancelled()) return STATUS_CANCELLED;
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                return STATUS_ERROR;
            }

            final GestureLibrary store = mGestureStore;

            if (store.load()) {
                for (String name : store.getGestureEntries()) {
                    if (isCancelled()) break;

                    for (Gesture gesture : store.getGestures(name)) {
                        final Bitmap bitmap = gesture.toBitmap(mThumbnailSize, mThumbnailSize,
                                mThumbnailInset, mPathColor);
                        final NamedGesture namedGesture = new NamedGesture();
                        String[] split = name.split("\\|");
                        if (split[0].equals(name))
                            continue;
                        namedGesture.gesture = gesture;
                        namedGesture.uri = split[0];
                        namedGesture.name = split[1];
                        mAdapter.addBitmap(namedGesture.gesture.getID(), bitmap);
                        publishProgress(namedGesture);
                    }
                }

                return STATUS_OK;
            }

            return STATUS_ERROR;
        }

        @Override
        protected void onProgressUpdate(NamedGesture... values) {
            super.onProgressUpdate(values);

            final GestureAdapter adapter = mAdapter;
            adapter.setNotifyOnChange(false);

            for (NamedGesture gesture : values) {
                adapter.add(gesture);
            }

            adapter.sort(mSorter);
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            mProgressDialog.dismiss();
        }
    }

}