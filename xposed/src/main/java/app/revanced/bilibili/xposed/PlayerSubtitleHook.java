package app.revanced.bilibili.xposed;

import android.content.res.Configuration;
import android.graphics.Path;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;

final class PlayerSubtitleHook extends PlayerHookSupport {
    PlayerSubtitleHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    private int color(String key, String fallback) {
        try { return (int) Long.parseLong(text(key, fallback).replace("#", ""), 16); }
        catch (NumberFormatException error) { return (int) Long.parseLong(fallback, 16); }
    }
    void install() {
        installPart("subtitleStyle", () -> {
            Class<?> canvas = host("com.bilibili.cron.Canvas");
            var paintField = PlayerReflection.field(canvas, "paint");
            var widthField = PlayerReflection.field(canvas, "maxWidth");
            var layoutField = PlayerReflection.field(canvas, "staticLayout");
            var alignmentField = PlayerReflection.field(canvas, "alignment");
            var measure = canvas.getDeclaredMethod("measureTextFromLayout", StaticLayout.class); measure.setAccessible(true);
            entry.hook(canvas.getDeclaredMethod("drawPath", Path.class, boolean.class)).intercept(chain ->
                    enabled("custom_subtitle") && runtime.preferences.getBoolean("subtitle_remove_bg", true) ? null : chain.proceed());
            entry.hook(canvas.getDeclaredMethod("measureTextImpl", String.class)).intercept(chain -> {
                if (!enabled("custom_subtitle")) return chain.proceed();
                Object object = chain.getThisObject();
                try {
                    float maxWidth = widthField.getFloat(object);
                    if (maxWidth != 0) {
                        TextPaint paint = (TextPaint) paintField.get(object);
                        if (runtime.subtitles.font() != null) paint.setTypeface(runtime.subtitles.font());
                        paint.setStrokeWidth(runtime.preferences.getFloat("subtitle_stroke_width", 5));
                        paint.setFakeBoldText(runtime.preferences.getBoolean("subtitle_bold", true));
                        var resources = runtime.hostContext.getResources();
                        String key = resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                                ? "subtitle_font_size_landscape" : "subtitle_font_size_portrait";
                        int size = runtime.preferences.getInt(key, 0);
                        if (size > 0) paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.getDisplayMetrics()));
                        String value = (String) chain.getArg(0);
                        Layout.Alignment alignment = (Layout.Alignment) alignmentField.get(object);
                        int width = maxWidth <= 0 ? Integer.MAX_VALUE : Math.max(1, (int) Math.min(Integer.MAX_VALUE, maxWidth));
                        StaticLayout first = StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
                                .setAlignment(alignment).setIncludePad(false).build();
                        int lineWidth = 1;
                        for (int i = 0; i < first.getLineCount(); i++) lineWidth = Math.max(lineWidth, (int) Math.ceil(first.getLineWidth(i)));
                        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), paint, lineWidth)
                                .setAlignment(alignment).setIncludePad(false).build();
                        layoutField.set(object, layout); return measure.invoke(null, layout);
                    }
                } catch (Throwable error) { entry.failure("Player.subtitleStyle", canvas.getName(), "measure", error); }
                return chain.proceed();
            });
            var fill = PlayerReflection.field(canvas, "fillColor"); var stroke = PlayerReflection.field(canvas, "strokeColor");
            entry.hook(canvas.getDeclaredMethod("drawText", String.class, float.class, float.class, boolean.class)).intercept(chain -> {
                boolean customize = false;
                try {
                    Object object = chain.getThisObject();
                    customize = enabled("custom_subtitle") && widthField.getFloat(object) != 0;
                    if (customize) {
                        fill.setInt(object, color("subtitle_font_color2", "FFFFFFFF"));
                        stroke.setInt(object, color("subtitle_stroke_color", "FF000000"));
                    }
                } catch (Throwable error) { customize = false; entry.failure("Player.subtitleStyle", canvas.getName(), "color", error); }
                if (!customize) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[3] = true; chain.proceed(args);
                args[3] = false; return chain.proceed(args);
            });
        });
        for (String owner : new String[]{"receive.GetDanmakuConfig", "send.DanmakuConfigChange"}) {
            installPart("subtitleOffset." + owner, () -> entry.hook(host("tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods."
                    + owner + "$SubtitleConfig").getDeclaredMethod("setBottomMargin", Float.class)).intercept(chain -> {
                if (!enabled("custom_subtitle") || chain.getArg(0) == null) return chain.proceed();
                return chain.proceed(new Object[]{(float) chain.getArg(0) + runtime.preferences.getInt("subtitle_offset", 0)});
            }));
        }
    }
}
