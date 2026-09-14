package app.revanced.bilibili.xposed;

import org.junit.Test;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class SettingsMigrationTest {
    private static final Map<String, String> TYPES = Map.of("debug", "Boolean", "showing_bottom_items", "StringSet");

    @Test public void copiesLegacyBottomAndDebug() {
        var result = SettingsMigration.missing(TYPES, Map.of(),
                Map.of("debug", true, "showing_bottom_items", Set.of("home", "mine")), Set.of());
        assertEquals(true, result.get("debug"));
        assertEquals(Set.of("home", "mine"), result.get("showing_bottom_items"));
    }
    @Test public void neverOverwritesLocalSettingsOrResetsOnRetry() {
        var result = SettingsMigration.missing(TYPES, Map.of("debug", false),
                Map.of("debug", true, "showing_bottom_items", Set.of("home")), Set.of("showing_bottom_items"));
        assertTrue(result.isEmpty());
    }
    @Test public void skipsInternalUnknownAndInvalidValues() {
        var result = SettingsMigration.missing(TYPES, Map.of(),
                Map.of("debug", "true", "showing_bottom_items", Set.of(), "_lsposed_config_revision", 5L, "unknown", true), Set.of());
        assertTrue(result.isEmpty());
    }
    @Test public void importedSetsAreDetachedFromLegacySnapshot() {
        var legacy = new HashSet<>(Set.of("home"));
        var result = SettingsMigration.missing(TYPES, Map.of(), Map.of("showing_bottom_items", legacy), Set.of());
        legacy.add("mine");
        assertEquals(Set.of("home"), result.get("showing_bottom_items"));
    }
}
