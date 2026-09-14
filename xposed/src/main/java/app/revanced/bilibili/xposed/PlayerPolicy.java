package app.revanced.bilibili.xposed;

import java.net.URI;
import java.util.List;

/** Device-independent player choices from the original integrations. */
final class PlayerPolicy {
    static boolean isUniteEndpoint(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && java.util.Set.of("grpc.biliapi.net", "grpc.biliapi.com", "grpc.bilibili.com").contains(uri.getHost())
                    && "/bilibili.app.playerunite.v1.Player/PlayViewUnite".equals(uri.getPath());
        } catch (RuntimeException error) { return false; }
    }
    static float[] speeds(String value, boolean reverse) {
        if (value.trim().isEmpty()) return new float[0];
        String[] parts = value.trim().split("\\s+");
        if (parts.length > 64) throw new IllegalArgumentException("最多支持 64 个倍速选项");
        float[] result = new float[parts.length]; boolean normal = false;
        java.util.Set<Float> seen = new java.util.HashSet<>();
        for (int i = 0; i < parts.length; i++) {
            float speed = Float.parseFloat(parts[i]);
            if (!Float.isFinite(speed) || speed <= 0 || !seen.add(speed)) throw new IllegalArgumentException("倍速必须为不重复的正数");
            normal |= speed == 1f; result[reverse ? parts.length - 1 - i : i] = speed;
        }
        if (!normal) throw new IllegalArgumentException("倍速列表必须包含 1 倍速");
        return result;
    }
    static float defaultSpeed(float original, float configured, boolean remember, float selected) {
        if (remember && Float.isFinite(selected) && selected > 0) return selected;
        return Float.isFinite(configured) && configured > 0 ? configured : original;
    }
    static float longPressSpeed(float original, float configured) {
        return (original == 2f || original == 3f) && configured > 0 && Float.isFinite(configured) ? configured : original;
    }
    static int cdnRank(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return -1;
            String authority = uri.getRawAuthority(); if (authority == null) return -1;
            if (url.contains("szbdyd.com") || url.contains(".mcdn.bilivideo") || url.matches("^https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) return 3;
            if (url.matches("^https?://cn-.*\\.bilivideo.*")) return 2;
            if (authority.contains("oss")) return 1;
            return 0;
        } catch (RuntimeException error) { return -1; }
    }
    static int stableBackup(String original, List<String> backups) {
        int originalRank = cdnRank(original); if (originalRank <= 0) return -1;
        try { if (String.valueOf(URI.create(original).getRawPath()).contains("live")) return -1; }
        catch (RuntimeException error) { return -1; }
        int best = -1, rank = 3;
        for (int i = 0; i < backups.size(); i++) {
            int candidate = cdnRank(backups.get(i));
            if (candidate >= 0 && candidate < rank) { rank = candidate; best = i; }
        }
        return best;
    }
}
