package app.revanced.bilibili.xposed;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.Context;
import java.lang.reflect.Method;

/** Only player-related keys from the original ConfigPatch are overridden. */
final class PlayerConfigHook extends PlayerHookSupport {
    PlayerConfigHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        installPart("config", () -> {
            after(host("com.bilibili.lib.blconfig.internal.ABSource").getDeclaredMethod("f", String.class, Boolean.class), "config.ab",
                    (chain, result) -> ab((String) chain.getArgs().get(0), result));
            after(host("com.bilibili.lib.blconfig.internal.ConfigSource").getDeclaredMethod("g", String.class, String.class), "config.string",
                    (chain, result) -> config((String) chain.getArgs().get(0), result));
            Class<?> dd = host("com.bilibili.lib.dd.internal.DDContractImpl"), function = host("kotlin.jvm.functions.Function1");
            after(dd.getDeclaredMethod("getBoolean", String.class, boolean.class, function), "config.ddBool",
                    (chain, result) -> ab((String) chain.getArgs().get(0), result));
            after(dd.getDeclaredMethod("a", String.class, String.class, function), "config.ddString",
                    (chain, result) -> config((String) chain.getArgs().get(0), result));
        });
        installPart("quality.fullscreen", () -> after(host("com.bilibili.playerbizcommon.utils.PlayerSettingHelper").getDeclaredMethod("getDefaultQuality"),
                "quality.fullscreen", (chain, result) -> { int qn = fullQuality(); return qn == 0 ? result : qn; }));
        installPart("route", () -> {
            Class<?> builder = host("com.bilibili.lib.blrouter.RouteRequest$Builder");
            Class<?> request = host("com.bilibili.lib.blrouter.RouteRequest");
            for (var constructor : new java.lang.reflect.Constructor<?>[]{builder.getDeclaredConstructor(android.net.Uri.class), request.getDeclaredConstructor(android.net.Uri.class, builder)}) {
                entry.hook(constructor).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    try { if (args[0] instanceof android.net.Uri uri) args[0] = route(uri); }
                    catch (Throwable error) { entry.failure("Player.route", request.getName(), "adjust player request", error); }
                    return chain.proceed(args);
                });
            }
        });
    }
    int fullQuality() {
        return Integer.parseInt(text(wifiConnected() ? "full_screen_quality" : "full_screen_quality_mobile", "0"));
    }
    // Runs in the host process using its permission, checked below; the module has no components.
    @android.annotation.SuppressLint("MissingPermission")
    boolean wifiConnected() {
        if (runtime.hostContext.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        ConnectivityManager manager = (ConnectivityManager) runtime.hostContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            NetworkCapabilities network = manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
            return network != null && network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (SecurityException denied) { return false; }
    }
    private android.net.Uri route(android.net.Uri uri) {
        String authority = uri.getAuthority(), path = uri.getPath();
        boolean app = "bilibili".equals(uri.getScheme());
        boolean playlist = app && "music".equals(authority) && path != null && path.startsWith("/playlist/playpage") && !path.equals("/playlist/playpage/0");
        boolean video = app && ("story".equals(authority) || "video".equals(authority));
        boolean pgc = ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && "www.bilibili.com".equals(authority) && path != null && path.startsWith("/bangumi/play");
        if (!video && !playlist && !pgc) return uri;
        boolean preload = !text("half_screen_quality", "0").equals("0") || fullQuality() != 0 || runtime.preferences.getFloat("default_playback_speed", 0) != 0;
        boolean oldPlaylist = playlist && text("player_version", "0").equals("1");
        if (!preload && !oldPlaylist) return uri;
        var result = uri.buildUpon(); java.util.List<String> query = new java.util.ArrayList<>();
        if (uri.getEncodedQuery() != null) for (String part : uri.getEncodedQuery().split("&", -1)) {
            String key = android.net.Uri.decode(part.split("=", 2)[0]);
            if ((preload && key.equals("player_preload")) || (oldPlaylist && key.equals("force_old_playlist"))) continue;
            query.add(part);
        }
        result.encodedQuery(query.isEmpty() ? null : String.join("&", query));
        if (oldPlaylist) {
            result.appendQueryParameter("force_old_playlist", "1");
            String aid = uri.getQueryParameter("aid");
            if ("10".equals(uri.getQueryParameter("page_type")) && aid != null && !aid.isEmpty()) result.authority("video").path(aid);
        }
        return result.build();
    }
    private Object ab(String key, Object original) {
        if ("ff_unite_detail2".equals(key) || "ff_unite_player".equals(key)) {
            String version = text("player_version", "0"); if ("1".equals(version)) return false; if ("2".equals(version)) return true;
        }
        if (enabled("disable_p2p_upload")) {
            if ("ff_live_room_player_close_p2p".equals(key)) return true;
            if ("ijkplayer.p2p_hot_push".equals(key) || "ijkplayer.p2p_upload".equals(key)) return false;
        }
        if (enabled("prefer_stable_cdn") && "ijkplayer.p2p_download".equals(key)) return false;
        if ("ff_player_use_remote_auto_threshold_qn".equals(key) && (!"0".equals(text("half_screen_quality", "0"))
                || !"0".equals(text("full_screen_quality", "0")) || !"0".equals(text("full_screen_quality_mobile", "0")))) return true;
        return original;
    }
    private Object config(String key, Object original) throws ReflectiveOperationException {
        if ("ijkplayer.autoswitch_max_qn".equals(key) && (!text("half_screen_quality", "0").equals("0")
                || !text("full_screen_quality", "0").equals("0") || !text("full_screen_quality_mobile", "0").equals("0"))) return "127";
        if ("player.unite_login_qn".equals(key) || "player.unite_unlogin_qn".equals(key)) {
            int quality = Integer.parseInt(text("half_screen_quality", "0"));
            if (quality == 1) quality = (Integer) host("com.bilibili.playerbizcommon.utils.PlayerSettingHelper").getDeclaredMethod("getDefaultQuality").invoke(null);
            if (quality != 0) return String.valueOf(quality);
        }
        return original;
    }
}
