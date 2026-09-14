package app.revanced.bilibili.xposed;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keep host menu builders and click callbacks; replace only their speed data. */
final class PlayerSpeedListHook extends PlayerHookSupport {
    private final ThreadLocal<Boolean> menu = new ThreadLocal<>(), story = new ThreadLocal<>();
    private float[] descending, ascending;
    PlayerSpeedListHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        try {
            descending = PlayerPolicy.speeds(text("playback_speed_override", ""), false);
            ascending = PlayerPolicy.speeds(text("playback_speed_override", ""), true);
        } catch (RuntimeException error) { entry.failure("Player.speedList", "settings", "parse", error); return; }
        if (descending.length == 0) return; // This option explicitly requires restarting the host.
        installPart("speedList.menus", () -> {
            var listOf = host("kotlin.collections.CollectionsKt").getMethod("listOf", Object[].class);
            entry.hook(listOf).intercept(chain -> {
                Object input = chain.getArg(0);
                if (Boolean.TRUE.equals(menu.get()) && input instanceof String[] strings
                        && Arrays.equals(strings, new String[]{"0.5", "0.75", "1.0", "1.25", "1.5", "2.0"})) {
                    List<String> result = new ArrayList<>(); for (float speed : ascending) result.add(String.valueOf(speed));
                    return result;
                }
                return chain.proceed();
            });
            Class<?> service = host("com.bilibili.ship.theseus.united.page.toolbar.MenuService");
            scoped(service.getDeclaredMethod("t0", host("com.bilibili.playerbizcommonv2.widget.setting.h"),
                    host("com.bilibili.playerbizcommonv2.widget.setting.channel.VideoSettingType")), menu);
            scoped(host("com.bilibili.playerbizcommonv2.widget.setting.PlayerSettingFunctionWidget2").getDeclaredMethod("z0"), menu);
        });
        for (String name : new String[]{
                "com.bilibili.ad.adview.pegasus.holders.inline.pegasus.IInlinePanelControllerKt$defaultSpeeds$2",
                "com.bilibili.pegasus.card.base.clickprocessors.IInlinePanelControllerKt$defaultSpeeds$2",
                "com.bilibili.pegasus.common.inline.IInlinePanelControllerKt$defaultSpeeds$2",
                "com.bilibili.search2.share.SearchShareHelper$speedsArray$2"}) {
            installPart("speedList." + name, () -> {
                for (Method method : host(name).getDeclaredMethods()) {
                    if (method.getName().equals("invoke") && method.getParameterCount() == 0 && method.getReturnType() == float[].class)
                        after(method, "speedList", (chain, result) -> replacement((float[]) result));
                }
            });
        }
        for (String[] target : new String[][]{
                {"com.bilibili.playerbizcommonv2.widget.speed.f", "d"}, {"uy1.b", "d"}, {"wo2.a", "c"},
                {"com.bilibili.music.podcast.segment.AbsMusicPlayerPanelSegment", "n"},
                {"com.bilibili.music.podcast.view.PodcastSpeedSeekBar", "v"},
                {"com.bilibili.ship.theseus.ogv.intro.kingposition.OgvKingPositionShareService", "v"}}) {
            installPart("speedList." + target[0], () -> {
                Class<?> type = host(target[0]); var field = PlayerReflection.field(type, target[1]);
                for (var constructor : type.getDeclaredConstructors()) entry.hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    try { field.set(chain.getThisObject(), replacement((float[]) field.get(chain.getThisObject()))); }
                    catch (Throwable error) { entry.failure("Player.speedList", type.getName(), "constructor", error); }
                    return result;
                });
            });
        }
        for (String[] target : new String[][]{
                {"com.bilibili.bangumi.logic.page.detail.service.refactor.NewShareService", "v"},
                {"com.mall.videodetail.vd.united.page.toolbar.MenuService", "I"}}) {
            installPart("speedList." + target[0], () -> {
                Class<?> type = host(target[0]); var field = PlayerReflection.field(type, target[1]);
                entry.hookClassInitializer(type).intercept(chain -> {
                    Object result = chain.proceed();
                    try { field.set(null, replacement((float[]) field.get(null))); }
                    catch (Throwable error) { entry.failure("Player.speedList", type.getName(), "static array", error); }
                    return result;
                });
            });
        }
        installPart("speedList.oldSettings", this::oldSettings);
        installPart("speedList.story", this::storyMenu);
    }
    private float[] replacement(float[] original) {
        if (original == null || original.length == 0) return original;
        return (original[0] < original[original.length - 1] ? ascending : descending).clone();
    }
    private void scoped(Method method, ThreadLocal<Boolean> scope) {
        entry.hook(method).intercept(chain -> {
            Boolean previous = scope.get(); scope.set(true);
            try { return chain.proceed(); }
            finally { if (previous == null) scope.remove(); else scope.set(previous); }
        });
        entry.deoptimize(method);
    }
    private void oldSettings() throws ReflectiveOperationException {
        Class<?> type = host("com.bilibili.playerbizcommon.widget.function.setting.b0");
        int[] ids = new int[ascending.length]; for (int i = 0; i < ids.length; i++) ids[i] = View.generateViewId();
        var speedField = PlayerReflection.field(type, "m"); var idsField = PlayerReflection.field(type, "n");
        entry.hook(type.getDeclaredConstructors()[0]).intercept(chain -> {
            Object result = chain.proceed();
            try {
                Object object = chain.getThisObject();
                ViewGroup group = (ViewGroup) PlayerReflection.get(object, "d");
                ViewGroup parent = (ViewGroup) group.getParent();
                HorizontalScrollView scroll = new HorizontalScrollView(group.getContext());
                scroll.setId(group.getId()); scroll.setHorizontalScrollBarEnabled(false);
                LinearLayout row = new LinearLayout(group.getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
                ColorStateList color = (ColorStateList) PlayerReflection.get(object, "k");
                int padding = Math.round(16 * group.getResources().getDisplayMetrics().density);
                for (int i = 0; i < ascending.length; i++) {
                    TextView label = new TextView(group.getContext()); label.setId(ids[i]); label.setText(String.valueOf(ascending[i]));
                    label.setTextSize(14); label.setTextColor(color); label.setPadding(padding, padding / 4, padding, padding / 4);
                    label.setOnClickListener((View.OnClickListener) object); row.addView(label);
                }
                scroll.addView(row); int index = parent.indexOfChild(group); var params = group.getLayoutParams();
                parent.removeView(group); parent.addView(scroll, index, params);
                PlayerReflection.set(object, "d", row); speedField.set(null, ascending.clone()); idsField.set(null, ids.clone());
            } catch (Throwable error) { entry.failure("Player.speedList", type.getName(), "old settings", error); }
            return result;
        });
        // Host fallbacks assume index 2 exists. Resolve arbitrary lists by matching the normal speed instead.
        entry.hook(type.getDeclaredMethod("J1", float.class)).intercept(chain -> {
            float selected = (float) chain.getArg(0); int fallback = ids[0];
            for (int i = 0; i < ascending.length; i++) { if (ascending[i] == selected) return ids[i]; if (ascending[i] == 1f) fallback = ids[i]; }
            return fallback;
        });
        entry.hook(type.getDeclaredMethod("K1", int.class)).intercept(chain -> {
            int id = (int) chain.getArg(0);
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) return ascending[i];
            return 1f;
        });
    }
    private void storyMenu() throws ReflectiveOperationException {
        Class<?> manager = host("com.bilibili.video.story.setting.StorySpeedDialogManager");
        scoped(manager.getDeclaredMethod("b"), story);
        // Rebuild the final item list, retaining the host's selected state and callback.
        Class<?> item = host("com.bilibili.playerbizcommonv2.widget.setting.dialog.c$b");
        var ctor = item.getDeclaredConstructor(String.class, String.class, String.class,
                host("kotlinx.coroutines.flow.MutableStateFlow"), host("kotlin.jvm.functions.Function1"));
        entry.hook(host("com.bilibili.playerbizcommonv2.widget.setting.dialog.c$a").getDeclaredConstructor(List.class)).intercept(chain -> {
            Object[] args = chain.getArgs().toArray();
            try {
                if (Boolean.TRUE.equals(story.get()) && args[0] instanceof List<?> original && !original.isEmpty() && item.isInstance(original.get(0))) {
                    Object first = original.get(0); Object state = PlayerReflection.call(first, "c"), callback = PlayerReflection.call(first, "b");
                    List<Object> replacement = new ArrayList<>();
                    for (float speed : descending) replacement.add(ctor.newInstance(String.valueOf(speed), speed + "x", "", state, callback));
                    args[0] = replacement;
                }
            } catch (Throwable error) { entry.failure("Player.speedList", manager.getName(), "story list", error); }
            return chain.proceed(args);
        });
        entry.hook(manager.getDeclaredMethod("c", float[].class)).intercept(chain ->
                Boolean.TRUE.equals(story.get()) ? chain.proceed(new Object[]{descending.clone()}) : chain.proceed());
    }
}
