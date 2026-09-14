package app.revanced.bilibili.xposed;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import io.github.libxposed.api.XposedInterface;

/** Speed adapters for the two player implementations shipped in 8.27.0. */
final class PlayerSpeedHook extends PlayerHookSupport {
    private final ThreadLocal<Object> preparing = new ThreadLocal<>();
    private final ThreadLocal<Boolean> longPress = new ThreadLocal<>();
    private WeakReference<Object> lastPlayer = new WeakReference<>(null);
    PlayerSpeedHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    private float defaultSpeed(float original) {
        return PlayerPolicy.defaultSpeed(original, runtime.preferences.getFloat("default_playback_speed", 0),
                enabled("remember_playback_speed"), runtime.preferences.getFloat("selected_playback_speed", 0));
    }
    private void remember(float speed) {
        if (enabled("remember_playback_speed") && speed > 0 && Float.isFinite(speed))
            runtime.preferences.edit().putFloat("selected_playback_speed", speed).apply();
    }
    void install() {
        installPart("speedManager", () -> {
            Class<?> manager = host("com.bilibili.player.tangram.basic.PlaySpeedManagerImpl");
            var state = PlayerReflection.field(manager, "a");
            entry.hook(manager.getDeclaredConstructor()).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    float speed = defaultSpeed(1f);
                    if (speed != 1f) PlayerReflection.call(state.get(chain.getThisObject()), "setValue", speed);
                } catch (Throwable error) { entry.failure("Player.defaultSpeed", manager.getName(), "init", error); }
                return result;
            });
        });
        for (String widget : new String[]{"b42.d", "uy1.a", "com.bilibili.playerbizcommonv2.widget.speed.e"}) {
            installPart("rememberSpeed." + widget, () -> after(host(widget).getDeclaredMethod("C", float.class), "rememberSpeed",
                    (chain, result) -> { remember((float) chain.getArg(0)); return result; }));
        }
        installPart("rememberSpeed.oldSettings", () -> after(host("com.bilibili.playerbizcommon.widget.function.setting.b0")
                .getDeclaredMethod("N1", android.view.View.class), "rememberSpeed", (chain, result) -> {
                    remember((float) PlayerReflection.call(chain.getThisObject(), "K1", ((android.view.View) chain.getArg(0)).getId())); return result;
                }));
        installPart("rememberSpeed.story", () -> after(host("com.bilibili.video.story.setting.StorySpeedDialogManager$createDialog$onSelect$1")
                .getDeclaredMethod("invoke", host("com.bilibili.playerbizcommonv2.widget.setting.dialog.c$b")), "rememberSpeed", (chain, result) -> {
                    remember(Float.parseFloat((String) PlayerReflection.call(chain.getArg(0), "a"))); return result;
                }));
        installPart("rememberSpeed.newSettings", () -> {
            Class<?> widget = host("com.bilibili.playerbizcommonv2.widget.setting.PlayerSettingFunctionWidget2$createSpeed$1$1");
            for (Method method : widget.getDeclaredMethods()) {
                if (method.getName().equals("invoke") && method.getReturnType() == void.class && method.getParameterCount() == 0)
                    after(method, "rememberSpeed", (chain, result) -> { remember(Float.parseFloat((String) PlayerReflection.get(chain.getThisObject(), "$select"))); return result; });
            }
            Class<?> menu = host("com.bilibili.ship.theseus.united.page.toolbar.MenuService$createSpeed$1");
            after(menu.getDeclaredMethod("invoke", int.class), "rememberSpeed", (chain, result) -> {
                List<?> values = (List<?>) PlayerReflection.get(chain.getThisObject(), "$selects");
                int index = (int) chain.getArg(0);
                if (index >= 0 && index < values.size()) remember(Float.parseFloat((String) values.get(index)));
                return result;
            });
        });
        installPart("preparedSpeed", () -> {
            Class<?> core = host("aj3.p0");
            var prepared = core.getDeclaredMethod("z1", core, host("tv.danmaku.ijk.media.player.IMediaPlayer"));
            entry.hook(prepared).intercept(chain -> {
                Object previous = preparing.get(); preparing.set(chain.getArg(1));
                try { return chain.proceed(); }
                finally { if (previous == null) preparing.remove(); else preparing.set(previous); }
            });
            entry.hook(core.getDeclaredMethod("setPlaySpeed", float.class)).intercept(chain -> {
                Object[] args = chain.getArgs().toArray(); Object player = preparing.get();
                try {
                    if (player != null && ((Number) PlayerReflection.call(player, "getVideoSarNum")).intValue() > 0) {
                        synchronized (this) {
                            var activity = runtime.getTopActivity();
                            boolean story = activity != null && activity.getClass().getName().equals("com.bilibili.video.story.StoryVideoActivity");
                            if (lastPlayer.get() != player || story) args[0] = defaultSpeed((float) args[0]);
                            lastPlayer = new WeakReference<>(player);
                        }
                    }
                } catch (Throwable error) { entry.failure("Player.defaultSpeed", core.getName(), "prepared", error); }
                return chain.proceed(args);
            });
        });
        installPart("longPressSpeed", () -> {
            Class<?> action = host("com.bilibili.ship.theseus.united.player.TripleSpeedService$mListener$1$onLongPress$1");
            for (var constructor : action.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != 4 || constructor.getParameterTypes()[1] != float.class) continue;
                entry.hook(constructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    args[1] = PlayerPolicy.longPressSpeed((float) args[1], runtime.preferences.getFloat("long_press_playback_speed", 0));
                    return chain.proceed(args);
                });
            }
        });
        installPart("longPressSpeed.newWidget", () -> {
            Method action = host("com.bilibili.ship.theseus.united.player.TripleSpeedService$showNewTripleSpeedWidget$1$1")
                    .getDeclaredMethod("invokeSuspend", Object.class);
            scopedLongPress(action);
            entry.deoptimize(action);
            speedArgument(host("com.bilibili.ship.theseus.keel.player.TheseusKeelPlayer").getDeclaredMethod("q", float.class));
        });
        installPart("longPressSpeed.mall", () -> {
            Class<?> service = host("com.mall.videodetail.vd.united.player.TripleSpeedService");
            scopedLongPress(service.getDeclaredMethod("w"));
            speedArgument(service.getDeclaredMethod("x", float.class));
        });
    }
    private void scopedLongPress(Method method) {
        entry.hook(method).intercept(chain -> {
            Boolean previous = longPress.get(); longPress.set(true);
            try { return chain.proceed(); }
            finally { if (previous == null) longPress.remove(); else longPress.set(previous); }
        });
    }
    private void speedArgument(Method method) {
        entry.hook(method).intercept(chain -> {
            if (!Boolean.TRUE.equals(longPress.get())) return chain.proceed();
            return chain.proceed(new Object[]{PlayerPolicy.longPressSpeed((float) chain.getArg(0), runtime.preferences.getFloat("long_press_playback_speed", 0))});
        });
    }
}
