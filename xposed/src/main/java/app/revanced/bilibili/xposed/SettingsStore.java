package app.revanced.bilibili.xposed;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.function.Consumer;

/** Host-only settings and diagnostics; there is no independent module UI. */
interface SettingsStore {
    SharedPreferences preferences();
    SharedPreferences catalog();
    JSONArray schema();
    String connectionStatus();
    default boolean busy() { return false; }
    void observe(Runnable observer);
    void stopObserving(Runnable observer);
    void refresh();
    void save(Consumer<SharedPreferences.Editor> change, Consumer<String> callback);
}
