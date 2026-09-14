package app.revanced.bilibili.xposed;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class JsonFeatureFilterTest {
    static class Tab { String uri, param; Tab(String uri) { this.uri = uri; this.param = uri; } }
    static class Left { String url = "bilibili://videoshortcut/"; }
    static class Home { List<Tab> tab, top; Left topLeftInfo = new Left(); }
    static class Item { long id; String title, uri; int redDot = 1; boolean redDotRorNew = true;
        Item(long id, String title, String uri) { this.id = id; this.title = title; this.uri = uri; } }
    static class Group { String title = "更多服务"; List<Item> itemList; Object button; }
    static class Mine { List<Group> sectionListV2; Object liveTip = new Object(), gameTips = new Object(); }
    static class Space { List<Tab> tab; Object adV2 = new Object(), ad = new Object(); List<Entrance> buttonEntranceList; }
    static class Entrance { String moduleType; Entrance(String value) { moduleType = value; } }
    private JsonFeatureFilter filter() { return new JsonFeatureFilter((ids, names) -> {}); }
    @Test public void defaultsLeaveHomeUntouched() throws Exception {
        Home home = new Home(); home.tab = List.of(new Tab("bilibili://live/home")); home.top = List.of(new Tab("bilibili://game_center/home"));
        Object originalTab = home.tab, originalTop = home.top, originalLeft = home.topLeftInfo;
        filter().home(home, Map.of());
        assertSame(originalTab, home.tab); assertSame(originalTop, home.top); assertSame(originalLeft, home.topLeftInfo);
    }
    @Test public void homeFiltersImmutableListsAndRetainsRecommendation() throws Exception {
        Home home = new Home(); Tab promo = new Tab("bilibili://pegasus/promo");
        home.tab = List.of(new Tab("bilibili://live/home"), promo); home.top = List.of(new Tab("bilibili://game_center/home?x=1"));
        filter().home(home, Map.of("customize_home_tab", Set.of("live"), "purify_game", true, "disable_main_page_story", true));
        assertEquals(List.of(promo), home.tab); assertTrue(home.top.isEmpty()); assertNull(home.topLeftInfo);
    }
    @Test public void cannotRemoveEveryHomeTab() throws Exception {
        Home home = new Home(); home.tab = List.of(new Tab("bilibili://live/home"));
        List<Tab> original = home.tab; filter().home(home, Map.of("customize_home_tab", Set.of("live")));
        assertSame(original, home.tab);
    }
    @Test public void alternateHomeRoutesUseOriginalCategories() {
        assertEquals("movie", JsonFeatureFilter.homeType("bilibili://pgc/cinema_v2"));
        assertEquals("bangumi", JsonFeatureFilter.homeType("bilibili://following/home_activity_tab/6544"));
        assertEquals("other_tabs", JsonFeatureFilter.homeType("bilibili://unknown/future"));
    }
    @Test public void hiddenSectionStillPreservesHostSettingsAndUnfilteredCatalog() throws Exception {
        Item settings = new Item(458, "设置", "activity://main/preference"), game = new Item(20, "游戏", "bilibili://game");
        Group group = new Group(); group.itemList = List.of(game, settings); Mine mine = new Mine(); mine.sectionListV2 = List.of(group);
        List<String> catalog = new ArrayList<>();
        new JsonFeatureFilter((ids, names) -> catalog.addAll(ids)).mine(mine, Map.of("showing_drawer_items", Set.of(), "purify_drawer_reddot", true, "block_tips", true));
        assertEquals(List.of(group), mine.sectionListV2); assertEquals(List.of(settings), group.itemList);
        assertEquals(List.of("更多服务", "20"), catalog); assertEquals(0, settings.redDot); assertFalse(settings.redDotRorNew);
        assertNull(mine.liveTip); assertNull(mine.gameTips);
    }
    @Test public void spaceAdFilteringRetainsOtherEntrancesAndTab() throws Exception {
        Space space = new Space(); Tab main = new Tab("main"), dynamic = new Tab("dynamic"); space.tab = List.of(main, dynamic);
        Entrance goods = new Entrance("goods"), other = new Entrance("other"); space.buttonEntranceList = List.of(goods, other);
        filter().space(space, Map.of("customize_space", Set.of("adV2", "tab.dynamic")));
        assertNull(space.ad); assertNull(space.adV2); assertEquals(List.of(main), space.tab); assertEquals(List.of(other), space.buttonEntranceList);
    }
    @Test public void nullAndUnrelatedParserResultsPassThrough() throws Exception {
        JsonFeatureFilter filter = filter(); Object unknown = new Object();
        assertNull(filter.filter(null, Map.of())); assertSame(unknown, filter.filter(unknown, Map.of("purify_game", true)));
        filter.home(null, Map.of("purify_game", true));
    }
    @Test public void invalidSelectionDoesNotMutateHomeTabs() throws Exception {
        Home home = new Home(); home.tab = List.of(new Tab("bilibili://live/home")); Object original = home.tab;
        assertThrows(IllegalArgumentException.class, () -> filter().home(home, Map.of("customize_home_tab", "live")));
        assertSame(original, home.tab);
    }
    @Test public void splashKeepsBirthdayAndEnvelope() throws Exception {
        var birthday = new tv.danmaku.bili.ui.splash.event.EventSplashDataList.Event(true);
        var ad = new tv.danmaku.bili.ui.splash.event.EventSplashDataList.Event(false);
        var splash = new tv.danmaku.bili.ui.splash.event.EventSplashDataList(List.of(ad, birthday));
        var response = new com.bilibili.okretro.GeneralResponse(splash);
        assertSame(response, filter().filter(response, Map.of("purify_splash", true)));
        assertEquals(List.of(birthday), splash.eventList);
    }
    @Test public void nullSplashListRemainsNull() throws Exception {
        var splash = new tv.danmaku.bili.ui.splash.event.EventSplashDataList(null);
        assertSame(splash, filter().filter(splash, Map.of("purify_splash", true))); assertNull(splash.eventList);
    }
    @Test public void liveFiltersSelectedCardsWithoutMutatingOtherSwitches() throws Exception {
        var room = new com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.BiliLiveRoomInfo();
        Object wish = room.functionCard.wishlistCard, mask = room.areaMaskInfo;
        filter().filter(room, Map.of("purify_live_popups", Set.of("follow", "plusOne"), "remove_live_watermark", true));
        assertNull(room.functionCard.followCard); assertNull(room.dmComboInfo); assertNull(room.danmakuVoteCard);
        assertSame(wish, room.functionCard.wishlistCard); assertSame(mask, room.areaMaskInfo);
        assertEquals(0, room.newSwitchInfo.get("room-player-watermark")); assertEquals(2, room.newSwitchInfo.get("other"));
    }
    @Test public void shoppingNullResultMatchesOriginalEnvelopeSemantics() throws Exception {
        var card = new com.bilibili.bililive.room.biz.shopping.beans.LiveGoodsCardInfo();
        var envelope = new com.bilibili.okretro.GeneralResponse(card); JsonFeatureFilter filter = filter();
        assertSame(envelope, filter.filter(envelope, Map.of()));
        assertNull(filter.filter(envelope, Map.of("purify_live_popups", Set.of("shoppingCard"))));
        assertNull(filter.filter(card, Map.of("purify_live_popups", Set.of("shoppingCard"))));
    }
}
