package app.revanced.bilibili.xposed;

import android.os.Bundle;
import android.util.Log;

import app.revanced.bilibili.runtime.BottomBarPolicy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/** Adapter for the bottom-bar subset of 1.23.3's Json bytecode Patch. */
final class BottomBarHook {
    private static final String JSON = "com.alibaba.fastjson.JSON";
    private static final String ROOT = "tv.danmaku.bili.ui.main2.resource.MainResourceManager$";
    private final ModuleEntry entry;
    private final HostRuntime runtime;
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private Class<?> responseClass;
    private Class<?> generalResponseClass;
    private Field responseData;
    private Field generalData;
    private Field bottom;
    private Field id;
    private Field name;

    BottomBarHook(ModuleEntry entry, HostRuntime runtime) {
        this.entry = entry;
        this.runtime = runtime;
    }

    private Class<?> hostClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, runtime.hostLoader);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Search inherited fields; the terminal error is logged by install().
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    void install() {
        try {
            responseClass = hostClass(ROOT + "TabResponse");
            responseData = field(responseClass, "tabData");
            generalResponseClass = hostClass("com.bilibili.okretro.GeneralResponse");
            generalData = field(generalResponseClass, "data");
            bottom = field(hostClass(ROOT + "TabData"), "bottom");
            id = field(hostClass(ROOT + "Tab"), "tabId");
            name = field(hostClass(ROOT + "Tab"), "name");
            if (!List.class.isAssignableFrom(bottom.getType()) || id.getType() != String.class || name.getType() != String.class) {
                throw new IllegalStateException("Bottom-bar field types do not match verified APK");
            }
            Class<?> parser = hostClass(JSON);
            Class<?> features = Array.newInstance(hostClass("com.alibaba.fastjson.parser.Feature"), 0).getClass();
            Method[] methods = {
                    parser.getDeclaredMethod("parseObject", String.class, Class.class),
                    parser.getDeclaredMethod("parseObject", String.class, Type.class, features),
                    parser.getDeclaredMethod("parseObject", String.class, Type.class, int.class, features)
            };
            int installed = 0;
            for (Method method : methods) {
                try {
                    entry.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        int nesting = depth.get();
                        depth.set(nesting + 1);
                        Object result;
                        try {
                            result = chain.proceed();
                        } finally {
                            if (nesting == 0) depth.remove();
                            else depth.set(nesting);
                        }
                        // Overloads may call one another. Publish the unfiltered catalog once.
                        if (nesting == 0) {
                            try {
                                filter(result);
                            } catch (Throwable error) {
                                entry.failure("Json.BottomBar", JSON, method.toGenericString(), error);
                            }
                        }
                        return result;
                    });
                    installed++;
                } catch (Throwable error) {
                    entry.failure("Json.BottomBar.install", JSON, method.toGenericString(), error);
                }
            }
            entry.log(Log.INFO, ModuleConstants.TAG, "Json.BottomBar: installed " + installed + "/3 parseObject hooks; host=8.27.0/8270400");
            Bundle ready = new Bundle();
            ready.putString("state", installed == 3 ? "ready" : "partial");
            runtime.report(ready, "ready:" + installed);
        } catch (Throwable error) {
            entry.failure("Json.BottomBar.install", JSON + "; " + ROOT, "parseObject / tabData.bottom", error);
        }
    }

    private void filter(Object value) throws IllegalAccessException {
        if (value == null) return;
        Object response = generalResponseClass.isInstance(value) ? generalData.get(value) : value;
        if (!responseClass.isInstance(response)) return;
        Object data = responseData.get(response);
        if (data == null) return;
        Object raw = bottom.get(data);
        if (!(raw instanceof List<?> tabs) || tabs.isEmpty()) return;
        // One immutable framework snapshot keeps selection and its acknowledgement in sync.
        Map<String, ?> snapshot = runtime.preferences.getAll();
        Object configured = snapshot.get(ModuleConstants.BOTTOM_KEY);
        Set<String> showing = new HashSet<>();
        if (configured == null) showing.add(BottomBarPolicy.ALL);
        else if (configured instanceof Set<?> values) {
            for (Object item : values) {
                if (!(item instanceof String text)) throw new IllegalStateException("Invalid bottom selection type");
                showing.add(text);
            }
        } else throw new IllegalStateException("Bottom selection must be a string set");
        long revision = snapshot.get(ModuleConstants.REVISION) instanceof Long number ? number : 0;
        ArrayList<Object> retained = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (Object tab : tabs) {
            String tabId = (String) id.get(tab);
            if (tabId == null || tabId.isEmpty()) throw new IllegalStateException("Navigation tab has no stable ID");
            String label = (String) name.get(tab);
            ids.add(tabId);
            names.add(label == null || label.isEmpty() ? tabId : label);
            if (BottomBarPolicy.shouldShowing(showing, tabId)) retained.add(tab);
        }
        // An obsolete or manually edited ID set must not leave the host without navigation.
        boolean mismatch = retained.isEmpty();
        if (mismatch) {
            entry.failure("Json.BottomBar.selection", ROOT + "TabData", "bottom",
                    new IllegalStateException("Selection matches no current tabs; keeping original navigation"));
            retained.addAll(tabs);
        }
        Bundle report = new Bundle();
        report.putString("state", mismatch ? "selection_mismatch" : "applied");
        report.putStringArrayList("ids", ids);
        report.putStringArrayList("names", names);
        report.putLong("revision", revision);
        report.putInt("before", tabs.size());
        report.putInt("after", retained.size());
        // Commit the list only after all reflection and selection work succeeded.
        if (retained.size() != tabs.size()) bottom.set(data, retained);
        runtime.report(report, ids + ":" + names + ":" + revision + ":" + retained.size() + ":" + mismatch);
        runtime.debug("Json.BottomBar: revision=" + revision + ", visible=" + retained.size() + "/" + tabs.size());
    }
}
