package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

/** Adds a real Preference to the exact 8.27.0 settings fragment, with no resource-ID guesses. */
final class SettingsEntranceHook {
    static final String TARGET = "com.bilibili.app.preferences.BiliPreferencesActivity$BiliPreferencesFragment";
    private static final String KEY = "biliroamingx_lsposed_settings";
    private final ModuleEntry entry;
    private final HostRuntime runtime;
    private Class<?> preference;
    private Constructor<?> rowConstructor;
    private Method getScreen, findPreference, getContext, getActivity, setKey, setTitle, setSummary,
            setOrder, setPersistent, setIconSpace, setListener, addPreference;

    SettingsEntranceHook(ModuleEntry entry, HostRuntime runtime) { this.entry = entry; this.runtime = runtime; }

    void install() {
        try {
            Class<?> fragment = Class.forName(TARGET, false, runtime.hostLoader);
            preference = Class.forName("androidx.preference.Preference", false, runtime.hostLoader);
            Class<?> group = Class.forName("androidx.preference.PreferenceGroup", false, runtime.hostLoader);
            rowConstructor = preference.getConstructor(Context.class);
            getScreen = fragment.getMethod("getPreferenceScreen");
            getContext = fragment.getMethod("getContext");
            getActivity = fragment.getMethod("getActivity");
            findPreference = group.getMethod("findPreference", CharSequence.class);
            addPreference = group.getMethod("addPreference", preference);
            setKey = preference.getMethod("setKey", String.class);
            setTitle = preference.getMethod("setTitle", CharSequence.class);
            setSummary = preference.getMethod("setSummary", CharSequence.class);
            setOrder = preference.getMethod("setOrder", int.class);
            setPersistent = preference.getMethod("setPersistent", boolean.class);
            setIconSpace = preference.getMethod("setIconSpaceReserved", boolean.class);
            // The listener's class name is obfuscated ($d); derive it once from the stable setter.
            for (Method method : preference.getMethods()) {
                if (method.getName().equals("setOnPreferenceClickListener") && method.getParameterCount() == 1) setListener = method;
            }
            if (setListener == null) throw new NoSuchMethodException("setOnPreferenceClickListener");
            Method create = fragment.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
            entry.hook(create).setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try { addEntrance(chain.getThisObject()); }
                        catch (Throwable error) { entry.failure("Settings.entrance", TARGET, "onCreatePreferences", error); }
                        return result;
                    });
            entry.log(Log.INFO, ModuleConstants.TAG, "Settings.entrance: installed onCreatePreferences hook; host=8.27.0/8270400");
        } catch (Throwable error) {
            entry.failure("Settings.entrance", TARGET, "onCreatePreferences", error);
        }
    }

    private void addEntrance(Object fragment) throws ReflectiveOperationException {
        Object screen = getScreen.invoke(fragment);
        if (screen == null) throw new IllegalStateException("Settings screen unavailable");
        if (findPreference.invoke(screen, KEY) != null) return;
        Context context = (Context) getContext.invoke(fragment);
        if (context == null) throw new IllegalStateException("Settings fragment detached");
        Object row = rowConstructor.newInstance(context);
        setKey.invoke(row, KEY);
        setTitle.invoke(row, "哔哩漫游X");
        setSummary.invoke(row, "哔哩漫游X 设置");
        setOrder.invoke(row, -1000);
        setPersistent.invoke(row, false);
        setIconSpace.invoke(row, false);
        Class<?> listener = setListener.getParameterTypes()[0];
        Object callback = Proxy.newProxyInstance(runtime.hostLoader, new Class<?>[]{listener}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> args != null && proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "BiliRoamingX settings click listener";
                    default -> null;
                };
            }
            if (method.getReturnType() == boolean.class && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == preference) {
                try {
                    Activity activity = (Activity) getActivity.invoke(fragment);
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) runtime.openSettings(activity);
                } catch (Throwable error) { entry.failure("Settings.open", TARGET, method.getName(), error); }
                return true;
            }
            throw new UnsupportedOperationException("Unexpected preference listener method");
        });
        setListener.invoke(row, callback);
        Object added = addPreference.invoke(screen, row);
        if (!Boolean.TRUE.equals(added)) throw new IllegalStateException("Settings entrance rejected");
        entry.log(Log.INFO, ModuleConstants.TAG, "Settings.entrance: preference added");
    }
}
