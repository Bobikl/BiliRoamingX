package app.revanced.bilibili.xposed;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Records a UI edit before validating and committing it on the shared host writer. */
final class SettingsEdits implements SharedPreferences.Editor {
    final Map<String, Object> puts = new HashMap<>();
    final Set<String> removes = new HashSet<>();
    private SharedPreferences.Editor put(String key, Object value) {
        if (value == null) return remove(key);
        removes.remove(key); puts.put(key, value); return this;
    }
    @Override public SharedPreferences.Editor putString(String key, String value) { return put(key, value); }
    @Override public SharedPreferences.Editor putStringSet(String key, Set<String> value) {
        return put(key, value == null ? null : new ArrayList<>(value));
    }
    @Override public SharedPreferences.Editor putInt(String key, int value) { return put(key, value); }
    @Override public SharedPreferences.Editor putLong(String key, long value) { return put(key, value); }
    @Override public SharedPreferences.Editor putFloat(String key, float value) { return put(key, value); }
    @Override public SharedPreferences.Editor putBoolean(String key, boolean value) { return put(key, value); }
    @Override public SharedPreferences.Editor remove(String key) { puts.remove(key); removes.add(key); return this; }
    @Override public SharedPreferences.Editor clear() { throw new UnsupportedOperationException("Reset individual settings"); }
    @Override public boolean commit() { throw new UnsupportedOperationException("Use host settings writer"); }
    @Override public void apply() { throw new UnsupportedOperationException("Use host settings writer"); }

    @SuppressWarnings("unchecked")
    static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean flag) editor.putBoolean(key, flag);
        else if (value instanceof Integer number) editor.putInt(key, number);
        else if (value instanceof Long number) editor.putLong(key, number);
        else if (value instanceof Float number) editor.putFloat(key, number);
        else if (value instanceof String text) editor.putString(key, text);
        else if (value instanceof Set<?> set) editor.putStringSet(key, new HashSet<>((Set<String>) set));
        else throw new IllegalArgumentException("Unsupported settings value type");
    }
}
