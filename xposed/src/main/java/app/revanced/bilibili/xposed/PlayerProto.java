package app.revanced.bilibili.xposed;

/** Copy-on-edit: never mutate host replies or protobuf singleton default instances. */
final class PlayerProto {
    @FunctionalInterface interface Edit { void apply(Object message) throws ReflectiveOperationException; }
    static Object copy(Object message) throws ReflectiveOperationException {
        byte[] bytes = (byte[]) PlayerReflection.call(message, "toByteArray");
        return message.getClass().getDeclaredMethod("parseFrom", byte[].class).invoke(null, (Object) bytes);
    }
    static Object edited(Object original, Edit edit) throws ReflectiveOperationException {
        Object result = copy(original); edit.apply(result); return result;
    }
    static void child(Object parent, String suffix, Edit edit) throws ReflectiveOperationException {
        if (!(boolean) PlayerReflection.call(parent, "has" + suffix)) return;
        Object child = edited(PlayerReflection.call(parent, "get" + suffix), edit);
        PlayerReflection.call(parent, "set" + suffix, child);
    }
    private PlayerProto() { }
}
