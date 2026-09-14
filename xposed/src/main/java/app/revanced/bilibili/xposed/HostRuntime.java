package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Host-only runtime. Module UI resources and private files never use this Context. */
final class HostRuntime implements Application.ActivityLifecycleCallbacks {
    final Context hostContext;
    final ClassLoader hostLoader;
    final SharedPreferences preferences;
    private final Application hostApplication;
    private final ModuleEntry entry;
    private volatile WeakReference<Activity> topActivity = new WeakReference<>(null);
    private final ExecutorService reports = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BiliRoamingX-catalog");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String lastReport = "";

    HostRuntime(Application hostApplication, ClassLoader hostLoader, SharedPreferences preferences, ModuleEntry entry) {
        this.hostApplication = hostApplication;
        this.hostContext = hostApplication;
        this.hostLoader = hostLoader;
        this.preferences = preferences;
        this.entry = entry;
    }

    void registerLifecycle() {
        hostApplication.registerActivityLifecycleCallbacks(this);
    }

    Activity getTopActivity() {
        return topActivity.get();
    }

    void report(Bundle data, String identity) {
        // Reports contain only navigation labels/IDs, configuration revision and counts.
        if (identity.equals(lastReport)) return;
        reports.execute(() -> {
            if (identity.equals(lastReport)) return;
            try {
                Bundle response = hostContext.getContentResolver().call(
                        Uri.parse("content://" + ModuleConstants.CATALOG_AUTHORITY), "report", null, data);
                if (response == null || !response.getBoolean("accepted")) {
                    throw new IllegalStateException("Module catalog provider did not accept report");
                }
                lastReport = identity;
            } catch (Throwable error) {
                entry.failure("BottomBar.catalog", ModuleConstants.CATALOG_AUTHORITY, "ContentProvider.call(report)", error);
            }
        });
    }

    void debug(String message) {
        if (preferences.getBoolean("debug", false)) entry.log(Log.DEBUG, ModuleConstants.TAG, message);
    }

    @Override public void onActivityResumed(Activity activity) { topActivity = new WeakReference<>(activity); }
    @Override public void onActivityPaused(Activity activity) {
        if (topActivity.get() == activity) topActivity = new WeakReference<>(null);
    }
    @Override public void onActivityDestroyed(Activity activity) {
        if (topActivity.get() == activity) topActivity = new WeakReference<>(null);
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
