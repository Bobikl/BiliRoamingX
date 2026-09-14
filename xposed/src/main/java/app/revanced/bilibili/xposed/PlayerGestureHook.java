package app.revanced.bilibili.xposed;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class PlayerGestureHook extends PlayerHookSupport {
    private final Map<Object, Float> scales = Collections.synchronizedMap(new WeakHashMap<>());
    PlayerGestureHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        installPart("disableLongPress", () -> entry.hook(host("com.bilibili.playerbizcommon.gesture.g$b")
                .getDeclaredMethod("onLongPress", MotionEvent.class)).intercept(chain ->
                enabled("disable_player_long_press") ? null : chain.proceed()));
        installPart("scaleRatio", () -> {
            Class<?> listener = host("com.bilibili.playerbizcommon.gesture.GestureService$m");
            Class<?> service = host("com.bilibili.playerbizcommon.gesture.GestureService");
            var getPlayer = service.getDeclaredMethod("access$getMPlayerContainer$p", service);
            getPlayer.setAccessible(true);
            Class<?> ratio = host("tv.danmaku.videoplayer.core.videoview.AspectRatio");
            Object crop = ratio.getField("RATIO_CENTER_CROP").get(null);
            Object fit = ratio.getField("RATIO_ADJUST_CONTENT").get(null);
            after(listener.getDeclaredMethod("onScaleBegin", ScaleGestureDetector.class), "scaleBegin", (chain, result) -> {
                scales.put(chain.getThisObject(), 1f); return result;
            });
            after(listener.getDeclaredMethod("onScale", ScaleGestureDetector.class), "scale", (chain, result) -> {
                if (enabled("scale_to_switch_ratio")) {
                    Object object = chain.getThisObject();
                    float factor = ((ScaleGestureDetector) chain.getArg(0)).getScaleFactor();
                    if (Float.isFinite(factor) && factor > 0) scales.put(object, scales.getOrDefault(object, 1f) * factor);
                }
                return result;
            });
            after(listener.getDeclaredMethod("onScaleEnd", ScaleGestureDetector.class), "scaleEnd", (chain, result) -> {
                Float scale = scales.remove(chain.getThisObject());
                if (enabled("scale_to_switch_ratio") && scale != null) {
                    Object player = getPlayer.invoke(null, PlayerReflection.get(chain.getThisObject(), "c"));
                    Object render = PlayerReflection.call(player, "getRenderContainerService");
                    PlayerReflection.call(render, "setAspectRatio", scale > 1 ? crop : fit);
                    PlayerReflection.call(render, "resetRenderContainer", true, null);
                }
                return result;
            });
            for (var method : listener.getDeclaredMethods()) {
                if ((method.getName().equals("onScroll") || method.getName().equals("onRotate")) && method.getReturnType() == boolean.class)
                    entry.hook(method).intercept(chain -> enabled("scale_to_switch_ratio") ? true : chain.proceed());
            }
        });
        installPart("scaleRatio.resetWidget", () -> {
            Class<?> widget = host("com.bilibili.playerbizcommon.gesture.y");
            var label = PlayerReflection.field(widget, "e");
            for (var method : new java.lang.reflect.Method[]{widget.getDeclaredMethod("onControlContainerVisibleChanged", boolean.class), widget.getDeclaredMethod("onWidgetShow")})
                after(method, "scaleRatio.resetWidget", (chain, result) -> {
                    if (enabled("scale_to_switch_ratio") && label.get(chain.getThisObject()) instanceof android.view.View view) view.setVisibility(android.view.View.GONE);
                    return result;
                });
        });
    }
}
