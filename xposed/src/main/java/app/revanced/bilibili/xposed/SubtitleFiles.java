package app.revanced.bilibili.xposed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Bounded UTF-8 subtitle file and HTTP reads. Automatic translation is deferred. */
final class SubtitleFiles {
    static final int LIMIT = 8 * 1024 * 1024;
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36";
    static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static byte[] read(InputStream stream, int maximum) throws IOException {
        if (stream == null) throw new IOException("文件无法读取");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > maximum) throw new IOException("文件超过大小限制");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    static String fetch(String url) throws IOException {
        URL address = new URL(url);
        if (!address.getProtocol().equals("https") && !address.getProtocol().equals("http")) throw new IOException("不支持的字幕地址");
        HttpURLConnection connection = (HttpURLConnection) address.openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("服务返回 HTTP " + status);
            try (InputStream raw = connection.getInputStream();
                 InputStream stream = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
                return utf8(read(stream, LIMIT));
            }
        } finally { connection.disconnect(); }
    }
}
