package app.revanced.bilibili.xposed;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PlayerRulesTest {
    @Test public void endpointDoesNotAcceptLookalikes() {
        String path = "/bilibili.app.playerunite.v1.Player/PlayViewUnite";
        assertTrue(PlayerPolicy.isUniteEndpoint("https://grpc.biliapi.net" + path));
        for (String url : List.of("http://grpc.biliapi.net" + path,
                "https://grpc.biliapi.net.evil.example" + path,
                "https://user@grpc.biliapi.net" + path,
                "https://grpc.biliapi.net" + path + "/other", "invalid"))
            assertFalse(url, PlayerPolicy.isUniteEndpoint(url));
    }
    @Test public void speedListsPreserveOrderAndNormalSpeed() {
        assertArrayEquals(new float[]{3, 2, 1, .5f}, PlayerPolicy.speeds("3 2 1 0.5", false), 0);
        assertArrayEquals(new float[]{.5f, 1, 2, 3}, PlayerPolicy.speeds("3 2 1 0.5", true), 0);
        assertEquals(0, PlayerPolicy.speeds(" ", false).length);
        for (String value : List.of("2 3", "1 1.0", "1 NaN", "1 Infinity", "1 0", "1 -2"))
            assertThrows(value, IllegalArgumentException.class, () -> PlayerPolicy.speeds(value, false));
    }
    @Test public void rememberedAndLongPressSpeedsHaveSeparateRules() {
        assertEquals(1.5f, PlayerPolicy.defaultSpeed(1, 2, true, 1.5f), 0);
        assertEquals(2, PlayerPolicy.defaultSpeed(1, 2, false, 1.5f), 0);
        assertEquals(1, PlayerPolicy.defaultSpeed(1, Float.NaN, true, -1), 0);
        assertEquals(4, PlayerPolicy.longPressSpeed(3, 4), 0);
        assertEquals(1.5f, PlayerPolicy.longPressSpeed(1.5f, 4), 0);
        assertEquals(2, PlayerPolicy.longPressSpeed(2, Float.NaN), 0);
    }
    @Test public void backupsKeepGoodUrlsAndSkipLive() {
        var backups = List.of("invalid", "https://cn-a.bilivideo.com/video", "https://upos-sz.bilivideo.com/video");
        assertEquals(2, PlayerPolicy.stableBackup("https://1.2.3.4/video", backups));
        assertEquals(-1, PlayerPolicy.stableBackup("https://upos-sz.bilivideo.com/video", backups));
        assertEquals(-1, PlayerPolicy.stableBackup("https://1.2.3.4/live/stream", backups));
    }
    @Test public void settingsRejectInvalidNumbersAndPreserveChineseErrors() {
        assertNull(PlayerSettingsInput.parse("playback_speed_default", "Float", " "));
        assertEquals("FF00AABB", PlayerSettingsInput.parse("subtitle_font_color2", "String", "#ff00aabb"));
        assertEquals(-100, PlayerSettingsInput.parse("subtitle_offset", "Int", "-100"));
        assertEquals(0f, PlayerSettingsInput.parse("subtitle_stroke_width", "Float", "0"));
        assertThrows(IllegalArgumentException.class, () -> PlayerSettingsInput.parse("subtitle_offset", "Int", "10000"));
        assertThrows(IllegalArgumentException.class, () -> PlayerSettingsInput.parse("subtitle_font_color2", "String", "FFFFFF"));
        assertThrows(IllegalArgumentException.class, () -> PlayerSettingsInput.parse("access_key_main", "String", "abc\ndef"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsInput.parse("playback_speed_default", "Float", "NaN")).getMessage().contains("有效数字"));
    }
    @Test public void srtRoundTripPreservesUnicodeAndLines() {
        var cues = SubtitleText.srtOrVtt("\ufeff1\r\n00:00:01,250 --> 00:00:02,500\r\n你好 &amp; world\r\n第二行\r\n\r\n2\r\n00:01:00,000 --> 00:01:01,000\r\n结束");
        assertEquals(2, cues.size());
        assertEquals("你好 & world\n第二行", cues.get(0).content());
        assertEquals(1.25, cues.get(0).from(), 0);
        assertEquals(cues, SubtitleText.srtOrVtt(SubtitleText.srt(cues)));
    }
    @Test public void vttAcceptsSettingsAndTwoPartTimes() {
        var cues = SubtitleText.srtOrVtt("WEBVTT\n\nintro\n00:01.000 --> 00:02.000 align:start\n<v Alice><b>Hello</b> &lt;world&gt;\n");
        assertEquals("Hello <world>", cues.get(0).content());
    }
    @Test public void assMergesLayersAndPreservesCommas() {
        String source = "[Script Info]\nTitle: test\n[Events]\nFormat: Layer, Start, End, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,{\\i1}低层,逗号\\N第二行\n"
                + "Dialogue: 1,0:00:01.00,0:00:02.00,高层\n";
        var cues = SubtitleText.ass(source);
        assertEquals(1, cues.size());
        assertEquals("高层\n低层,逗号\n第二行", cues.get(0).content());
    }
    @Test public void invalidSubtitleTimelinesFail() {
        assertThrows(IllegalArgumentException.class, () -> new SubtitleText.Cue(Double.NaN, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> new SubtitleText.Cue(2, 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> SubtitleText.srtOrVtt("not subtitles"));
        assertThrows(IllegalArgumentException.class, () -> SubtitleText.ass("[Events]\nFormat: Start, End, Text\nDialogue: 1"));
    }
    @Test public void timestampRoundingCarriesToNextMinute() {
        assertTrue(SubtitleText.srt(List.of(new SubtitleText.Cue(59.9996, 60.5, "测试"))).contains("00:01:00,000 --> 00:01:00,500"));
    }
    // Minimal generated-message fixture with private setters and a shared default child.
    public static class Message {
        private static final Message DEFAULT = new Message(0);
        private int value;
        private Message child;
        Message(int value) { this.value = value; }
        public byte[] toByteArray() { return new byte[]{(byte) value, (byte) (child == null ? -1 : child.value)}; }
        public static Message parseFrom(byte[] bytes) {
            Message result = new Message(bytes[0]);
            if (bytes[1] >= 0) result.child = new Message(bytes[1]);
            return result;
        }
        private void setValue(int value) { this.value = value; }
        public boolean hasChild() { return child != null; }
        public Message getChild() { return child == null ? DEFAULT : child; }
        private void setChild(Message value) { child = value; }
    }
    @Test public void protobufEditsDoNotChangeOriginalOrDefault() throws Exception {
        Message original = new Message(1); original.child = new Message(2);
        Message copy = (Message) PlayerProto.edited(original,
                message -> PlayerProto.child(message, "Child", child -> PlayerReflection.call(child, "setValue", 3)));
        assertEquals(2, original.child.value); assertEquals(3, copy.child.value);
        Message absent = new Message(1);
        PlayerProto.child(absent, "Child", child -> PlayerReflection.call(child, "setValue", 9));
        assertFalse(absent.hasChild()); assertEquals(0, Message.DEFAULT.value);
    }
    @Test public void failedEditLeavesOriginalUntouched() {
        Message original = new Message(1);
        assertThrows(NoSuchMethodException.class, () -> PlayerProto.edited(original, copy -> {
            PlayerReflection.call(copy, "setValue", 9); PlayerReflection.call(copy, "missing");
        }));
        assertEquals(1, original.value);
    }
}
