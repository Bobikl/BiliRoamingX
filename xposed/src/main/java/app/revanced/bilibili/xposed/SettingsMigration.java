package app.revanced.bilibili.xposed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only import recognized, well-typed keys that have never been edited locally. */
final class SettingsMigration {
    static Map<String, Object> missing(Map<String, String> types, Map<String, ?> local,
                                       Map<String, ?> legacy, Set<String> edited) {
        Map<String, Object> result = new HashMap<>();
        for (var item : legacy.entrySet()) {
            String key = item.getKey();
            if (!types.containsKey(key) || local.containsKey(key) || edited.contains(key) || item.getValue() == null) continue;
            Object value = item.getValue();
            if (value instanceof Set<?> set) value = new ArrayList<>(set);
            try {
                result.put(key, SettingsMutation.parse(types, Map.of(key, value), List.of()).value);
            } catch (IllegalArgumentException ignored) {
                // Bad legacy values must not disable host-local settings or overwrite defaults.
            }
        }
        return result;
    }
    private SettingsMigration() {}
}
