package app.revanced.bilibili.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** All bridge IPC runs off the host UI thread. No host-owned settings file is created. */
final class HostSettingsStore implements SettingsStore {
    private final Context context;
    private final ModuleEntry entry;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private SharedPreferences catalog = new PreferenceSnapshot(new Bundle());
    private JSONArray schema = new JSONArray();
    private String connection = "正在连接模块设置…";
    private Runnable observer;
    private boolean busy;
    private boolean closed;

    HostSettingsStore(Context context, ModuleEntry entry) {
        this.context = context.getApplicationContext();
        this.entry = entry;
    }

    @Override public SharedPreferences preferences() { return preferences; }
    @Override public SharedPreferences catalog() { return catalog; }
    @Override public JSONArray schema() { return schema; }
    @Override public String connectionStatus() { return connection; }
    @Override public boolean busy() { return busy; }
    @Override public void observe(Runnable callback) { observer = callback; }
    @Override public void stopObserving(Runnable callback) { if (observer == callback) observer = null; }
    @Override public void refresh() { request("settings_snapshot", null, null); }

    @Override public void save(Consumer<SharedPreferences.Editor> change, Consumer<String> callback) {
        if (closed) return;
        if (busy) { callback.accept("正在处理上一项操作，请稍候。"); return; }
        PreferenceSnapshot.Changes changes = new PreferenceSnapshot.Changes();
        change.accept(changes);
        request("settings_save", changes.request(), callback);
    }

    private void request(String method, Bundle data, Consumer<String> callback) {
        if (closed || busy) return;
        busy = true;
        connection = callback == null ? "正在读取模块设置…" : "正在保存…";
        notifyState();
        io.execute(() -> {
            Bundle result = null;
            String errorMessage = null;
            try {
                result = context.getContentResolver().call(Uri.parse("content://" + ModuleConstants.CATALOG_AUTHORITY),
                        method, null, data);
                if (result == null) throw new IllegalStateException("Module bridge unavailable");
                if (callback != null && !result.getBoolean("saved")) throw new IllegalStateException("Write not confirmed");
            } catch (RuntimeException error) {
                // Do not log setting keys, values or request bundles.
                entry.failure("Settings.bridge", ModuleConstants.CATALOG_AUTHORITY, method, error);
                errorMessage = "模块设置连接失败，请确认模块已启用；可先打开一次独立模块，再返回此页刷新。";
            }
            Bundle response = result;
            String failure = errorMessage;
            main.post(() -> {
                if (closed) return;
                busy = false;
                String message = failure;
                if (message == null) {
                    try {
                        schema = new JSONArray(response.getString("schema", "[]"));
                        Bundle stored = response.getBundle("preferences");
                        preferences = response.getBoolean("connected") && stored != null ? new PreferenceSnapshot(stored) : null;
                        Bundle metadata = response.getBundle("catalog");
                        catalog = new PreferenceSnapshot(metadata == null ? new Bundle() : metadata);
                        connection = response.getString("connection", "模块设置服务尚未连接。");
                    } catch (JSONException | RuntimeException error) {
                        entry.failure("Settings.snapshot", ModuleConstants.CATALOG_AUTHORITY, method, error);
                        message = "模块设置数据读取失败，请更新模块后重试。";
                    }
                }
                if (message != null) { preferences = null; connection = message; }
                if (callback != null) callback.accept(message);
                notifyState();
            });
        });
    }

    private void notifyState() { if (observer != null) observer.run(); }
    void close() { closed = true; observer = null; io.shutdown(); }
}
