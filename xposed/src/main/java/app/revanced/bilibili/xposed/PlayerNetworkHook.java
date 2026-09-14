package app.revanced.bilibili.xposed;

import android.util.Base64;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;

final class PlayerNetworkHook extends PlayerHookSupport {
    PlayerNetworkHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        installPart("network", () -> {
            Class<?> type = host("org.chromium.net.impl.BidirectionalStreamBuilderImpl");
            var url = PlayerReflection.field(type, "mUrl"); var headers = PlayerReflection.field(type, "mRequestHeaders");
            for (var method : type.getDeclaredMethods()) {
                if (!method.getName().equals("build") || method.isBridge() || method.getParameterCount() != 0) continue;
                entry.hook(method).intercept(chain -> {
                    Object builder = chain.getThisObject(); Object original = null; boolean replaced = false;
                    synchronized (builder) {
                        try {
                            String target = (String) url.get(builder);
                            if (PlayerPolicy.isUniteEndpoint(target)) {
                                String access = text("access_key_main", "").trim();
                                boolean trial = enabled("trial_vip_quality") && PlayerAccount.knownNonVip(runtime);
                                if (!access.isEmpty() || trial) {
                                    original = headers.get(builder);
                                    if (!(original instanceof ArrayList<?> list)) throw new IllegalStateException("Unexpected header list");
                                    ArrayList<Map.Entry<String, String>> changed = new ArrayList<>(); boolean authorization = false;
                                    for (Object raw : list) {
                                        if (!(raw instanceof Map.Entry<?, ?> item) || !(item.getKey() instanceof String key) || !(item.getValue() instanceof String value))
                                            throw new IllegalStateException("Unexpected header type");
                                        if (!access.isEmpty() && key.equalsIgnoreCase("authorization")) { value = "identify_v1 " + access; authorization = true; }
                                        if (trial && key.equalsIgnoreCase("x-bili-network-bin")) {
                                            Class<?> network = host("com.bapis.bilibili.metadata.network.Network");
                                            Object message = network.getDeclaredMethod("parseFrom", byte[].class).invoke(null, (Object) Base64.decode(value, Base64.DEFAULT));
                                            PlayerReflection.call(message, "setType", host("com.bapis.bilibili.metadata.network.NetworkType").getField("WIFI").get(null));
                                            value = Base64.encodeToString((byte[]) PlayerReflection.call(message, "toByteArray"), Base64.NO_WRAP);
                                        }
                                        changed.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
                                    }
                                    if (!access.isEmpty() && !authorization) changed.add(new AbstractMap.SimpleImmutableEntry<>("authorization", "identify_v1 " + access));
                                    headers.set(builder, changed); replaced = true;
                                }
                            }
                        } catch (Throwable error) {
                            // Never include request headers or credentials in logs.
                            entry.failure("Player.network", type.getName(), "prepare headers", new IllegalStateException(error.getClass().getSimpleName()));
                        }
                        try { return chain.proceed(); }
                        finally {
                            if (replaced) try { headers.set(builder, original); }
                            catch (Throwable error) { entry.failure("Player.network", type.getName(), "restore headers", new IllegalStateException(error.getClass().getSimpleName())); }
                        }
                    }
                });
            }
        });
    }
}
