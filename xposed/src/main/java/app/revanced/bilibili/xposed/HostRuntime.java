package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.res.Configuration;
import android.view.ViewGroup;
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
    private Dialog settingsDialog;

    void openSettings(Activity activity) {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Dialog dialog = new Dialog(activity, dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        dialog.setOwnerActivity(activity);
        dialog.setTitle("哔哩漫游X");
        HostSettingsStore store = new HostSettingsStore(hostContext, entry);
        SettingsScreen screen = new SettingsScreen(activity, dialog.getContext(), store, dialog::setContentView, dialog::dismiss);
        settingsDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            screen.pause();
            store.close();
            if (settingsDialog == dialog) settingsDialog = null;
        });
        try {
            dialog.show();
            if (dialog.getWindow() != null) {
                var window = dialog.getWindow();
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
                else window.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | (dark ? 0 : android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
                window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                if (android.os.Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
                    int flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    window.getInsetsController().setSystemBarsAppearance(dark ? 0 : flags, flags);
                }
            }
            screen.resume();
            entry.log(Log.INFO, ModuleConstants.TAG, "Settings.open: host-owned settings page shown");
        } catch (RuntimeException error) {
            dialog.dismiss();
            screen.pause();
            store.close();
            settingsDialog = null;
            throw error;
        }
    }

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
        if (settingsDialog != null && settingsDialog.getOwnerActivity() == activity) settingsDialog.dismiss();
        if (topActivity.get() == activity) topActivity = new WeakReference<>(null);
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
