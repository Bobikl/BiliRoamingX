package app.revanced.bilibili.xposed;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import app.revanced.bilibili.runtime.BottomBarPolicy;

/** JSON-only portions of the original JSONPatch; no host class is linked into the module. */
final class JsonFeatureFilter {
    private static final String MAIN = "tv.danmaku.bili.ui.main2.resource.MainResourceManager$";
    private static final String ROOM = "com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.";
    private static final String SHOP = "com.bilibili.bililive.room.biz.shopping.beans.";
    private final Map<Class<?>, Map<String, Field>> fields = new ConcurrentHashMap<>();
    private final BiConsumer<List<String>, List<String>> drawerCatalog;
    JsonFeatureFilter(BiConsumer<List<String>, List<String>> drawerCatalog) { this.drawerCatalog = drawerCatalog; }

    private Field field(Object object, String name) throws ReflectiveOperationException {
        Map<String, Field> cache = fields.computeIfAbsent(object.getClass(), ignored -> new ConcurrentHashMap<>());
        Field existing = cache.get(name); if (existing != null) return existing;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field found = type.getDeclaredField(name); found.setAccessible(true); cache.put(name, found); return found; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }
    private Object get(Object object, String key) throws ReflectiveOperationException { return object == null ? null : field(object, key).get(object); }
    private void put(Object object, String key, Object value) throws ReflectiveOperationException { if (object != null) field(object, key).set(object, value); }
    private String string(Object object, String key) throws ReflectiveOperationException { Object value = get(object, key); return value == null ? "" : value.toString(); }
    private List<?> list(Object object, String key) throws ReflectiveOperationException {
        Object value = get(object, key); return value instanceof List<?> items ? items : Collections.emptyList();
    }
    private static boolean on(Map<String, ?> settings, String key) { return Boolean.TRUE.equals(settings.get(key)); }
    private static Set<String> selection(Map<String, ?> settings, String key, boolean all) {
        Object raw = settings.get(key);
        if (raw == null) return all ? Collections.singleton("_all") : Collections.emptySet();
        if (!(raw instanceof Set<?> items)) throw new IllegalArgumentException("Invalid selection: " + key);
        Set<String> result = new HashSet<>();
        for (Object item : items) { if (!(item instanceof String value)) throw new IllegalArgumentException(key); result.add(value); }
        return result;
    }
    private void clear(Object value, String... names) throws ReflectiveOperationException {
        for (String name : names) if (get(value, name) != null) put(value, name, new ArrayList<>());
    }
    Object filter(Object original, Map<String, ?> settings) throws ReflectiveOperationException {
        if (original == null) return null;
        Object data = original.getClass().getName().equals("com.bilibili.okretro.GeneralResponse") ? get(original, "data") : original;
        if (data == null) return original;
        Set<String> popups = selection(settings, "purify_live_popups", false);
        switch (data.getClass().getName()) {
            case MAIN + "TabResponse" -> home(get(data, "tabData"), settings);
            case "tv.danmaku.bili.ui.main2.api.AccountMine" -> mine(data, settings);
            case "com.bilibili.app.authorspace.api.BiliSpace" -> space(data, settings);
            case "tv.danmaku.bili.ui.main.event.model.EventEntranceModel" -> { if (on(settings, "purify_game")) return null; }
            case "com.bilibili.app.comm.list.widget.recommend.RecommendModeGuidanceConfig" -> { if (on(settings, "block_recommend_guidance")) return null; }
            case "com.bilibili.ad.adview.videodetail.danmakuv2.model.DmAdvert" -> { if (on(settings, "block_up_rcmd_ads")) clear(data, "ads"); }
            case "tv.danmaku.bili.ui.splash.ad.model.SplashData" -> { if (on(settings, "purify_splash")) clear(data, "splashList", "strategyList"); }
            case "tv.danmaku.bili.ui.splash.ad.model.SplashShowData" -> { if (on(settings, "purify_splash")) clear(data, "strategyList"); }
            case "tv.danmaku.bili.ui.splash.brand.model.BrandSplashData" -> {
                if (on(settings, "purify_splash")) for (String key : new String[]{"brandList", "preloadList", "queryList", "showList"}) put(data, key, null);
            }
            case "tv.danmaku.bili.ui.splash.event.EventSplashDataList" -> {
                if (on(settings, "purify_splash")) {
                    ArrayList<Object> retained = new ArrayList<>();
                    for (Object event : list(data, "eventList")) if (event != null && Boolean.TRUE.equals(event.getClass().getMethod("isBirthdayData").invoke(event))) retained.add(event);
                    if (get(data, "eventList") != null) put(data, "eventList", retained);
                }
            }
            case SHOP + "LiveShoppingInfo" -> { if (popups.contains("shoppingCard")) { put(data, "shoppingCardDetail", null); put(data, "recommendCardDetail", null); } }
            case SHOP + "LiveGoodsCardInfo", SHOP + "LiveShoppingRecommendCardGoodsDetail" -> { if (popups.contains("shoppingCard")) return null; }
            case SHOP + "LiveShoppingGotoBuyInfo" -> { if (popups.contains("gotoBuy")) return null; }
            case "com.bilibili.bililive.videoliveplayer.net.beans.attentioncard.LiveRoomRecommendCard" -> { if (popups.contains("follow")) return null; }
            case "com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveInfo" -> { if (popups.contains("reserve")) put(data, "showReserveDetail", false); }
            case ROOM + "BiliLiveRoomInfo$DmComboInfo", ROOM + "LiveRoomDanmakuVoteCardInfo" -> { if (popups.contains("plusOne")) return null; }
            case ROOM + "BiliLiveRoomInfo" -> {
                if (popups.contains("follow")) put(get(data, "functionCard"), "followCard", null);
                if (popups.contains("wish")) put(get(data, "functionCard"), "wishlistCard", null);
                if (popups.contains("banner")) put(data, "bannerInfo", null);
                if (popups.contains("plusOne")) { put(data, "dmComboInfo", null); put(data, "danmakuVoteCard", null); }
                if (on(settings, "remove_live_mask")) put(data, "areaMaskInfo", null);
                if (on(settings, "live_no_block")) put(data, "blockInfo", null);
                if (on(settings, "remove_live_watermark") && get(data, "newSwitchInfo") instanceof Map<?, ?> switches) {
                    Map<Object, Object> copy = new java.util.HashMap<>(switches); copy.put("room-player-watermark", 0); put(data, "newSwitchInfo", copy);
                }
            }
            case "com.bilibili.bililive.videoliveplayer.net.beans.gateway.userinfo.BiliLiveRoomUserInfo" -> {
                if (popups.contains("gift")) put(get(data, "functionCard"), "sengGiftCard", null);
                if (popups.contains("task")) put(data, "taskInfo", null);
                if (popups.contains("playTogether")) { put(data, "playTogetherInfo", null); put(data, "playTogetherInfoV2", null); }
                if (popups.contains("qoe")) put(data, "qoe", null);
            }
            default -> { }
        }
        return original;
    }
    void home(Object data, Map<String, ?> settings) throws ReflectiveOperationException {
        if (data == null) return;
        if (on(settings, "purify_game")) {
            ArrayList<Object> kept = new ArrayList<>();
            for (Object tab : list(data, "top")) if (tab == null || !string(tab, "uri").startsWith("bilibili://game_center/home")) kept.add(tab);
            if (get(data, "top") != null) put(data, "top", kept);
        }
        if (on(settings, "disable_main_page_story")) {
            Object left = get(data, "topLeftInfo");
            if (left != null && string(left, "url").startsWith("bilibili://videoshortcut")) put(data, "topLeftInfo", null);
        }
        Set<String> hidden = selection(settings, "customize_home_tab", false);
        if (hidden.isEmpty()) return;
        List<?> tabs = list(data, "tab"); ArrayList<Object> kept = new ArrayList<>();
        for (Object tab : tabs) if (tab == null || !hidden.contains(homeType(string(tab, "uri")))) kept.add(tab);
        // Retain a working home page if a stale/all selection would hide every tab.
        if (!kept.isEmpty() && kept.size() != tabs.size()) put(data, "tab", kept);
    }
    static String homeType(String uri) {
        return switch (uri) {
            case "bilibili://live/home" -> "live";
            case "bilibili://pegasus/promo" -> "promo";
            case "bilibili://pegasus/hottopic" -> "hottopic";
            case "bilibili://pgc/home", "bilibili://following/home_activity_tab/6544" -> "bangumi";
            case "bilibili://pgc/home?home_flow_type=2", "bilibili://pgc/cinema-tab", "bilibili://pgc/cinema_v2", "bilibili://following/home_activity_tab/168644" -> "movie";
            case "bilibili://following/home_activity_tab/95636", "bilibili://following/home_activity_tab/163541" -> "korea";
            default -> "other_tabs";
        };
    }
    void mine(Object mine, Map<String, ?> settings) throws ReflectiveOperationException {
        if (on(settings, "block_tips")) { put(mine, "liveTip", null); put(mine, "gameTips", null); }
        Set<String> showing = selection(settings, "showing_drawer_items", true);
        boolean dots = on(settings, "purify_drawer_reddot");
        List<String> ids = new ArrayList<>(), names = new ArrayList<>(); ArrayList<Object> sections = new ArrayList<>();
        for (Object section : list(mine, "sectionListV2")) {
            if (section == null) continue;
            String sectionTitle = string(section, "title");
            if (!sectionTitle.isEmpty()) { ids.add(sectionTitle); names.add("【标题项目】" + sectionTitle); }
            ArrayList<Object> items = new ArrayList<>(); boolean hasSettings = false;
            for (Object item : list(section, "itemList")) {
                if (item == null) continue;
                String name = string(item, "title"), id = string(item, "id"), uri = string(item, "uri");
                boolean settingsItem = "设置".equals(name) || "activity://main/preference".equals(uri);
                hasSettings |= settingsItem;
                if (!name.isEmpty() && !settingsItem) { ids.add(id); names.add(name); }
                if (dots) { put(item, "redDot", 0); put(item, "redDotRorNew", false); }
                if (settingsItem || name.isEmpty() || BottomBarPolicy.shouldShowing(showing, id)) items.add(item);
            }
            if (get(section, "itemList") != null) put(section, "itemList", items);
            Object button = get(section, "button");
            if (button != null) {
                String text = string(button, "text");
                if (!text.isEmpty()) { ids.add(text); names.add("按钮：" + text); if (!BottomBarPolicy.shouldShowing(showing, text)) put(section, "button", null); }
            }
            // A hidden section cannot remove the host settings entrance.
            if (hasSettings || sectionTitle.isEmpty() || BottomBarPolicy.shouldShowing(showing, sectionTitle)) sections.add(section);
        }
        if (get(mine, "sectionListV2") != null) put(mine, "sectionListV2", sections);
        if (!ids.isEmpty()) drawerCatalog.accept(ids, names);
    }
    void space(Object space, Map<String, ?> settings) throws ReflectiveOperationException {
        Set<String> hidden = selection(settings, "customize_space", false); if (hidden.isEmpty()) return;
        List<?> tabs = list(space, "tab"); ArrayList<Object> kept = new ArrayList<>();
        for (Object tab : tabs) if (tab == null || !hidden.contains("tab." + string(tab, "param"))) kept.add(tab);
        if (!kept.isEmpty() && kept.size() != tabs.size()) put(space, "tab", kept);
        for (String key : new String[]{"liveEntry", "chargeResult", "guard", "adV2", "archiveVideo", "article", "audio", "season", "coinVideo", "recommendVideo", "followComicList", "spaceGame", "cheeseVideo", "fansDress", "favoriteBox", "comicList", "ugcSeasonList", "contractResource", "nftShowModule"}) {
            if (hidden.contains(key)) { put(space, key, null); if ("adV2".equals(key)) put(space, "ad", null); }
        }
        ArrayList<Object> buttons = new ArrayList<>();
        for (Object button : list(space, "buttonEntranceList")) {
            String type = string(button, "moduleType");
            if (!(hidden.contains("chargeResult") && "charge".equals(type) || hidden.contains("guard") && "navigation".equals(type) || hidden.contains("adV2") && "goods".equals(type))) buttons.add(button);
        }
        if (get(space, "buttonEntranceList") != null) put(space, "buttonEntranceList", buttons);
    }
}
