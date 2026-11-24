package com.nascent.soundpicker;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SoundPickerActivity extends Activity {
    private static final int REQUEST_AUDIO_FILE = 1;
    private static final int REQUEST_AUDIO_PERMISSION = 2;

    private final List<Tone> tones = new ArrayList<>();
    private int ringtoneType = RingtoneManager.TYPE_ALL;
    private boolean launchedAsPicker;
    private Uri selectedUri;
    private Ringtone previewRingtone;
    private Uri previewUri;
    private ToneAdapter adapter;
    private TextView titleView;
    private TextView emptyView;
    private Button useButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent request = getIntent();
        launchedAsPicker = RingtoneManager.ACTION_RINGTONE_PICKER.equals(request.getAction());
        if (launchedAsPicker) {
            ringtoneType = request.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALL);
            selectedUri = request.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);
        }

        buildUi();
        loadTones();
        requestAudioPermissionIfNeeded();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(242, 242, 247));

        titleView = new TextView(this);
        titleView.setText(titleForType());
        titleView.setTextSize(25);
        titleView.setTextColor(Color.rgb(12, 12, 14));
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleView, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout volumeCard = new LinearLayout(this);
        volumeCard.setOrientation(LinearLayout.VERTICAL);
        volumeCard.setPadding(dp(20), dp(14), dp(20), dp(12));
        volumeCard.setBackground(rounded(Color.WHITE, dp(24)));

        TextView volumeLabel = new TextView(this);
        volumeLabel.setText("Sound volume");
        volumeLabel.setTextSize(16);
        volumeLabel.setTextColor(Color.rgb(28, 28, 30));
        volumeCard.addView(volumeLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView speaker = new TextView(this);
        speaker.setText("♪");
        speaker.setTextSize(24);
        speaker.setTextColor(Color.rgb(63, 117, 220));
        volumeRow.addView(speaker, new LinearLayout.LayoutParams(dp(36), dp(42)));

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int stream = streamForType(ringtoneType);
        SeekBar volume = new SeekBar(this);
        volume.setMax(Math.max(1, audioManager.getStreamMaxVolume(stream)));
        volume.setProgress(audioManager.getStreamVolume(stream));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) audioManager.setStreamVolume(stream, progress, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        volumeRow.addView(volume, new LinearLayout.LayoutParams(0, -2, 1));
        volumeCard.addView(volumeRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams volumeParams = new LinearLayout.LayoutParams(-1, -2);
        volumeParams.bottomMargin = dp(18);
        root.addView(volumeCard, volumeParams);

        TextView sectionLabel = new TextView(this);
        sectionLabel.setText("Available sounds");
        sectionLabel.setTextSize(14);
        sectionLabel.setTextColor(Color.rgb(90, 90, 96));
        sectionLabel.setPadding(dp(8), 0, 0, dp(8));
        root.addView(sectionLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout listCard = new LinearLayout(this);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setBackground(rounded(Color.WHITE, dp(24)));
        listCard.setClipToOutline(true);

        ListView listView = new ListView(this);
        listView.setDividerHeight(dp(1));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(224, 224, 228)));
        listView.setSelector(android.R.color.transparent);
        adapter = new ToneAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> selectTone(tones.get(position)));
        listCard.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        emptyView = new TextView(this);
        emptyView.setText("No sounds found. Try Browse files below.");
        emptyView.setTextSize(15);
        emptyView.setTextColor(Color.rgb(95, 95, 100));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(20), dp(28), dp(20), dp(28));
        listCard.addView(emptyView, new LinearLayout.LayoutParams(-1, -2));

        root.addView(listCard, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button browseButton = new Button(this);
        browseButton.setText("Browse files");
        browseButton.setOnClickListener(v -> browseForAudio());
        actions.addView(browseButton, new LinearLayout.LayoutParams(0, -2, 1));

        useButton = new Button(this);
        useButton.setText("Use sound");
        useButton.setEnabled(selectedUri != null);
        useButton.setOnClickListener(v -> returnSelectedSound());
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, -2, 1);
        useParams.leftMargin = dp(8);
        actions.addView(useButton, useParams);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        updateEmptyView();
    }

    private String titleForType() {
        if (ringtoneType == RingtoneManager.TYPE_ALARM) return "Choose alarm sound";
        if (ringtoneType == RingtoneManager.TYPE_NOTIFICATION) return "Choose notification sound";
        if (ringtoneType == RingtoneManager.TYPE_RINGTONE) return "Choose ringtone";
        return "Choose sound";
    }

    private void requestAudioPermissionIfNeeded() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_AUDIO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION) loadTones();
    }

    private void loadTones() {
        stopPreview();
        tones.clear();
        Set<String> seen = new HashSet<>();

        try {
            RingtoneManager manager = new RingtoneManager(this);
            manager.setType(ringtoneType);
            Cursor cursor = manager.getCursor();
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Uri uri = manager.getRingtoneUri(cursor.getPosition());
                        String name = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                        addTone(uri, name, seen);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (RuntimeException ignored) {
            // Continue with audio files below; a provider may restrict its ringtone catalog.
        }

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int displayColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(titleColumn);
                    if (name == null || name.trim().isEmpty()) name = cursor.getString(displayColumn);
                    addTone(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            name, seen);
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // SAF remains available through Browse files when audio-library access is denied.
        }

        if (selectedUri != null) {
            boolean found = false;
            for (Tone tone : tones) {
                if (tone.uri.equals(selectedUri)) { found = true; break; }
            }
            if (!found) addTone(selectedUri, displayName(selectedUri), seen);
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void addTone(Uri uri, String name, Set<String> seen) {
        if (uri == null || !seen.add(uri.toString())) return;
        if (name == null || name.trim().isEmpty()) name = "Audio file";
        tones.add(new Tone(uri, name.trim()));
    }

    private void updateEmptyView() {
        if (emptyView != null) emptyView.setVisibility(tones.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void selectTone(Tone tone) {
        selectedUri = tone.uri;
        useButton.setEnabled(true);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void togglePreview(Tone tone) {
        if (previewUri != null && previewUri.equals(tone.uri)) {
            stopPreview();
            return;
        }
        stopPreview();
        try {
            previewRingtone = RingtoneManager.getRingtone(this, tone.uri);
            if (previewRingtone == null) throw new IllegalStateException("No ringtone");
            previewRingtone.setStreamType(streamForType(ringtoneType));
            previewRingtone.play();
            previewUri = tone.uri;
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (RuntimeException e) {
            previewRingtone = null;
            previewUri = null;
            Toast.makeText(this, "Can't preview this audio file", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        if (previewRingtone != null) {
            try { previewRingtone.stop(); } catch (RuntimeException ignored) { }
        }
        previewRingtone = null;
        previewUri = null;
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private int streamForType(int type) {
        if ((type & RingtoneManager.TYPE_ALARM) != 0) return AudioManager.STREAM_ALARM;
        if ((type & RingtoneManager.TYPE_NOTIFICATION) != 0) return AudioManager.STREAM_NOTIFICATION;
        return AudioManager.STREAM_RING;
    }

    private void browseForAudio() {
        stopPreview();
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("audio/*");
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(open, REQUEST_AUDIO_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUDIO_FILE || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | UnsupportedOperationException ignored) { }

        selectedUri = uri;
        if (adapter != null) {
            addTone(uri, displayName(uri), new HashSet<>());
            adapter.notifyDataSetChanged();
        }
        useButton.setEnabled(true);
        updateEmptyView();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String name = cursor.getString(column);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment() == null ? "Selected audio file" : uri.getLastPathSegment();
    }

    private void returnSelectedSound() {
        if (selectedUri == null) return;
        stopPreview();
        if (!launchedAsPicker) {
            Toast.makeText(this, "Open Sound Picker from an app's sound chooser to assign a tone.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent result = new Intent();
        result.setData(selectedUri);
        result.setClipData(ClipData.newRawUri(displayName(selectedUri), selectedUri));
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        result.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, selectedUri);
        result.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Tone {
        final Uri uri;
        final String title;
        Tone(Uri uri, String title) { this.uri = uri; this.title = title; }
    }

    private final class ToneAdapter extends BaseAdapter {
        @Override public int getCount() { return tones.size(); }
        @Override public Tone getItem(int position) { return tones.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView name;
            RadioButton radio;
            TextView play;

            if (convertView instanceof LinearLayout && convertView.getTag() instanceof RowViews) {
                row = (LinearLayout) convertView;
                RowViews views = (RowViews) row.getTag();
                name = views.name;
                radio = views.radio;
                play = views.play;
            } else {
                row = new LinearLayout(SoundPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(4), dp(12), dp(4));

                name = new TextView(SoundPickerActivity.this);
                name.setTextSize(17);
                name.setTextColor(Color.rgb(18, 18, 20));
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, dp(62), 1));

                play = new TextView(SoundPickerActivity.this);
                play.setGravity(Gravity.CENTER);
                play.setTextSize(18);
                play.setTextColor(Color.rgb(70, 117, 210));
                row.addView(play, new LinearLayout.LayoutParams(dp(44), dp(54)));

                radio = new RadioButton(SoundPickerActivity.this);
                radio.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(70, 117, 210)));
                row.addView(radio, new LinearLayout.LayoutParams(dp(44), dp(54)));

                row.setTag(new RowViews(name, radio, play));
            }

            Tone tone = getItem(position);
            name.setText(tone.title);
            radio.setChecked(tone.uri.equals(selectedUri));
            play.setText(tone.uri.equals(previewUri) && previewRingtone != null
                    && previewRingtone.isPlaying() ? "■" : "▶");
            radio.setOnClickListener(v -> selectTone(tone));
            play.setOnClickListener(v -> togglePreview(tone));
            row.setOnClickListener(v -> selectTone(tone));
            return row;
        }
    }

    private static final class RowViews {
        final TextView name;
        final RadioButton radio;
        final TextView play;
        RowViews(TextView name, RadioButton radio, TextView play) {
            this.name = name;
            this.radio = radio;
            this.play = play;
        }
    }
}
