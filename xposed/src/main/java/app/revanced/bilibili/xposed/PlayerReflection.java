package app.revanced.bilibili.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reflection helpers shared by the verified player adapters. */
final class PlayerReflection {
    private static final Map<String, Field> fields = new ConcurrentHashMap<>();
    static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        String key = type.getName() + ":" + name;
        Field cached = fields.get(key); if (cached != null) return cached;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field found = current.getDeclaredField(name); found.setAccessible(true); fields.put(key, found); return found; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(key);
    }
    static Object get(Object object, String name) throws ReflectiveOperationException { return field(object.getClass(), name).get(object); }
    static void set(Object object, String name, Object value) throws ReflectiveOperationException { field(object.getClass(), name).set(object, value); }
    static Object call(Object object, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> current = object.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] types = method.getParameterTypes(); boolean match = true;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) { match &= !types[i].isPrimitive(); continue; }
                    Class<?> boxed = types[i] == int.class ? Integer.class : types[i] == boolean.class ? Boolean.class
                            : types[i] == float.class ? Float.class : types[i] == long.class ? Long.class : types[i];
                    match &= boxed.isInstance(args[i]);
                }
                if (match) { method.setAccessible(true); return method.invoke(object, args); }
            }
        }
        throw new NoSuchMethodException(object.getClass().getName() + "." + name);
    }
}
