package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Subtitle menu, generated tracks, and user-selected files stay inside the host. */
final class PlayerSubtitles extends PlayerHookSupport {
    private static final String DM = "com.bapis.bilibili.community.service.dm.v1.";
    private static final String LOCAL = "https://interface.bilibili.com/serverdate.js?brx_subtitle=";
    private final HostFilePicker picker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<Long, List<Imported>> imports = new LinkedHashMap<>();
    private final List<ReplyRef> replies = new ArrayList<>();
    private volatile Typeface font;
    private record Track(String source, String mode, String content) { }
    private record Imported(String url, String title) { }
    private record ReplyRef(WeakReference<Object> reply, long cid) { }
    PlayerSubtitles(ModuleEntry entry, HostRuntime runtime) {
        super(entry, runtime); picker = new HostFilePicker(entry);
        worker.execute(() -> { File file = fontFile(); if (file.isFile()) try { font = Typeface.createFromFile(file); } catch (RuntimeException ignored) { } });
    }
    Typeface font() { return font; }
    File fontFile() { return new File(runtime.hostContext.getFilesDir(), "brx-subtitle.font"); }
    void forget(Activity activity) { picker.forget(activity); }
    void install() {
        installPart("subtitle.transport", () -> {
            Class<?> call = host("okhttp3.g"); var request = PlayerReflection.field(call, "f");
            Class<?> body = host("okhttp3.ResponseBody"), mediaType = host("okhttp3.MediaType");
            Object jsonType = mediaType.getDeclaredMethod("parse", String.class).invoke(null, "application/json; charset=utf-8");
            var createBody = body.getDeclaredMethod("create", mediaType, String.class);
            var builder = host("okhttp3.Response$Builder").getDeclaredConstructor();
            Object protocol = host("okhttp3.Protocol").getField("HTTP_1_1").get(null);
            entry.hook(call.getDeclaredMethod("f")).intercept(chain -> {
                Object req = request.get(chain.getThisObject());
                String url = PlayerReflection.call(req, "url").toString();
                if (!url.startsWith(LOCAL)) return chain.proceed();
                if ((boolean) PlayerReflection.call(chain.getThisObject(), "isCanceled")) throw new IOException("Canceled");
                String content;
                try { content = resolve(url); }
                catch (Exception error) {
                    entry.failure("Player.subtitle", "generated track", "resolve", new IllegalStateException(error.getClass().getSimpleName()));
                    content = json(List.of(new SubtitleText.Cue(0, 9999, "字幕处理失败，请重试或切换翻译服务")));
                    notice("字幕处理失败：" + (error instanceof IOException ? error.getMessage() : "数据格式或转换错误"));
                }
                if ((boolean) PlayerReflection.call(chain.getThisObject(), "isCanceled")) throw new IOException("Canceled");
                Object response = builder.newInstance();
                PlayerReflection.call(response, "request", req); PlayerReflection.call(response, "protocol", protocol);
                PlayerReflection.call(response, "code", 200); PlayerReflection.call(response, "message", "OK");
                PlayerReflection.call(response, "addHeader", "Content-Type", "application/json; charset=utf-8");
                PlayerReflection.call(response, "body", createBody.invoke(null, jsonType, content));
                return PlayerReflection.call(response, "build");
            });
        });
        for (String name : new String[]{"com.bilibili.playerbizcommon.widget.function.setting.r", "nz1.f"}) {
            installPart("subtitle.menu." + name, () -> after(host(name).getDeclaredMethod("createContentView", Context.class), "subtitle.menu", (chain, result) -> {
                if (!enabled("subtitle_import_save") || !(result instanceof View view)) return result;
                Object widget = chain.getThisObject();
                LinearLayout wrapper = new LinearLayout(view.getContext()); wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
                LinearLayout buttons = new LinearLayout(view.getContext());
                buttons.addView(button(view.getContext(), "导入字幕", () -> importSubtitle(widget, view)), new LinearLayout.LayoutParams(0, -2, 1));
                buttons.addView(button(view.getContext(), "保存字幕", () -> exportSubtitles(widget, view)), new LinearLayout.LayoutParams(0, -2, 1));
                wrapper.addView(buttons); return wrapper;
            }));
        }
    }
    private TextView button(Context context, String label, Runnable click) {
        TextView button = new TextView(context); button.setText(label); button.setTextColor(0xfffb7299); button.setTextSize(14);
        button.setGravity(Gravity.CENTER); int padding = Math.round(14 * context.getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding, padding, padding); button.setOnClickListener(v -> click.run()); return button;
    }
    private Activity activity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper wrapper) { if (context instanceof Activity activity) return activity; context = wrapper.getBaseContext(); }
        return runtime.getTopActivity();
    }
    private Object service(Object widget) throws ReflectiveOperationException {
        if (widget.getClass().getName().equals("nz1.f")) return PlayerReflection.get(widget, "f");
        return PlayerReflection.call(PlayerReflection.get(widget, "d"), "getInteractLayerService");
    }
    private Object reply(Object service) throws ReflectiveOperationException {
        return PlayerReflection.call(PlayerReflection.call(service, "getDanmakuParams"), "getDmViewReply");
    }
    private synchronized long cid(Object reply) {
        for (ReplyRef known : replies) if (known.reply().get() == reply) return known.cid();
        return 0;
    }
    private synchronized void remember(Object reply, long cid) {
        replies.removeIf(known -> known.reply().get() == null || known.reply().get() == reply);
        replies.add(new ReplyRef(new WeakReference<>(reply), cid));
        if (replies.size() > 128) replies.remove(0);
    }
    private synchronized String register(String source, String mode, String content) {
        for (var entry : tracks.entrySet()) if (entry.getValue().source().equals(source) && entry.getValue().mode().equals(mode) && content == null) return LOCAL + entry.getKey();
        long importedSize = tracks.values().stream().filter(t -> t.content() != null).mapToLong(t -> t.content().length()).sum();
        if (content != null && importedSize + content.length() > 8 * 1024 * 1024) throw new IllegalStateException("本次播放导入字幕过多，请重启后再试");
        String key = UUID.randomUUID().toString(); tracks.put(key, new Track(source, mode, content));
        if (tracks.size() > 128) tracks.remove(tracks.keySet().iterator().next());
        return LOCAL + key;
    }
    String resolve(String url) throws Exception {
        if (!url.startsWith(LOCAL)) return SubtitleFiles.fetch(url.startsWith("//") ? "https:" + url : url);
        Track track; synchronized (this) { track = tracks.get(url.substring(LOCAL.length())); }
        if (track == null) throw new IOException("字幕缓存已失效，请重新载入视频");
        return track.content();
    }

    Object enrich(Object request, Object original) throws ReflectiveOperationException {
        long cid = ((Number) PlayerReflection.call(request, "getOid")).longValue();
        Object result = original;
        List<Imported> added; synchronized (this) { added = new ArrayList<>(imports.getOrDefault(cid, List.of())); }
        if (enabled("auto_select_ai_subtitle") || !added.isEmpty()) {
            result = PlayerProto.edited(original, copy -> {
                Object subtitles = PlayerProto.copy(PlayerReflection.call(copy, "getSubtitle"));
                List<?> current = (List<?>) PlayerReflection.call(subtitles, "getSubtitlesList");
                List<String> languages = new ArrayList<>(); for (Object item : current) languages.add((String) PlayerReflection.call(item, "getLan"));
                for (int i = 0; i < added.size(); i++) {
                    Imported item = added.get(i); String language = "brx-import-" + i;
                    if (!languages.contains(language)) PlayerReflection.call(subtitles, "addSubtitles", newItem(item.url(), language, item.title(), false));
                }
                current = (List<?>) PlayerReflection.call(subtitles, "getSubtitlesList");
                if (enabled("auto_select_ai_subtitle") && !languages.contains("zh-Hans") && !languages.contains("zh-CN")) {
                    for (int i = 0; i < current.size(); i++) if (PlayerReflection.call(current.get(i), "getLan").equals("ai-zh")) {
                        Object selected = PlayerProto.edited(current.get(i), item -> {
                            PlayerReflection.call(item, "setLan", "zh-Hans"); PlayerReflection.call(item, "setType", host(DM + "SubtitleType").getField("CC").get(null));
                        });
                        PlayerReflection.call(subtitles, "setSubtitles", i, selected); break;
                    }
                }
                PlayerReflection.call(copy, "setSubtitle", subtitles);
            });
        }
        remember(result, cid); return result;
    }
    private Object newItem(String url, String language, String title, boolean ai) throws ReflectiveOperationException {
        Object item = host(DM + "SubtitleItem").getDeclaredMethod("parseFrom", byte[].class).invoke(null, (Object) new byte[0]);
        long id = 0x4000000000000000L | (UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits() & 0x3fffffffffffffffL);
        PlayerReflection.call(item, "setId", id); PlayerReflection.call(item, "setIdStr", String.valueOf(id));
        PlayerReflection.call(item, "setLan", language); PlayerReflection.call(item, "setLanDoc", title);
        PlayerReflection.call(item, "setLanDocBrief", language.startsWith("brx-import") ? "导入" : "简中");
        PlayerReflection.call(item, "setSubtitleUrl", url);
        PlayerReflection.call(item, "setType", host(DM + "SubtitleType").getField(ai ? "AI" : "CC").get(null));
        if (ai) {
            PlayerReflection.call(item, "setAiStatus", host(DM + "SubtitleAiStatus").getField("Assist").get(null));
            PlayerReflection.call(item, "setAiType", host(DM + "SubtitleAiType").getField("Translate").get(null));
        }
        return item;
    }
    static List<SubtitleText.Cue> cues(JSONObject document) throws Exception {
        JSONArray body = document.getJSONArray("body"); List<SubtitleText.Cue> result = new ArrayList<>();
        if (body.length() > 20000) throw new IOException("字幕条数过多");
        for (int i = 0; i < body.length(); i++) { JSONObject item = body.getJSONObject(i); result.add(new SubtitleText.Cue(item.getDouble("from"), item.getDouble("to"), item.getString("content"))); }
        if (result.isEmpty()) throw new IOException("文件中没有字幕"); return result;
    }
    static String json(List<SubtitleText.Cue> cues) throws Exception {
        JSONArray body = new JSONArray();
        for (var cue : cues) body.put(new JSONObject().put("from", cue.from()).put("to", cue.to()).put("location", 2).put("content", cue.content()));
        return new JSONObject().put("body", body).toString();
    }
    private void notice(String message) { main.post(() -> Toast.makeText(runtime.hostContext, message, Toast.LENGTH_LONG).show()); }
    private String displayName(Uri uri) {
        try (var cursor = runtime.hostContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return "subtitle.json";
    }
    private void importSubtitle(Object widget, View view) {
        try {
            Activity activity = activity(view); if (activity == null) throw new IllegalStateException("找不到当前播放页面");
            Object service = service(widget); long cid = cid(reply(service));
            if (cid <= 0) throw new IllegalStateException("请重新载入视频后再导入字幕");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            picker.launch(activity, intent, data -> {
                if (data == null || data.getData() == null) return;
                Uri uri = data.getData();
                worker.execute(() -> {
                    try {
                        String name = displayName(uri), input;
                        try (var stream = runtime.hostContext.getContentResolver().openInputStream(uri)) { input = SubtitleFiles.utf8(SubtitleFiles.read(stream, SubtitleFiles.LIMIT)); }
                        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                        List<SubtitleText.Cue> parsed = switch (extension) {
                            case "ass" -> SubtitleText.ass(input); case "vtt", "srt" -> SubtitleText.srtOrVtt(input);
                            case "json" -> cues(new JSONObject(input)); default -> throw new IOException("支持 ASS、SRT、VTT 和 JSON 字幕");
                        };
                        String body = json(parsed);
                        main.post(() -> {
                            try {
                                if (activity.isFinishing() || activity.isDestroyed()) return;
                                Object current = reply(service);
                                if (cid(current) != cid) { notice("播放视频已切换，请重新导入"); return; }
                                String url = register("", "import", body); int index;
                                synchronized (this) { index = imports.getOrDefault(cid, List.of()).size(); }
                                if (index >= 16) throw new IllegalStateException("每个视频最多导入 16 份字幕");
                                String title = "漫游导入" + (index + 1);
                                Object item = newItem(url, "brx-import-" + index, title, false);
                                Object copy = PlayerProto.edited(current, result -> {
                                    Object subtitles = PlayerProto.copy(PlayerReflection.call(result, "getSubtitle"));
                                    PlayerReflection.call(subtitles, "addSubtitles", item); PlayerReflection.call(result, "setSubtitle", subtitles);
                                });
                                PlayerReflection.call(service, "setDmViewReply", copy); remember(copy, cid);
                                PlayerReflection.call(service, "loadSubtitle", item, null);
                                PlayerReflection.call(service, "recordSelectedSubtitle", false, false);
                                synchronized (this) {
                                    imports.computeIfAbsent(cid, ignored -> new ArrayList<>()).add(new Imported(url, title));
                                    if (imports.size() > 8) imports.remove(imports.keySet().iterator().next());
                                }
                                notice("字幕已导入并选中，可关闭字幕面板继续播放");
                            } catch (Exception error) { notice("导入字幕失败，请重新载入视频后重试"); entry.failure("Player.subtitle", widget.getClass().getName(), "import", error); }
                        });
                    } catch (Exception error) { notice("导入失败：" + (error instanceof IOException || error instanceof IllegalArgumentException ? error.getMessage() : "字幕格式无效，请使用 UTF-8 文件")); }
                });
            });
        } catch (Exception error) { notice(error.getMessage() == null ? "无法打开文件选择器" : error.getMessage()); }
    }
    private void exportSubtitles(Object widget, View view) {
        try {
            Activity activity = activity(view); if (activity == null) throw new IllegalStateException("找不到当前播放页面");
            Object current = reply(service(widget)); long cid = cid(current);
            List<?> items = new ArrayList<>((List<?>) PlayerReflection.call(PlayerReflection.call(current, "getSubtitle"), "getSubtitlesList"));
            if (items.isEmpty()) { notice("当前视频没有可保存的字幕"); return; }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "bilibili-" + (cid > 0 ? cid : System.currentTimeMillis()) + "-subtitles.zip");
            picker.launch(activity, intent, data -> {
                if (data == null || data.getData() == null) return;
                worker.execute(() -> {
                    int succeeded = 0, failed = 0;
                    try (var stream = runtime.hostContext.getContentResolver().openOutputStream(data.getData(), "wt")) {
                        if (stream == null) throw new IOException("无法写入所选位置");
                        try (var zip = new ZipOutputStream(stream, StandardCharsets.UTF_8)) {
                            for (int index = 0; index < items.size(); index++) {
                                Object item = items.get(index); String body; List<SubtitleText.Cue> cues;
                                try {
                                    String url = (String) PlayerReflection.call(item, "getSubtitleUrl");
                                    String source = resolve(url);
                                    cues = Uri.parse(url).getPath() != null && Uri.parse(url).getPath().endsWith(".ass") ? SubtitleText.ass(source) : cues(new JSONObject(source));
                                    body = json(cues);
                                } catch (Exception error) { failed++; continue; }
                                String name = (index + 1) + "-" + PlayerReflection.call(item, "getLan") + "-" + PlayerReflection.call(item, "getLanDoc");
                                name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
                                if (name.length() > 120) name = name.substring(0, 120);
                                zip.putNextEntry(new ZipEntry(name + ".json")); zip.write(body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                                zip.putNextEntry(new ZipEntry(name + ".srt")); zip.write(SubtitleText.srt(cues).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                                succeeded++;
                            }
                        }
                        notice("字幕保存完成：成功 " + succeeded + " 份，失败 " + failed + " 份；ZIP 内含 JSON 和 SRT");
                    } catch (Exception error) { notice("字幕保存失败，请检查所选位置或网络"); }
                });
            });
        } catch (Exception error) { notice(error.getMessage() == null ? "无法打开文件选择器" : error.getMessage()); }
    }
    void importFont(Activity activity, Runnable changed) {
        try {
            picker.launch(activity, new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), data -> {
                if (data == null || data.getData() == null) return;
                worker.execute(() -> {
                    File temporary = new File(runtime.hostContext.getFilesDir(), "brx-subtitle.font.tmp");
                    try {
                        byte[] bytes;
                        try (var stream = runtime.hostContext.getContentResolver().openInputStream(data.getData())) { bytes = SubtitleFiles.read(stream, 16 * 1024 * 1024); }
                        Files.write(temporary.toPath(), bytes); Typeface.createFromFile(temporary);
                        Files.move(temporary.toPath(), fontFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
                        font = Typeface.createFromFile(fontFile()); notice("字幕字体已导入，重启哔哩哔哩后生效"); main.post(changed);
                    } catch (Exception error) { notice("字体导入失败，请选择有效的 TTF 或 OTF 文件（不超过 16MB）"); }
                    finally { if (temporary.isFile()) temporary.delete(); }
                });
            });
        } catch (Exception error) { notice("无法打开文件选择器"); }
    }
    void resetFont(Runnable changed) {
        worker.execute(() -> {
            File file = fontFile();
            if (file.exists() && !file.delete()) { notice("字体重置失败"); return; }
            font = null; notice("已恢复默认字幕字体，重启哔哩哔哩后生效"); main.post(changed);
        });
    }
}
