package app.revanced.bilibili.xposed;

import java.util.Locale;

final class PlayerSettingsInput {
    static Object parse(String key, String type, String input) {
        String text = input.trim();
        if (text.isEmpty()) return null;
        if (key.equals("playback_speed_override")) {
            float[] speeds = PlayerPolicy.speeds(text, false); StringBuilder result = new StringBuilder();
            for (float speed : speeds) { if (result.length() > 0) result.append(' '); result.append(speed); }
            return result.toString();
        }
        if (key.equals("subtitle_font_color2") || key.equals("subtitle_stroke_color")) {
            text = text.replaceFirst("^#", "").toUpperCase(Locale.ROOT);
            if (!text.matches("[0-9A-F]{8}")) throw new IllegalArgumentException("请输入 8 位 ARGB 颜色，例如 FFFFFFFF");
            return text;
        }
        if (key.equals("access_key_main")) {
            if (!text.matches("[A-Za-z0-9._-]{1,512}")) throw new IllegalArgumentException("访问密钥格式无效，请检查空格或换行");
            return text;
        }
        if (type.equals("Float")) {
            float value = Float.parseFloat(text);
            if (!Float.isFinite(value)) throw new IllegalArgumentException("请输入有效数字");
            if (key.equals("subtitle_stroke_width")) {
                if (value < 0 || value > 100) throw new IllegalArgumentException("描边宽度范围为 0～100");
            } else if (value <= 0) throw new IllegalArgumentException("倍速必须大于 0；留空恢复默认");
            return value;
        }
        if (type.equals("Int")) {
            int value = Integer.parseInt(text);
            if (key.equals("subtitle_offset")) {
                if (value < -9999 || value > 9999) throw new IllegalArgumentException("偏移范围为 -9999～9999");
            } else if (value < 0 || value > 999) throw new IllegalArgumentException("字号范围为 0～999；0 为默认");
            return value;
        }
        throw new IllegalArgumentException("此设置暂不支持编辑");
    }
    private PlayerSettingsInput() { }
}
