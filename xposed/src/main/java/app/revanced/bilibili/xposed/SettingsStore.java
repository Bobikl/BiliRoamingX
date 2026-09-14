package app.revanced.bilibili.xposed;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.function.Consumer;

/** One UI, with storage provided by either the module or its UID-checked bridge. */
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
