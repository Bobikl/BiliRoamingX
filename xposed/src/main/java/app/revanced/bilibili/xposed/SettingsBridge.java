package app.revanced.bilibili.xposed;

import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Module-side protocol. Callers are authenticated by CatalogProvider before entering here. */
final class SettingsBridge {
    static Bundle snapshot(ModuleApplication module) {
        Bundle result = new Bundle();
        SharedPreferences prefs = module.preferences();
        result.putBoolean("connected", prefs != null);
        result.putString("connection", module.connectionStatus());
        result.putString("schema", module.schema().toString());
        result.putBundle("catalog", PreferenceSnapshot.encode(module.catalog()));
        if (prefs != null) result.putBundle("preferences", PreferenceSnapshot.encode(prefs));
        return result;
    }

    @SuppressWarnings("unchecked")
    static Consumer<SharedPreferences.Editor> validate(JSONArray schema, Bundle request) {
        if (request == null) throw new IllegalArgumentException("Missing settings change");
        Bundle puts = request.getBundle("puts");
        Map<String, Object> values = new HashMap<>();
        if (puts != null) for (String key : puts.keySet()) values.put(key, puts.get(key));
        Map<String, String> types = new HashMap<>();
        for (int i = 0; i < schema.length(); i++) {
            JSONObject item = schema.optJSONObject(i);
            if (item != null) types.put(item.optString("key"), item.optString("type"));
        }
        SettingsMutation change = SettingsMutation.parse(types, puts == null ? null : values,
                request.getStringArrayList("removes"));
        return editor -> {
            if (change.remove) { editor.remove(change.key); return; }
            switch (change.type) {
                case "Boolean" -> editor.putBoolean(change.key, (Boolean) change.value);
                case "Int" -> editor.putInt(change.key, (Integer) change.value);
                case "Long" -> editor.putLong(change.key, (Long) change.value);
                case "Float" -> editor.putFloat(change.key, (Float) change.value);
                case "String" -> editor.putString(change.key, (String) change.value);
                case "StringSet" -> editor.putStringSet(change.key, (Set<String>) change.value);
                default -> throw new IllegalArgumentException("Unsupported type");
            }
        };
    }

    private SettingsBridge() {}
}
