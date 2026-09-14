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

/** UID-checked discovery and in-host settings bridge; only the module writes Remote Preferences. */
public final class CatalogProvider extends ContentProvider {
    static final String LOCAL_GROUP = "host_catalog";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context moduleContext = getContext();
        if (moduleContext == null) throw new IllegalStateException("Module context unavailable");
        int caller = Binder.getCallingUid();
        String[] packages = moduleContext.getPackageManager().getPackagesForUid(caller);
        if (caller != Process.myUid() && (packages == null || !Arrays.asList(packages).contains(ModuleConstants.HOST))) {
            throw new SecurityException("Only scoped Bilibili UID may access module bridge");
        }
        ModuleApplication module = (ModuleApplication) moduleContext.getApplicationContext();
        if (caller != Process.myUid() && ("settings_snapshot".equals(method) || "settings_save".equals(method))) {
            verifyHostSignature(moduleContext);
        }
        if ("settings_snapshot".equals(method)) return SettingsBridge.snapshot(module);
        if ("settings_save".equals(method)) {
            module.saveFromHost(SettingsBridge.validate(module.schema(), extras));
            Bundle result = SettingsBridge.snapshot(module);
            result.putBoolean("saved", true);
            return result;
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

    private static void verifyHostSignature(Context context) {
        try {
            boolean modern = android.os.Build.VERSION.SDK_INT >= 28;
            var info = context.getPackageManager().getPackageInfo(ModuleConstants.HOST, modern
                    ? android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                    : android.content.pm.PackageManager.GET_SIGNATURES);
            var signatures = modern ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signatures == null || signatures.length != 1) throw new SecurityException("Unexpected host signer count");
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte part : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            if (!ModuleConstants.HOST_CERT_SHA256.contentEquals(hex)) throw new SecurityException("Official host signature required");
        } catch (Exception error) {
            throw new SecurityException("Host settings caller verification failed", error);
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
        throw new UnsupportedOperationException("Use UID-checked call");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
