package app.revanced.bilibili.xposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;

/** One-way, UID-checked navigation discovery. Configuration uses Remote Preferences. */
public final class CatalogProvider extends ContentProvider {
    static final String LOCAL_GROUP = "host_catalog";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context moduleContext = getContext();
        if (moduleContext == null) throw new IllegalStateException("Module context unavailable");
        int caller = Binder.getCallingUid();
        String[] packages = moduleContext.getPackageManager().getPackagesForUid(caller);
        if (caller != Process.myUid() && (packages == null || !Arrays.asList(packages).contains(ModuleConstants.HOST))) {
            throw new SecurityException("Only scoped Bilibili UID may publish navigation metadata");
        }
        if (!"report".equals(method) || extras == null) throw new IllegalArgumentException("Unsupported call");
        String state = extras.getString("state", "");
        if (!Arrays.asList("ready", "partial", "applied", "selection_mismatch").contains(state)) throw new IllegalArgumentException("Invalid report state");
        var prefs = moduleContext.getSharedPreferences(LOCAL_GROUP, Context.MODE_PRIVATE);
        var editor = prefs.edit().putString("state", state).putLong("received_at", System.currentTimeMillis());
        if ("applied".equals(state) || "selection_mismatch".equals(state)) {
            var ids = extras.getStringArrayList("ids");
            var names = extras.getStringArrayList("names");
            if (ids == null || names == null || ids.isEmpty() || ids.size() != names.size() || ids.size() > 64
                    || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Invalid catalog");
            JSONArray array = new JSONArray();
            try {
                for (int i = 0; i < ids.size(); i++) {
                    if (ids.get(i) == null || names.get(i) == null || ids.get(i).isEmpty()
                            || ids.get(i).length() > 128 || names.get(i).length() > 128) {
                        throw new IllegalArgumentException("Invalid navigation label");
                    }
                    array.put(new JSONObject().put("id", ids.get(i)).put("name", names.get(i)));
                }
            } catch (JSONException error) {
                throw new IllegalArgumentException("Invalid navigation report", error);
            }
            int before = extras.getInt("before");
            int after = extras.getInt("after");
            if (before != ids.size() || after < 1 || after > before) throw new IllegalArgumentException("Invalid counts");
            editor.putString("tabs", array.toString()).putLong("revision", extras.getLong("revision"))
                    .putInt("before", before).putInt("after", after);
        }
        if (!editor.commit()) throw new IllegalStateException("Catalog storage failed");
        ((ModuleApplication) moduleContext.getApplicationContext()).notifyState();
        Bundle result = new Bundle();
        result.putBoolean("accepted", true);
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
        throw new UnsupportedOperationException("Use UID-checked call");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
