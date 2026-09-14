package app.revanced.bilibili.xposed;

import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A detached IPC snapshot, never backed by host files. Editors only record changes. */
final class PreferenceSnapshot implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();

    PreferenceSnapshot(Bundle bundle) {
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            values.put(key, value instanceof ArrayList<?> list ? new HashSet<>(list) : value);
        }
    }

    static Bundle encode(SharedPreferences prefs) {
        Changes changes = new Changes();
        for (var item : prefs.getAll().entrySet()) {
            Object value = item.getValue();
            String key = item.getKey();
            if (value instanceof String text) changes.putString(key, text);
            else if (value instanceof Boolean flag) changes.putBoolean(key, flag);
            else if (value instanceof Integer number) changes.putInt(key, number);
            else if (value instanceof Long number) changes.putLong(key, number);
            else if (value instanceof Float number) changes.putFloat(key, number);
            else if (value instanceof Set<?> set) {
                Set<String> strings = new HashSet<>();
                for (Object entry : set) strings.add((String) entry);
                changes.putStringSet(key, strings);
            }
        }
        return changes.puts;
    }

    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
    @Override public int getInt(String key, int fallback) { return (Integer) values.getOrDefault(key, fallback); }
    @Override public long getLong(String key, long fallback) { return (Long) values.getOrDefault(key, fallback); }
    @Override public float getFloat(String key, float fallback) { return (Float) values.getOrDefault(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return (Boolean) values.getOrDefault(key, fallback); }
    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        Set<String> result = (Set<String>) values.getOrDefault(key, fallback);
        return result == null ? null : new HashSet<>(result);
    }
    @Override public Editor edit() { throw new UnsupportedOperationException("Use SettingsStore.save"); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    static final class Changes implements Editor {
        final Bundle puts = new Bundle();
        final Set<String> removes = new HashSet<>();
        private void put(String key) { removes.remove(key); }
        @Override public Editor putString(String key, String value) {
            if (value == null) return remove(key);
            put(key); puts.putString(key, value); return this;
        }
        @Override public Editor putStringSet(String key, Set<String> value) {
            if (value == null) return remove(key);
            put(key); puts.putStringArrayList(key, new ArrayList<>(value)); return this;
        }
        @Override public Editor putInt(String key, int value) { put(key); puts.putInt(key, value); return this; }
        @Override public Editor putLong(String key, long value) { put(key); puts.putLong(key, value); return this; }
        @Override public Editor putFloat(String key, float value) { put(key); puts.putFloat(key, value); return this; }
        @Override public Editor putBoolean(String key, boolean value) { put(key); puts.putBoolean(key, value); return this; }
        @Override public Editor remove(String key) { puts.remove(key); removes.add(key); return this; }
        @Override public Editor clear() { throw new UnsupportedOperationException("Reset individual settings"); }
        @Override public boolean commit() { throw new UnsupportedOperationException("Recording editor"); }
        @Override public void apply() { throw new UnsupportedOperationException("Recording editor"); }
        Bundle request() {
            Bundle result = new Bundle();
            result.putBundle("puts", puts);
            result.putStringArrayList("removes", new ArrayList<>(removes));
            return result;
        }
    }
}
