package app.revanced.bilibili.xposed;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class SettingsMutationTest {
    private static final Map<String, String> TYPES = Map.of("debug", "Boolean", "text", "String",
            "int", "Int", "long", "Long", "float", "Float", "set", "StringSet", "showing_bottom_items", "StringSet");
    private SettingsMutation put(String key, Object value) { return SettingsMutation.parse(TYPES, Map.of(key, value), List.of()); }

    @Test public void preservesAllSixSettingTypes() {
        assertEquals(true, put("debug", true).value);
        assertEquals("中文", put("text", "中文").value);
        assertEquals(5, put("int", 5).value);
        assertEquals(5L, put("long", 5L).value);
        assertEquals(1.5f, put("float", 1.5f).value);
        assertEquals(Set.of("首页", "动态"), put("set", List.of("首页", "动态", "首页")).value);
    }
    @Test public void resetUsesRemovalAndNeverWritesDefaultOverExistingType() {
        SettingsMutation reset = SettingsMutation.parse(TYPES, Map.of(), List.of("showing_bottom_items"));
        assertTrue(reset.remove);
        assertNull(reset.value);
    }
    @Test public void deniesInternalAndUnknownKeys() {
        assertThrows(IllegalArgumentException.class, () -> put("_lsposed_config_revision", 99L));
        assertThrows(IllegalArgumentException.class, () -> SettingsMutation.parse(TYPES, Map.of(), List.of("unknown")));
    }
    @Test public void rejectsTypeConfusionAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> put("int", 2L));
        assertThrows(IllegalArgumentException.class, () -> put("debug", "true"));
        assertThrows(IllegalArgumentException.class, () -> put("float", Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> put("float", Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> put("set", List.of(1)));
    }
    @Test public void emptyBottomIsRejectedButOtherEmptySetsAreValid() {
        assertThrows(IllegalArgumentException.class, () -> put("showing_bottom_items", List.of()));
        assertEquals(Set.of(), put("set", List.of()).value);
        assertEquals(Set.of("_all"), put("showing_bottom_items", List.of("_all")).value);
    }
    @Test public void boundsPayloadAndMutationCount() {
        assertThrows(IllegalArgumentException.class, () -> put("text", "a".repeat(32769)));
        assertThrows(IllegalArgumentException.class, () -> put("set", List.of("a".repeat(32769))));
        assertThrows(IllegalArgumentException.class, () -> SettingsMutation.parse(TYPES, Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettingsMutation.parse(TYPES, Map.of("debug", true), List.of("text")));
        assertThrows(IllegalArgumentException.class, () -> SettingsMutation.parse(TYPES, null, List.of("text")));
    }
}
