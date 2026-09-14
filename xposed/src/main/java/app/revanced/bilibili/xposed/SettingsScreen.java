package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import app.revanced.bilibili.runtime.BottomBarPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Shared native screen mounted in the module Activity or a host-owned Dialog. */
final class SettingsScreen {
    private final SettingsStore module;
    private JSONArray schema;
    private LinearLayout content;
    private LinearLayout settingsList;
    private ScrollView scroll;
    private boolean advanced;
    private String search = "";
    private Set<String> pendingBottom;
    private boolean bottomDirty;
    private String catalogIdentity = "";
    private final Runnable observer = this::render;

    private final Activity activity;
    private final Context context;
    private final java.util.function.Consumer<android.view.View> mount;
    private final Runnable close;
    private boolean active;
    private AlertDialog editingDialog;

    SettingsScreen(Activity activity, Context context, SettingsStore module,
                   java.util.function.Consumer<android.view.View> mount, Runnable close) {
        this.activity = activity;
        this.context = context;
        this.module = module;
        this.mount = mount;
        this.close = close;
    }

    void resume() {
        active = true;
        module.observe(observer);
        render();
        module.refresh();
    }

    void pause() {
        active = false;
        module.stopObserving(observer);
        if (editingDialog != null) { editingDialog.dismiss(); editingDialog = null; }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        android.util.TypedValue color = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true)) {
            view.setTextColor(color.resourceId != 0 ? context.getColorStateList(color.resourceId)
                    : android.content.res.ColorStateList.valueOf(color.data));
        }
        view.setPadding(0, dp(7), 0, dp(7));
        content.addView(view);
        return view;
    }

    private Button button(String label, Runnable action, boolean enabled) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setEnabled(enabled);
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void render() {
        try { renderContent(); }
        catch (RuntimeException error) {
            android.util.Log.e(ModuleConstants.TAG, "Module settings rendering failed", error);
            toast("模块设置显示失败，请返回后重试。");
        }
    }

    private void renderContent() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        int oldScroll = scroll == null ? 0 : scroll.getScrollY();
        scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(28));
        // Let the window report status/navigation bars and display cutouts; no fixed heights.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                var cutout = insets.getDisplayCutout();
                left = Math.max(left, cutout.getSafeInsetLeft());
                top = Math.max(top, cutout.getSafeInsetTop());
                right = Math.max(right, cutout.getSafeInsetRight());
                bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        scroll.addView(content);
        mount.accept(scroll);
        scroll.requestApplyInsets();
        schema = module.schema();
        if (close != null) button("返回哔哩哔哩设置", close, true);
        text("哔哩漫游X", 27).setTypeface(null, Typeface.BOLD);
        text("LSPosed 模块 · 粉版 8.27.0", 15);
        text(module.connectionStatus(), 15);
        SharedPreferences preferences = module.preferences();
        long revision = preferences == null ? 0 : preferences.getLong(ModuleConstants.REVISION, 0);
        var catalog = module.catalog();
        String state = catalog.getString("state", "");
        long readRevision = catalog.getLong("revision", -1);
        if ("applied".equals(state)) {
            text("宿主最近读取：配置 " + readRevision + "，显示 " + catalog.getInt("after", 0)
                    + " / " + catalog.getInt("before", 0) + " 个底栏按钮。", 15);
            text("回传时间：" + DateFormat.getDateTimeInstance().format(new Date(catalog.getLong("received_at", 0))), 13);
            if (revision != readRevision) text("新设置已保存，等待重启哔哩哔哩后读取。", 15);
        } else if ("selection_mismatch".equals(state)) {
            text("所选按钮与当前底栏不匹配，宿主已保留原样。请按下面的最新列表重新选择，或恢复显示全部。", 15);
        } else if ("ready".equals(state)) {
            text("宿主 Hook 已加载，等待首页底栏数据。", 15);
        } else if ("partial".equals(state)) {
            text("部分 Hook 安装失败，请查看 LSPosed 模块日志。", 15);
        } else {
            text("尚未收到宿主回传。启用模块并勾选哔哩哔哩后，彻底关闭并重新打开哔哩哔哩，再返回此页。", 15);
        }
        text("需要展示的底栏", 21).setTypeface(null, Typeface.BOLD);
        text("取消勾选即可隐藏。保存后彻底关闭并重新打开哔哩哔哩。首轮只实现底栏过滤；其他功能尚未移植。", 15);
        renderBottom(preferences, catalog.getString("tabs", "[]"));
        button("刷新状态", module::refresh, true);
        if (close == null) button("打开哔哩哔哩", () -> {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(ModuleConstants.HOST);
            if (launch == null) toast("没有找到粉版哔哩哔哩。");
            else {
                try { activity.startActivity(launch); }
                catch (RuntimeException error) {
                    android.util.Log.e(ModuleConstants.TAG, "Host launch failed", error);
                    toast("无法打开哔哩哔哩，请从桌面启动。");
                }
            }
        }, true);
        button(advanced ? "收起全部设置" : "全部设置（" + schema.length() + " 项）", () -> {
            advanced = !advanced;
            render();
        }, true);
        if (advanced) {
            text("未移植项目前只保存配置，不会在宿主生效，也不会执行原设置的附加操作。", 15);
            EditText query = new EditText(context);
            query.setSingleLine(true);
            query.setHint("搜索设置名称或配置键");
            query.setText(search);
            content.addView(query);
            settingsList = new LinearLayout(context);
            settingsList.setOrientation(LinearLayout.VERTICAL);
            content.addView(settingsList);
            query.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    search = s.toString();
                    renderSettings();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            renderSettings();
        }
        text("基于 BiliRoamingX 1.23.3 · GPL-3.0\n底栏隐藏已通过用户真机验证；其他未移植项仅保存配置。", 12);
        ScrollView rendered = scroll;
        rendered.post(() -> rendered.scrollTo(0, oldScroll));
    }

    private void renderBottom(SharedPreferences preferences, String catalog) {
        try {
            JSONArray tabs = new JSONArray(catalog);
            if (tabs.length() == 0) {
                text("底栏列表会从你的哔哩哔哩自动读取，不使用预先猜测的按钮编号。", 15);
            } else {
                if (!catalog.equals(catalogIdentity)) bottomDirty = false;
                catalogIdentity = catalog;
                if (!bottomDirty || pendingBottom == null) {
                    pendingBottom = new HashSet<>();
                    Set<String> selected = preferences == null ? Collections.singleton(BottomBarPolicy.ALL)
                            : preferences.getStringSet(ModuleConstants.BOTTOM_KEY, Collections.singleton(BottomBarPolicy.ALL));
                    for (int i = 0; i < tabs.length(); i++) {
                        String id = tabs.getJSONObject(i).getString("id");
                        if (BottomBarPolicy.shouldShowing(selected, id)) pendingBottom.add(id);
                    }
                }
                for (int i = 0; i < tabs.length(); i++) {
                    JSONObject tab = tabs.getJSONObject(i);
                    String id = tab.getString("id");
                    CheckBox choice = new CheckBox(context);
                    choice.setText(tab.getString("name"));
                    choice.setTextSize(17);
                    choice.setChecked(pendingBottom.contains(id));
                    choice.setEnabled(preferences != null && !module.busy());
                    choice.setOnCheckedChangeListener((view, checked) -> {
                        bottomDirty = true;
                        if (checked) pendingBottom.add(id);
                        else pendingBottom.remove(id);
                    });
                    content.addView(choice);
                }
                button("保存底栏设置", () -> {
                    if (pendingBottom.isEmpty()) { toast("请至少保留一个底栏按钮。"); return; }
                    Set<String> selected = new HashSet<>(pendingBottom);
                    save(editor -> editor.putStringSet(ModuleConstants.BOTTOM_KEY, selected));
                }, preferences != null && !module.busy());
            }
            button("恢复显示全部底栏", () -> save(editor -> editor.remove(ModuleConstants.BOTTOM_KEY)), preferences != null && !module.busy());
        } catch (JSONException error) {
            android.util.Log.e(ModuleConstants.TAG, "Invalid cached catalog", error);
            text("底栏列表读取失败，请重新启动哔哩哔哩后刷新。", 15);
        }
    }

    private void save(Consumer<SharedPreferences.Editor> change) {
        module.save(change, error -> {
            if (!active || activity.isFinishing() || activity.isDestroyed()) return;
            if (error == null) {
                bottomDirty = false;
                toast("已保存。请彻底关闭并重新打开哔哩哔哩。");
            } else toast(error);
        });
    }

    private void renderSettings() {
        settingsList.removeAllViews();
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < schema.length(); i++) {
            JSONObject definition = schema.optJSONObject(i);
            if (definition == null) continue;
            String key = definition.optString("key");
            String title = definition.optString("title");
            String searchable = (title + " " + key + " " + definition.optString("symbol")).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) continue;
            Button row = new Button(context);
            row.setAllCaps(false);
            row.setText(title + (definition.optBoolean("ported") ? "" : " · 仅保存"));
            row.setEnabled(module.preferences() != null && !module.busy());
            row.setOnClickListener(view -> {
                if (ModuleConstants.BOTTOM_KEY.equals(key)) scroll.smoothScrollTo(0, 0);
                else edit(definition);
            });
            settingsList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private Object value(JSONObject definition) throws JSONException {
        SharedPreferences preferences = module.preferences();
        if (preferences != null && preferences.contains(definition.getString("key"))) {
            return preferences.getAll().get(definition.getString("key"));
        }
        return definition.get("default");
    }

    private String valueText(Object value) throws JSONException {
        if (value instanceof JSONArray array) {
            ArrayList<String> lines = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) lines.add(array.getString(i));
            return android.text.TextUtils.join("\n", lines);
        }
        if (value instanceof Set<?> set) {
            ArrayList<String> lines = new ArrayList<>();
            for (Object entry : set) lines.add(String.valueOf(entry));
            Collections.sort(lines);
            return android.text.TextUtils.join("\n", lines);
        }
        return String.valueOf(value);
    }

    private void edit(JSONObject definition) {
        try {
            String key = definition.getString("key");
            String type = definition.getString("type");
            String hint = definition.optBoolean("ported") ? "" : "尚未移植：此处只保存配置。\n";
            hint += "配置键：" + key;
            if (!definition.isNull("dependency")) hint += "\n依赖设置：" + definition.getString("dependency");
            AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(definition.getString("title"))
                    .setMessage(hint).setNeutralButton("恢复默认", (dialog, which) -> save(editor -> editor.remove(key)))
                    .setNegativeButton("取消", null);
            if ("Boolean".equals(type)) {
                CheckBox toggle = new CheckBox(context);
                toggle.setText("开启");
                toggle.setPadding(dp(24), dp(12), dp(24), dp(12));
                toggle.setChecked(Boolean.TRUE.equals(value(definition)));
                editingDialog = builder.setView(toggle).setPositiveButton("保存", (dialog, which) -> {
                    boolean checked = toggle.isChecked();
                    save(editor -> editor.putBoolean(key, checked));
                }).show();
                return;
            }
            EditText input = new EditText(context);
            input.setText(valueText(value(definition)));
            input.setPadding(dp(24), dp(12), dp(24), dp(12));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            if (key.startsWith("access_key")) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if ("StringSet".equals(type)) input.setHint("每行一个值；留空表示空集合");
            AlertDialog dialog = builder.setView(input).setPositiveButton("保存", null).create();
            editingDialog = dialog;
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String text = input.getText().toString();
                    Consumer<SharedPreferences.Editor> change;
                    switch (type) {
                        case "Int" -> { int number = Integer.parseInt(text.trim()); change = editor -> editor.putInt(key, number); }
                        case "Long" -> { long number = Long.parseLong(text.trim()); change = editor -> editor.putLong(key, number); }
                        case "Float" -> {
                            float number = Float.parseFloat(text.trim());
                            if (!Float.isFinite(number)) throw new IllegalArgumentException("Non-finite number");
                            change = editor -> editor.putFloat(key, number);
                        }
                        case "StringSet" -> {
                            Set<String> values = new HashSet<>();
                            for (String line : text.split("\n", -1)) if (!line.isEmpty()) values.add(line);
                            change = editor -> editor.putStringSet(key, values);
                        }
                        case "String" -> change = editor -> editor.putString(key, text);
                        default -> throw new IllegalArgumentException("Unsupported setting type");
                    }
                    save(change);
                    dialog.dismiss();
                } catch (IllegalArgumentException error) {
                    input.setError("请输入有效的" + ("Float".equals(type) ? "数字" : "整数或配置值"));
                }
            }));
            dialog.show();
        } catch (JSONException error) {
            android.util.Log.e(ModuleConstants.TAG, "Invalid setting definition", error);
            toast("该设置格式错误。");
        }
    }

    private void toast(String message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
}
