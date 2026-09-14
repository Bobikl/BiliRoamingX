package app.revanced.bilibili.runtime;

import java.util.Set;

/**
 * Extracted from 1.23.3 JSONPatch.shouldShowing; retain the original selection semantics.
 * SPDX-License-Identifier: GPL-3.0-only
 */
public final class BottomBarPolicy {
    public static final String ALL = "_all";

    private BottomBarPolicy() {}

    public static boolean shouldShowing(Set<? extends String> items, String item) {
        if (items.contains(item)) return true;
        return items.size() == 1 && items.contains(ALL);
    }
}
