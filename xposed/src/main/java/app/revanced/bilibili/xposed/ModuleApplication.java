package app.revanced.bilibili.xposed;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-UID Application: only this side writes framework Remote Preferences. */
public final class ModuleApplication extends Application implements SettingsStore {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writes = Executors.newSingleThreadExecutor();
    private volatile SharedPreferences remote;
    private volatile XposedService boundService;
    private volatile String connection = "尚未连接 LSPosed，请先启用模块并重新打开此页面。";
    private final Set<Runnable> observers = new CopyOnWriteArraySet<>();
    private JSONArray schema = new JSONArray();

    @Override public void onCreate() {
        super.onCreate();
        try (var stream = getAssets().open("settings-schema.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            schema = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) {
            Log.e(ModuleConstants.TAG, "Settings schema unavailable", error);
        }
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService service) {
                try {
                    remote = service.getRemotePreferences(ModuleConstants.GROUP);
                    boundService = service;
                    connection = "已连接 LSPosed，设置可以保存。";
                } catch (RuntimeException error) {
                    remote = null;
                    connection = "读取 LSPosed 设置服务失败，请检查框架版本并重新打开模块。";
                    Log.e(ModuleConstants.TAG, "Remote Preferences bind failed", error);
                }
                notifyState();
            }

            @Override public void onServiceDied(XposedService service) {
                if (boundService == service) {
                    remote = null;
                    boundService = null;
                    connection = "LSPosed 设置连接已断开，请重新打开模块。";
                    notifyState();
                }
            }
        });
    }

    @Override public SharedPreferences preferences() { return remote; }
    @Override public String connectionStatus() { return connection; }
    @Override public SharedPreferences catalog() { return getSharedPreferences(CatalogProvider.LOCAL_GROUP, MODE_PRIVATE); }
    @Override public JSONArray schema() { return schema; }
    @Override public void refresh() { notifyState(); }

    @Override public void observe(Runnable callback) { observers.add(callback); }
    @Override public void stopObserving(Runnable callback) { observers.remove(callback); }
    void notifyState() { main.post(() -> observers.forEach(Runnable::run)); }

    @Override public void save(Consumer<SharedPreferences.Editor> change, Consumer<String> callback) {
        writes.execute(() -> {
            String errorMessage = null;
            try {
                persist(change);
            } catch (RuntimeException error) {
                Log.e(ModuleConstants.TAG, "Remote Preferences save failed", error);
                errorMessage = "保存失败，请确认 LSPosed 已连接后重试。";
            }
            String result = errorMessage;
            main.post(() -> {
                callback.accept(result);
                notifyState();
            });
        });
    }

    /** Binder worker waits for the same writer queue as the standalone UI. Never called on main. */
    void saveFromHost(Consumer<SharedPreferences.Editor> change) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("IPC write on main thread");
        try {
            writes.submit(() -> persist(change)).get();
            notifyState();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Settings write interrupted", error);
        } catch (ExecutionException error) {
            throw new IllegalStateException("Settings write failed", error.getCause());
        }
    }

    private void persist(Consumer<SharedPreferences.Editor> change) {
        SharedPreferences current = remote;
        if (current == null) throw new IllegalStateException("Remote Preferences are unavailable");
        var editor = current.edit();
        change.accept(editor);
        editor.putLong(ModuleConstants.REVISION, current.getLong(ModuleConstants.REVISION, 0) + 1);
        if (!editor.commit()) throw new IllegalStateException("Framework did not confirm persistence");
    }
}
