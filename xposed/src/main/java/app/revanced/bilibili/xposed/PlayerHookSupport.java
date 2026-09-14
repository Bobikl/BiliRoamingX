package app.revanced.bilibili.xposed;

import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

abstract class PlayerHookSupport {
    final ModuleEntry entry;
    final HostRuntime runtime;
    PlayerHookSupport(ModuleEntry entry, HostRuntime runtime) { this.entry = entry; this.runtime = runtime; }
    Class<?> host(String name) throws ClassNotFoundException { return Class.forName(name, false, runtime.hostLoader); }
    boolean enabled(String key) { return runtime.preferences.getBoolean(key, false); }
    String text(String key, String fallback) { return runtime.preferences.getString(key, fallback); }
    @FunctionalInterface interface Work { void run() throws Throwable; }
    void installPart(String name, Work work) {
        try { work.run(); entry.log(Log.INFO, ModuleConstants.TAG, "Player." + name + ": installed"); }
        catch (Throwable error) { entry.failure("Player." + name, "8.27.0 player", "install", error); }
    }
    @FunctionalInterface interface Result { Object apply(XposedInterface.Chain chain, Object result) throws Throwable; }
    void after(Method method, String name, Result filter) {
        entry.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            try { return filter.apply(chain, result); }
            catch (Throwable error) { entry.failure("Player." + name, method.getDeclaringClass().getName(), method.getName(), error); return result; }
        });
    }
}
