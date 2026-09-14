package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.os.Bundle;

/** Standalone entry shares its controls and settings with the in-host page. */
public final class SettingsActivity extends Activity {
    private SettingsScreen screen;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new SettingsScreen(this, this, (ModuleApplication) getApplication(), this::setContentView, null);
    }

    @Override protected void onResume() { super.onResume(); screen.resume(); }
    @Override protected void onPause() { screen.pause(); super.onPause(); }
}
