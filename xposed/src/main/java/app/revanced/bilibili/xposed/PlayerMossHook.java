package app.revanced.bilibili.xposed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

final class PlayerMossHook extends PlayerHookSupport {
    private static final String UGC = "com.bapis.bilibili.app.playurl.v1.";
    private static final String UNITE = "com.bapis.bilibili.app.playerunite.v1.";
    private static final String DM = "com.bapis.bilibili.community.service.dm.v1.";
    private int lossless;
    PlayerMossHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        installPart("moss", () -> {
            lossless = host(UGC + "ConfType").getField("LOSSLESS_VALUE").getInt(null);
            Class<?> handler = host("com.bilibili.lib.moss.api.MossResponseHandler");
            for (var method : host("com.bilibili.lib.moss.api.MossServiceImp").getDeclaredMethods()) {
                boolean blocking = method.getName().equals("blockingUnaryCall");
                if (!blocking && !method.getName().equals("asyncUnaryCall") && !method.getName().equals("asyncServerStreamingCall")) continue;
                entry.hook(method).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    Object request = args[1];
                    if (request == null || !interested(request.getClass().getName())) return chain.proceed();
                    try { args[1] = before(request); }
                    catch (Throwable error) { entry.failure("Player.moss", request.getClass().getName(), "request", error); }
                    if (!blocking && args[2] != null) {
                        Object delegate = args[2];
                        try {
                        args[2] = Proxy.newProxyInstance(runtime.hostLoader, new Class<?>[]{handler}, (proxy, callback, values) -> {
                            Object[] forwarded = values;
                            if ((callback.getName().equals("onNext") || callback.getName().equals("onNextForAck")) && values != null && values.length == 1) {
                                forwarded = values.clone(); forwarded[0] = response(values[0], request);
                            }
                            if (callback.getDeclaringClass() == Object.class) {
                                if (callback.getName().equals("equals")) return proxy == values[0];
                                if (callback.getName().equals("hashCode")) return System.identityHashCode(proxy);
                                if (callback.getName().equals("toString")) return "PlayerResponseHandler";
                            }
                            try { return callback.invoke(delegate, forwarded); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                        });
                        } catch (RuntimeException error) {
                            entry.failure("Player.moss", request.getClass().getName(), "handler", error);
                        }
                    }
                    Object result = chain.proceed(args);
                    return blocking ? response(result, request) : result;
                });
            }
        });
    }
    private boolean interested(String name) {
        return name.equals(UGC + "PlayConfEditReq") || name.equals(UGC + "PlayConfReq")
                || name.equals(UGC + "PlayViewReq") || name.equals(UNITE + "PlayViewUniteReq")
                || name.equals("com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq")
                || name.equals("com.bapis.bilibili.app.view.v1.ContinuousPlayReq")
                || name.equals("com.bapis.bilibili.app.view.v1.ViewProgressReq")
                || name.equals("com.bapis.bilibili.app.viewunite.v1.ViewProgressReq")
                || name.equals(DM + "DmViewReq");
    }
    private boolean quality() {
        return !text("half_screen_quality", "0").equals("0") || !text("full_screen_quality", "0").equals("0")
                || !text("full_screen_quality_mobile", "0").equals("0");
    }
    private Object before(Object req) throws ReflectiveOperationException {
        String name = req.getClass().getName();
        if (name.equals(UGC + "PlayConfEditReq") && enabled("remember_lossless_setting")) {
            for (Object conf : (List<?>) PlayerReflection.call(req, "getPlayConfList")) {
                if (((Number) PlayerReflection.call(conf, "getConfTypeValue")).intValue() == lossless) {
                    Object value = PlayerReflection.call(conf, "getConfValue");
                    runtime.preferences.edit().putBoolean("lossless_enabled", (boolean) PlayerReflection.call(value, "getSwitchVal")).apply();
                }
            }
        }
        if (!quality()) return req;
        PlayerProto.Edit flags = message -> { PlayerReflection.call(message, "setFnval", 4048); PlayerReflection.call(message, "setFourk", true); };
        if (name.equals(UNITE + "PlayViewUniteReq")) return PlayerProto.edited(req, copy -> PlayerProto.child(copy, "Vod", flags));
        if (name.equals(UGC + "PlayViewReq") || name.equals("com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq")) return PlayerProto.edited(req, flags);
        return req;
    }
    private Object response(Object original, Object request) {
        if (original == null) return null;
        try { return transform(original, request); }
        catch (Throwable error) { entry.failure("Player.moss", original.getClass().getName(), "response", error); return original; }
    }
    private void switchValue(Object message) throws ReflectiveOperationException {
        PlayerProto.child(message, "ConfValue", value -> PlayerReflection.call(value, "setSwitchVal", enabled("lossless_enabled")));
    }
    private boolean trial(Object request) throws ReflectiveOperationException {
        if (!enabled("trial_vip_quality") || !PlayerAccount.knownNonVip(runtime)) return false;
        Object vod = request.getClass().getName().equals(UNITE + "PlayViewUniteReq") ? PlayerReflection.call(request, "getVod") : request;
        return ((Number) PlayerReflection.call(vod, "getDownload")).intValue() == 0;
    }
    private void trialStreams(Object message) throws ReflectiveOperationException {
        List<?> streams = (List<?>) PlayerReflection.call(message, "getStreamListList");
        for (int i = 0; i < streams.size(); i++) {
            Object original = streams.get(i);
            if (!(boolean) PlayerReflection.call(original, "hasDashVideo")) continue;
            Object stream = PlayerProto.edited(original, copy -> PlayerProto.child(copy, "StreamInfo", info -> {
                if ((boolean) PlayerReflection.call(info, "getNeedVip")) {
                    PlayerReflection.call(info, "setNeedVip", false); PlayerReflection.call(info, "setVipFree", true);
                }
            }));
            PlayerReflection.call(message, "setStreamList", i, stream);
        }
    }
    private Object transform(Object original, Object request) throws ReflectiveOperationException {
        String name = original.getClass().getName();
        if (name.equals("com.bapis.bilibili.app.view.v1.ContinuousPlayReply") && enabled("disable_auto_next_play"))
            return PlayerProto.edited(original, copy -> PlayerReflection.call(copy, "clearRelates"));
        if (name.equals("com.bapis.bilibili.app.view.v1.ViewProgressReply") && enabled("disable_segmented_section"))
            return PlayerProto.edited(original, copy -> PlayerReflection.call(copy, "setPointPermanent", false));
        if (name.equals("com.bapis.bilibili.app.viewunite.v1.ViewProgressReply") && enabled("disable_segmented_section"))
            return PlayerProto.edited(original, copy -> PlayerProto.child(copy, "VideoGuide", guide ->
                    PlayerProto.child(guide, "VideoPoint", point -> PlayerReflection.call(point, "setPointPermanent", false))));
        if (name.equals(UGC + "PlayConfReply") && enabled("remember_lossless_setting"))
            return PlayerProto.edited(original, copy -> PlayerProto.child(copy, "PlayConf", conf -> PlayerProto.child(conf, "LossLessConf", this::switchValue)));
        if (name.equals(UGC + "PlayViewReply")) {
            boolean trial = trial(request);
            if (!trial && !enabled("remember_lossless_setting")) return original;
            return PlayerProto.edited(original, copy -> {
                if (enabled("remember_lossless_setting")) PlayerProto.child(copy, "PlayConf", conf -> PlayerProto.child(conf, "LossLessConf", this::switchValue));
                if (trial) { PlayerReflection.call(copy, "clearAb"); PlayerProto.child(copy, "VideoInfo", this::trialStreams); }
            });
        }
        if (name.equals(UNITE + "PlayViewUniteReply") && (quality() || enabled("remember_lossless_setting") || enabled("trial_vip_quality"))) {
            boolean trial = trial(request);
            return PlayerProto.edited(original, copy -> {
                if (trial) { PlayerReflection.call(copy, "clearQnTrialInfo"); PlayerProto.child(copy, "VodInfo", this::trialStreams); }
                if (enabled("remember_lossless_setting")) PlayerProto.child(copy, "PlayDeviceConf", conf -> {
                    @SuppressWarnings("unchecked") Map<Object, Object> map = (Map<Object, Object>) PlayerReflection.call(conf, "getMutableDeviceConfsMap");
                    Object value = map.get(lossless);
                    if (value != null) map.put(lossless, PlayerProto.edited(value, this::switchValue));
                });
                if (quality()) PlayerProto.child(copy, "VideoCtrl", ctrl -> PlayerProto.child(ctrl, "AutoQnCtl", qn -> {
                    long full = Long.parseLong(text("full_screen_quality", "0"));
                    long mobile = Long.parseLong(text("full_screen_quality_mobile", "0"));
                    long half = Long.parseLong(text("half_screen_quality", "0"));
                    if (full != 0) { PlayerReflection.call(qn, "setLoginFull", full); PlayerReflection.call(qn, "setNologinFull", full); }
                    if (mobile != 0) { PlayerReflection.call(qn, "setMobileLoginFull", mobile); PlayerReflection.call(qn, "setMobileNologinFull", mobile); }
                    if (half > 1) { PlayerReflection.call(qn, "setLoginHalf", half); PlayerReflection.call(qn, "setNologinHalf", half); }
                    else if (half == 1) {
                        boolean wifi = new PlayerConfigHook(entry, runtime).wifiConnected();
                        PlayerReflection.call(qn, "setLoginHalf", PlayerReflection.call(qn, wifi ? "getLoginFull" : "getMobileLoginFull"));
                        PlayerReflection.call(qn, "setNologinHalf", PlayerReflection.call(qn, wifi ? "getNologinFull" : "getMobileNologinFull"));
                    }
                }));
            });
        }
        if (name.equals(DM + "DmViewReply")) return runtime.subtitles.enrich(request, original);
        return original;
    }
}
