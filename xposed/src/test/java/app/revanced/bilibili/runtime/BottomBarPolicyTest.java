package app.revanced.bilibili.runtime;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;

public class BottomBarPolicyTest {
    private List<String> visible(Set<String> selection) {
        List<String> tabs = new ArrayList<>(Arrays.asList("home", "dynamic", "shop", "mine"));
        tabs.removeIf(id -> !BottomBarPolicy.shouldShowing(selection, id));
        return tabs;
    }

    @Test public void defaultKeepsServerProvidedTabsInOrder() {
        assertEquals(Arrays.asList("home", "dynamic", "shop", "mine"), visible(Collections.singleton("_all")));
    }

    @Test public void selectionHidesUnselectedButtons() {
        assertEquals(Arrays.asList("home", "mine"), visible(new HashSet<>(Arrays.asList("mine", "home"))));
    }

    @Test public void allSentinelMixedWithIdsRetainsOriginalExplicitSelectionSemantics() {
        assertEquals(Collections.singletonList("home"), visible(new HashSet<>(Arrays.asList("_all", "home"))));
    }

    @Test public void obsoleteIdsDoNotSelectUnrelatedNewButtons() {
        assertEquals(Collections.emptyList(), visible(Collections.singleton("old-tab")));
        // The runtime adapter separately keeps the original list when no current ID matches.
    }
}
