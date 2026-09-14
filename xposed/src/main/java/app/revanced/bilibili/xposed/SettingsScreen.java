package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Original XML hierarchy rendered as Bilibili-style rows in the host window. */
final class SettingsScreen {
    private static final String ROOT = "biliroaming_settings";
    private final Activity activity;
    private final Context context;
    private final SettingsStore module;
    private final Consumer<View> mount;
    private final Runnable close;
    private final Runnable observer = this::render;
    private final ArrayDeque<String[]> history = new ArrayDeque<>();
    private final Map<String, JSONObject> definitions = new HashMap<>();
    private String page = ROOT, title = "哔哩漫游X";
    private boolean active;
    private LinearLayout content;
    private ScrollView scroll;
    private int foreground, muted, background, surface, divider;
    private final int pink = 0xfffb7299;

    SettingsScreen(Activity activity, Context context, SettingsStore module, Consumer<View> mount, Runnable close) {
        this.activity = activity; this.context = context; this.module = module; this.mount = mount; this.close = close;
        JSONArray schema = module.schema();
        for (int i = 0; i < schema.length(); i++) {
            JSONObject def = schema.optJSONObject(i);
            if (def != null) definitions.put(def.optString("key"), def);
        }
    }
    void resume() { active = true; module.observe(observer); render(); }
    void pause() { active = false; module.stopObserving(observer); }
    boolean back() {
        if (history.isEmpty()) return false;
        String[] previous = history.pop(); page = previous[0]; title = previous[1]; scroll = null; render(); return true;
    }
    private void open(String target, String label) {
        history.push(new String[]{page, title}); page = target; title = label; scroll = null; render();
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(context); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private void render() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            foreground = dark ? 0xffe3e5e7 : 0xff18191c; muted = dark ? 0xff9499a0 : 0xff9499a0;
            surface = dark ? 0xff17181a : 0xffffffff; background = dark ? 0xff101113 : 0xfff6f7f8;
            divider = dark ? 0xff242628 : 0xfff1f2f3;
            int position = scroll == null ? 0 : scroll.getScrollY();
            LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(background);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
                if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    var cutout = insets.getDisplayCutout(); left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop()); right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
                view.setPadding(left, top, right, bottom); return insets;
            });
            LinearLayout toolbar = new LinearLayout(context); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setBackgroundColor(surface);
            TextView arrow = label("‹", 34, foreground); arrow.setGravity(Gravity.CENTER); arrow.setContentDescription("返回");
            toolbar.addView(arrow, new LinearLayout.LayoutParams(dp(52), dp(56)));
            arrow.setOnClickListener(v -> { if (!back()) close.run(); });
            TextView heading = label(title, 18, foreground); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            root.addView(toolbar); scroll = new ScrollView(context); scroll.setFillViewport(true);
            content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, dp(8), 0, dp(24));
            scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            JSONObject model = module.pages().optJSONObject(page);
            if (model != null) children(model.optJSONArray("children"));
            else note("设置目录读取失败，请重新打开哔哩哔哩。");
            if (ROOT.equals(page)) note("未移植功能暂不可修改。设置保存后，请彻底关闭并重新打开哔哩哔哩。");
            mount.accept(root); root.requestApplyInsets();
            ScrollView rendered = scroll; rendered.post(() -> rendered.scrollTo(0, position));
        } catch (RuntimeException error) {
            android.util.Log.e(ModuleConstants.TAG, "Settings.render", error); toast("设置页加载失败，请返回重试。");
        }
    }
    private void children(JSONArray rows) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            String kind = row.optString("kind"), key = row.optString("key");
            String label = row.optString("title", definitions.containsKey(key) ? definitions.get(key).optString("title") : "");
            if ("version".equals(key)) {
                addRow(label, BuildConfig.VERSION_NAME + "\n" + module.connectionStatus(), "", null, null); continue;
            }
            if (kind.contains("Category")) { section(label); children(row.optJSONArray("children")); continue; }
            if (row.has("page")) {
                String target = row.optString("page");
                addRow(label, row.optString("summary"), "›", () -> open(target, label), null); continue;
            }
            JSONObject definition = definitions.get(key);
            if (kind.contains("CheckBoxGroup") || kind.contains("RadioGroup")) {
                options(row, definition, label); continue;
            }
            if (definition != null && definition.optBoolean("ported") && "Boolean".equals(definition.optString("type"))) {
                boolean checked = module.preferences().getBoolean(key, definition.optBoolean("default"));
                addRow(label, row.optString("summary"), "", () -> save(e -> e.putBoolean(key, !checked)), checked);
            } else if (definition != null && definition.optBoolean("ported") && row.has("entries")) {
                options(row, definition, label);
            } else {
                addRow(label.isEmpty() ? (key.isEmpty() ? "设置" : key) : label, row.optString("summary"), "未移植", null, null);
            }
        }
    }
    private void options(JSONObject row, JSONObject def, String name) {
        String key = row.optString("key");
        boolean ported = def != null && def.optBoolean("ported");
        JSONArray entries = row.optJSONArray("entries"), values = row.optJSONArray("entryValues");
        if (entries == null) entries = row.optJSONArray("radioEntries");
        if (values == null) values = row.optJSONArray("radioEntryValues");
        boolean dynamic = "showing_bottom_items".equals(key) || "showing_drawer_items".equals(key);
        if (dynamic) {
            entries = new JSONArray(); values = new JSONArray();
            try {
                JSONArray tabs = new JSONArray(module.catalog().getString("showing_bottom_items".equals(key) ? "tabs" : "drawer_tabs", "[]"));
                for (int i = 0; i < tabs.length(); i++) { JSONObject tab = tabs.getJSONObject(i); entries.put(tab.getString("name")); values.put(tab.getString("id")); }
            } catch (org.json.JSONException error) { toast("选项列表读取失败。"); }
            note("勾选需要显示的项目，修改后自动保存。" + ("showing_drawer_items".equals(key) ? "设置入口始终保留。" : "至少保留一个底栏按钮。"));
            addRow("恢复显示全部", "", "", () -> save(e -> e.remove(key)), null);
            if (entries.length() == 0) { note("请先打开首页和“我的”，收到页面数据后再进入此页。"); return; }
        } else if (!name.isEmpty()) section(name);
        if (entries == null || values == null) { addRow(name, "", "未移植", null, null); return; }
        Set<String> selected = new HashSet<>();
        boolean set = def != null && "StringSet".equals(def.optString("type"));
        if (set) {
            Set<String> defaults = new HashSet<>(); JSONArray array = def.optJSONArray("default");
            if (array != null) for (int i = 0; i < array.length(); i++) defaults.add(array.optString(i));
            selected.addAll(module.preferences().getStringSet(key, defaults));
            if (dynamic && selected.size() == 1 && selected.contains("_all")) {
                selected.clear(); for (int i = 0; i < values.length(); i++) selected.add(values.optString(i));
            }
        }
        for (int i = 0; i < Math.min(entries.length(), values.length()); i++) {
            String value = values.optString(i), text = entries.optString(i);
            boolean available = ported && (def.optJSONArray("portedOptions") == null || contains(def.optJSONArray("portedOptions"), value));
            boolean checked = set ? selected.contains(value) : def != null && value.equals(module.preferences().getString(key, def.optString("default")));
            JSONArray summaries = row.optJSONArray("radioEntrySummaries");
            addRow(text, summaries == null ? "" : summaries.optString(i), available ? "" : "未移植", available ? () -> {
                if (set) {
                    Set<String> update = new HashSet<>(selected); if (checked) update.remove(value); else update.add(value);
                    if ("showing_bottom_items".equals(key) && update.isEmpty()) { toast("请至少保留一个底栏按钮。"); return; }
                    save(e -> e.putStringSet(key, update));
                } else save(e -> e.putString(key, value));
            } : null, available ? checked : null, true);
        }
    }
    private static boolean contains(JSONArray values, String value) {
        for (int i = 0; i < values.length(); i++) if (value.equals(values.optString(i))) return true; return false;
    }
    private void section(String title) {
        TextView view = label(title, 13, muted); view.setPadding(dp(16), dp(16), dp(16), dp(9));
        if (title.isEmpty()) view.setPadding(0, dp(5), 0, dp(5)); content.addView(view);
    }
    private void note(String text) {
        TextView view = label(text, 13, muted); view.setPadding(dp(16), dp(14), dp(16), dp(14)); content.addView(view);
    }
    private void addRow(String title, String summary, String status, Runnable action, Boolean checked) {
        addRow(title, summary, status, action, checked, false);
    }
    private void addRow(String title, String summary, String status, Runnable action, Boolean checked, boolean choice) {
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(54));
        row.setPadding(dp(16), dp(13), dp(16), dp(13)); row.setBackgroundColor(surface);
        LinearLayout texts = new LinearLayout(context); texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 16, foreground));
        if (!summary.isEmpty()) { TextView sub = label(summary, 12, muted); sub.setPadding(0, dp(5), 0, 0); texts.addView(sub); }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (checked != null) {
            Toggle toggle = new Toggle(checked, choice); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(choice ? 24 : 40), dp(24)); lp.leftMargin = dp(16); row.addView(toggle, lp);
            row.setContentDescription(title + (checked ? "，已开启" : "，已关闭"));
        } else if (!status.isEmpty()) {
            TextView end = label(status, "›".equals(status) ? 26 : 13, muted); end.setPadding(dp(16), 0, 0, 0); row.addView(end);
        }
        if (action != null) {
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18777777), new ColorDrawable(surface), null));
            row.setEnabled(!module.busy()); row.setOnClickListener(v -> action.run());
        }
        content.addView(row); View line = new View(context); line.setBackgroundColor(divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.leftMargin = dp(16); content.addView(line, lp);
    }
    private void save(Consumer<SharedPreferences.Editor> change) {
        module.save(change, error -> { if (active) toast(error == null ? "已保存，重启哔哩哔哩后生效。" : error); });
    }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show(); }
    private final class Toggle extends View {
        private final boolean checked, choice; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Toggle(boolean checked, boolean choice) { super(context); this.checked = checked; this.choice = choice; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(); paint.setColor(checked ? pink : 0xffc9ccd0);
            if (choice) {
                paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
                canvas.drawCircle(w / 2, h / 2, h / 2 - dp(2), paint);
                if (checked) {
                    paint.setColor(0xffffffff); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
                    Path path = new Path(); path.moveTo(w * .27f, h * .5f); path.lineTo(w * .44f, h * .66f); path.lineTo(w * .75f, h * .34f); canvas.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.FILL); return;
            }
            canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, paint);
            paint.setColor(0xffffffff); canvas.drawCircle(checked ? w - h / 2 : h / 2, h / 2, h / 2 - dp(2), paint);
        }
    }
}
