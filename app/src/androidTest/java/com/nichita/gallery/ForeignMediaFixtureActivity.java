package com.nichita.gallery;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.util.UUID;

// Pure SDK Java: this separate test-APK process cannot load the target app's Kotlin runtime.
public final class ForeignMediaFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("owned_fixture", MODE_PRIVATE);
        String previous = prefs.getString("uri", null);
        if ("com.nichita.gallery.test.CLEANUP".equals(getIntent().getAction())) {
            if (previous != null && Uri.parse(previous).equals(getIntent().getData())) {
                try { getContentResolver().delete(Uri.parse(previous), null, null); }
                catch (RuntimeException ignored) { /* Already removed by the confirmed request. */ }
                prefs.edit().clear().commit();
            }
            setResult(RESULT_OK); finish(); return;
        }
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Gallery13-owned-fixture-" + UUID.randomUUID() + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Gallery13Tests");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (uri == null) throw new IllegalStateException("Fixture creation failed");
            prefs.edit().putString("uri", uri.toString()).commit();
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                Bitmap bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                bitmap.recycle();
            }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            setResult(RESULT_OK, new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception error) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            prefs.edit().clear().commit();
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
