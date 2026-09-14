package app.revanced.bilibili.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;

final class PlayerMediaHook extends PlayerHookSupport {
    PlayerMediaHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        installPart("cdn", () -> {
            Class<?> segment = host("tv.danmaku.ijk.media.player.IjkMediaAsset$MediaAssertSegment");
            var url = PlayerReflection.field(segment, "url"); var backups = PlayerReflection.field(segment, "backupUrls");
            after(host("tv.danmaku.ijk.media.player.IjkMediaAsset$MediaAssertSegment$Builder").getDeclaredMethod("build"), "cdn", (chain, result) -> {
                if (!enabled("prefer_stable_cdn") || result == null) return result;
                String original = (String) url.get(result); Object raw = backups.get(result);
                if (original == null || !(raw instanceof List<?> values)) return result;
                ArrayList<String> list = new ArrayList<>(); for (Object value : values) list.add(value instanceof String text ? text : "");
                int chosen = PlayerPolicy.stableBackup(original, list);
                if (chosen >= 0) { String stable = list.remove(chosen); list.add(original); backups.set(result, list); url.set(result, stable); }
                return result;
            });
        });
        installPart("hardwareCodec", () -> {
            Class<?> item = host("tv.danmaku.ijk.media.player.IjkMediaPlayerItem");
            var paramsField = PlayerReflection.field(item, "mIjkMediaConfigParams");
            var enabledField = PlayerReflection.field(host("tv.danmaku.ijk.media.player.IjkMediaConfigParams"), "mEnableHwCodec");
            Method method = item.getDeclaredMethod("setItemOptions");
            entry.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (!enabled("force_hw_codec")) return chain.proceed();
                Object params;
                try { params = paramsField.get(chain.getThisObject()); }
                catch (Throwable error) { entry.failure("Player.hardwareCodec", item.getName(), "read options", error); return chain.proceed(); }
                if (params == null) return chain.proceed();
                synchronized (params) {
                    boolean original;
                    try { original = enabledField.getBoolean(params); enabledField.setBoolean(params, true); }
                    catch (Throwable error) { entry.failure("Player.hardwareCodec", item.getName(), "enable option", error); return chain.proceed(); }
                    try { return chain.proceed(); }
                    finally {
                        try { enabledField.setBoolean(params, original); }
                        catch (Throwable error) { entry.failure("Player.hardwareCodec", item.getName(), "restore option", error); }
                    }
                }
            });
        });
    }
}
