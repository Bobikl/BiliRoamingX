package app.revanced.bilibili.xposed;

import android.app.Activity;
import android.content.Intent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Deliver only our own result codes; host file operations keep their original callbacks. */
final class HostFilePicker {
    private final ModuleEntry entry;
    private final Set<Method> hooked = new HashSet<>();
    private final Map<Activity, Pending> pending = new HashMap<>();
    private int nextCode = 0x6b00;
    private record Pending(int code, Consumer<Intent> callback) { }
    HostFilePicker(ModuleEntry entry) { this.entry = entry; }
    void launch(Activity activity, Intent intent, Consumer<Intent> callback) throws ReflectiveOperationException {
        if (pending.containsKey(activity)) throw new IllegalStateException("请先完成当前文件选择");
        Method target = null;
        for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
            try { target = type.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class); break; }
            catch (NoSuchMethodException ignored) { }
        }
        if (target == null) throw new NoSuchMethodException("Activity.onActivityResult");
        if (hooked.add(target)) entry.hook(target).intercept(chain -> {
            Activity owner = (Activity) chain.getThisObject(); Pending request = pending.get(owner);
            if (request == null || request.code() != (int) chain.getArg(0)) return chain.proceed();
            pending.remove(owner);
            if ((int) chain.getArg(1) == Activity.RESULT_OK) {
                try { request.callback().accept((Intent) chain.getArg(2)); }
                catch (RuntimeException error) {
                    entry.failure("Player.file", owner.getClass().getName(), "result", error);
                    android.widget.Toast.makeText(owner, "文件处理失败，请重新选择", android.widget.Toast.LENGTH_LONG).show();
                }
            }
            return null;
        });
        int code = nextCode++; if (nextCode > 0x6fff) nextCode = 0x6b00;
        pending.put(activity, new Pending(code, callback));
        try { activity.startActivityForResult(intent, code); }
        catch (RuntimeException error) { pending.remove(activity); throw error; }
    }
    void forget(Activity activity) { pending.remove(activity); }
}
