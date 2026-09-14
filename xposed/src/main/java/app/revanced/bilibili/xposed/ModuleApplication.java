package app.revanced.bilibili.xposed;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-UID Application: only this side writes framework Remote Preferences. */
public final class ModuleApplication extends Application {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writes = Executors.newSingleThreadExecutor();
    private volatile SharedPreferences remote;
    private volatile XposedService boundService;
    private volatile String connection = "尚未连接 LSPosed，请先启用模块并重新打开此页面。";
    private Runnable observer;

    @Override public void onCreate() {
        super.onCreate();
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

    SharedPreferences preferences() { return remote; }
    String connectionStatus() { return connection; }

    void observe(Runnable callback) { observer = callback; }
    void stopObserving(Runnable callback) { if (observer == callback) observer = null; }
    void notifyState() { main.post(() -> { if (observer != null) observer.run(); }); }

    void save(Consumer<SharedPreferences.Editor> change, Consumer<String> callback) {
        writes.execute(() -> {
            String errorMessage = null;
            try {
                SharedPreferences current = remote;
                if (current == null) throw new IllegalStateException("Remote Preferences are unavailable");
                var editor = current.edit();
                change.accept(editor);
                editor.putLong(ModuleConstants.REVISION, current.getLong(ModuleConstants.REVISION, 0) + 1);
                if (!editor.commit()) throw new IllegalStateException("Framework did not confirm persistence");
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
}
