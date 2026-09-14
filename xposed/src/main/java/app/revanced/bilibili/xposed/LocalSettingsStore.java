package app.revanced.bilibili.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** UI, hooks and navigation metadata use the same host-UID SharedPreferences instances. */
final class LocalSettingsStore implements SettingsStore {
    private final SharedPreferences preferences;
    private final SharedPreferences catalog;
    private final ModuleEntry entry;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Set<Runnable> observers = new CopyOnWriteArraySet<>();
    private final Map<String, String> types = new HashMap<>();
    private JSONArray schema = new JSONArray();
    private JSONObject pages = new JSONObject();
    private volatile String migrationStatus = "";
    private volatile String lastReport = "";
    private boolean saving;

    LocalSettingsStore(Context host, ModuleEntry entry) {
        this.entry = entry;
        preferences = host.getSharedPreferences(ModuleConstants.LOCAL_SETTINGS, Context.MODE_PRIVATE);
        catalog = host.getSharedPreferences(ModuleConstants.LOCAL_CATALOG, Context.MODE_PRIVATE);
        // Source path is supplied by the framework; do not query an invisible package or use host Assets.
        try (ZipFile apk = new ZipFile(entry.getModuleApplicationInfo().sourceDir)) {
            var asset = apk.getEntry("assets/settings-schema.json");
            if (asset == null) throw new IllegalStateException("Settings schema absent from module APK");
            try (var stream = apk.getInputStream(asset)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                schema = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
            for (int i = 0; i < schema.length(); i++) {
                JSONObject item = schema.getJSONObject(i);
                types.put(item.getString("key"), item.getString("type"));
            }
            if (types.size() != 204) throw new IllegalStateException("Unexpected settings schema size");
            var pageAsset = apk.getEntry("assets/settings-pages.json");
            if (pageAsset == null) throw new IllegalStateException("Settings pages absent");
            try (var stream = apk.getInputStream(pageAsset)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                pages = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            entry.failure("Settings.schema", "module APK asset", "read schema", error);
            schema = new JSONArray();
            types.clear();
            // The visible first feature must remain usable even when the optional directory is unavailable.
            types.put(ModuleConstants.BOTTOM_KEY, "StringSet");
            types.put("debug", "Boolean");
            migrationStatus = "完整设置目录读取失败；底栏设置仍可使用。";
        }
        if (schema.length() == 204) migrateLegacyOnce();
    }

    private void migrateLegacyOnce() {
        if (preferences.getBoolean(ModuleConstants.MIGRATED, false)) return;
        try {
            var legacy = entry.getRemotePreferences(ModuleConstants.GROUP).getAll();
            var imported = SettingsMigration.missing(types, preferences.getAll(), legacy,
                    preferences.getStringSet(ModuleConstants.EDITED_KEYS, Collections.emptySet()));
            var editor = preferences.edit();
            for (var item : imported.entrySet()) SettingsEdits.putValue(editor, item.getKey(), item.getValue());
            editor.putBoolean(ModuleConstants.MIGRATED, true);
            if (!editor.commit()) throw new IllegalStateException("Host did not persist migrated settings");
            migrationStatus = imported.isEmpty() ? "" : "已迁移旧版配置。";
            entry.log(Log.INFO, ModuleConstants.TAG, "Settings.migration: complete; imported keys=" + imported.size());
        } catch (RuntimeException error) {
            migrationStatus = "旧版配置迁移暂未完成；可直接重新设置，下次启动会重试迁移且保留本地修改。";
            entry.failure("Settings.migration", "legacy Remote Preferences", "read once", error);
        }
    }

    @Override public SharedPreferences preferences() { return preferences; }
    @Override public SharedPreferences catalog() { return catalog; }
    @Override public JSONArray schema() { return schema; }
    @Override public JSONObject pages() { return pages; }
    @Override public String connectionStatus() { return "设置保存在哔哩哔哩内，可直接修改。" + migrationStatus; }
    @Override public boolean busy() { return saving; }
    @Override public void observe(Runnable callback) { observers.add(callback); }
    @Override public void stopObserving(Runnable callback) { observers.remove(callback); }
    @Override public void refresh() { notifyState(); }
    private void notifyState() { main.post(() -> observers.forEach(Runnable::run)); }

    @Override public void save(Consumer<SharedPreferences.Editor> change, Consumer<String> callback) {
        if (saving) { callback.accept("正在保存，请稍候。"); return; }
        saving = true;
        notifyState();
        writer.execute(() -> {
            String message = null;
            try {
                SettingsEdits edits = new SettingsEdits();
                change.accept(edits);
                SettingsMutation update = SettingsMutation.parse(types, edits.puts, new ArrayList<>(edits.removes));
                var editor = preferences.edit();
                if (update.remove) editor.remove(update.key);
                else SettingsEdits.putValue(editor, update.key, update.value);
                Set<String> touched = new HashSet<>(preferences.getStringSet(ModuleConstants.EDITED_KEYS, Collections.emptySet()));
                touched.add(update.key);
                editor.putStringSet(ModuleConstants.EDITED_KEYS, touched);
                editor.putLong(ModuleConstants.REVISION, preferences.getLong(ModuleConstants.REVISION, 0) + 1);
                if (!editor.commit()) throw new IllegalStateException("Host settings persistence failed");
            } catch (RuntimeException error) {
                entry.failure("Settings.save", "host SharedPreferences", "commit", error);
                message = "设置保存失败，请重试。";
            }
            String result = message;
            main.post(() -> {
                saving = false;
                callback.accept(result);
                notifyState();
            });
        });
    }

    void report(Bundle data, String identity) {
        if (identity.equals(lastReport)) return;
        Bundle snapshot = new Bundle(data);
        writer.execute(() -> {
            if (identity.equals(lastReport)) return;
            try {
                String state = snapshot.getString("state", "");
                var edit = catalog.edit().putString("state", state).putLong("received_at", System.currentTimeMillis());
                var ids = snapshot.getStringArrayList("ids");
                var names = snapshot.getStringArrayList("names");
                if (ids != null && names != null) {
                    if (ids.size() != names.size()) throw new IllegalArgumentException("Invalid navigation metadata");
                    JSONArray tabs = new JSONArray();
                    for (int i = 0; i < ids.size(); i++) tabs.put(new JSONObject().put("id", ids.get(i)).put("name", names.get(i)));
                    edit.putString("tabs", tabs.toString()).putLong("revision", snapshot.getLong("revision"))
                            .putInt("before", snapshot.getInt("before")).putInt("after", snapshot.getInt("after"));
                }
                if (!edit.commit()) throw new IllegalStateException("Navigation metadata persistence failed");
                lastReport = identity;
                notifyState();
            } catch (Exception error) {
                entry.failure("BottomBar.catalog", "host SharedPreferences", "save metadata", error);
            }
        });
    }

    void reportDrawer(java.util.List<String> ids, java.util.List<String> names) {
        if (ids.size() != names.size()) return;
        try {
            JSONArray rows = new JSONArray();
            for (int i = 0; i < ids.size(); i++) rows.put(new JSONObject().put("id", ids.get(i)).put("name", names.get(i)));
            String text = rows.toString();
            if (text.equals(catalog.getString("drawer_tabs", ""))) return;
            writer.execute(() -> {
                if (text.equals(catalog.getString("drawer_tabs", ""))) return;
                if (catalog.edit().putString("drawer_tabs", text).commit()) notifyState();
                else entry.failure("Json.Mine.catalog", "host SharedPreferences", "commit", new IllegalStateException("Catalog save failed"));
            });
        } catch (org.json.JSONException error) { entry.failure("Json.Mine.catalog", "JSON", "encode", error); }
    }
}
