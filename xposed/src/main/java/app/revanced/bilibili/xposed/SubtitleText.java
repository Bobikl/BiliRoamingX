package app.revanced.bilibili.xposed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Format parsing independent of Android, exercised by local fixture tests. */
final class SubtitleText {
    record Cue(double from, double to, String content) {
        Cue {
            if (!Double.isFinite(from) || !Double.isFinite(to) || from < 0 || to < from || content == null)
                throw new IllegalArgumentException("字幕时间轴无效");
        }
    }
    private static final Pattern TIMELINE = Pattern.compile("((?:\\d{1,4}:)?\\d{2}:\\d{2}[.,]\\d{2,3})\\s*-->\\s*((?:\\d{1,4}:)?\\d{2}:\\d{2}[.,]\\d{2,3})");
    static double seconds(String value) {
        String[] parts = value.trim().replace(',', '.').split(":");
        if (parts.length < 2 || parts.length > 3) throw new IllegalArgumentException("字幕时间格式无效");
        double result = 0;
        for (String part : parts) result = result * 60 + Double.parseDouble(part);
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException("字幕时间无效");
        return result;
    }
    static List<Cue> srtOrVtt(String source) {
        List<Cue> result = new ArrayList<>(); StringBuilder text = new StringBuilder(); double from = 0, to = 0; boolean active = false;
        for (String line : (source.replace("\ufeff", "") + "\n\n").split("\\r?\\n", -1)) {
            var match = TIMELINE.matcher(line);
            if (match.find()) {
                if (active && text.length() > 0) result.add(new Cue(from, to, clean(text.toString())));
                text.setLength(0); from = seconds(match.group(1)); to = seconds(match.group(2)); active = true;
            } else if (active && line.isBlank()) {
                if (text.length() > 0) result.add(new Cue(from, to, clean(text.toString())));
                text.setLength(0); active = false;
            } else if (active) { if (text.length() > 0) text.append('\n'); text.append(line); }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("文件中没有有效字幕");
        return result;
    }
    static List<Cue> ass(String source) {
        record Line(int layer, Cue cue) { }
        List<Line> lines = new ArrayList<>(); String[] format = null; boolean events = false;
        for (String raw : source.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("[")) { events = line.equalsIgnoreCase("[Events]"); continue; }
            if (!events) continue;
            if (line.startsWith("Format:")) format = line.substring(7).trim().split("\\s*,\\s*");
            else if (line.startsWith("Dialogue:") && format != null) {
                String[] values = line.substring(9).trim().split(",", format.length);
                if (values.length != format.length) throw new IllegalArgumentException("ASS 字幕行不完整");
                String start = null, end = null, text = null; int layer = 0;
                for (int i = 0; i < format.length; i++) switch (format[i].toLowerCase(Locale.ROOT)) {
                    case "layer" -> layer = Integer.parseInt(values[i].trim());
                    case "start" -> start = values[i]; case "end" -> end = values[i]; case "text" -> text = values[i];
                }
                if (start == null || end == null || text == null) throw new IllegalArgumentException("ASS 缺少时间或正文列");
                lines.add(new Line(layer, new Cue(seconds(start), seconds(end), clean(text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")))));
            }
        }
        lines.sort(Comparator.comparingDouble((Line l) -> l.cue.from()).thenComparingDouble(l -> l.cue.to()).thenComparing(Comparator.comparingInt(Line::layer).reversed()));
        List<Cue> result = new ArrayList<>();
        for (Line line : lines) {
            Cue cue = line.cue();
            if (!result.isEmpty()) {
                Cue previous = result.get(result.size() - 1);
                if (previous.from() == cue.from() && previous.to() == cue.to()) {
                    result.set(result.size() - 1, new Cue(cue.from(), cue.to(), previous.content() + "\n" + cue.content())); continue;
                }
            }
            result.add(cue);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("文件中没有有效 ASS 字幕");
        return result;
    }
    private static String clean(String text) {
        return text.replaceAll("\\{[^}]*}", "").replaceAll("<[^>]*>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&nbsp;", " ").replace("&lrm;", "").replace("&rlm;", "");
    }
    static String srt(List<Cue> cues) {
        StringBuilder result = new StringBuilder(); int index = 1;
        for (Cue cue : cues) result.append(index++).append('\n').append(time(cue.from())).append(" --> ").append(time(cue.to()))
                .append('\n').append(cue.content().trim()).append("\n\n");
        return result.toString();
    }
    private static String time(double seconds) {
        long milliseconds = Math.round(seconds * 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", milliseconds / 3600000, milliseconds / 60000 % 60, milliseconds / 1000 % 60, milliseconds % 1000);
    }
}
