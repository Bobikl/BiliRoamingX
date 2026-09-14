package app.revanced.bilibili.xposed;

import java.util.List;
import java.util.Map;
import java.util.HashSet;

/** Pure Java validation of one setting edit received from the host. */
final class SettingsMutation {
    final String key;
    final String type;
    final Object value;
    final boolean remove;

    private SettingsMutation(String key, String type, Object value, boolean remove) {
        this.key = key; this.type = type; this.value = value; this.remove = remove;
    }

    static SettingsMutation parse(Map<String, String> types, Map<String, ?> puts, List<String> removes) {
        if (puts == null || removes == null || puts.size() + removes.size() != 1) {
            throw new IllegalArgumentException("Change exactly one setting");
        }
        String key = removes.isEmpty() ? puts.keySet().iterator().next() : removes.get(0);
        String type = types.get(key);
        if (type == null) throw new IllegalArgumentException("Unknown setting key");
        if (!removes.isEmpty()) return new SettingsMutation(key, type, null, true);
        Object value = puts.get(key);
        boolean valid = switch (type) {
            case "Boolean" -> value instanceof Boolean;
            case "Int" -> value instanceof Integer;
            case "Long" -> value instanceof Long;
            case "Float" -> value instanceof Float number && Float.isFinite(number);
            case "String" -> value instanceof String text && text.length() <= 32768;
            case "StringSet" -> value instanceof List<?> list && list.size() <= 2048;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Invalid setting type or size");
        if ("StringSet".equals(type)) {
            var strings = new HashSet<String>();
            int characters = 0;
            for (Object item : (List<?>) value) {
                if (!(item instanceof String text)) throw new IllegalArgumentException("Invalid set member");
                characters += text.length();
                if (characters > 32768) throw new IllegalArgumentException("Setting too large");
                strings.add(text);
            }
            if ("showing_bottom_items".equals(key) && strings.isEmpty()) {
                throw new IllegalArgumentException("Keep at least one navigation item");
            }
            value = strings;
        }
        return new SettingsMutation(key, type, value, false);
    }
}
