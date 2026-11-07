package dev.mclite.island.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {

    @Test
    void parsesMultipleTagsPerLine() {
        var doc = LrcParser.parse("""
                [ti:测试]
                [ar:歌手]
                [00:01.50][00:03.000]第一句
                [00:05]第二句
                """);
        assertEquals("测试", doc.title());
        assertEquals("歌手", doc.artist());
        assertEquals(3, doc.lines().size());
        assertEquals(1500L, doc.lines().get(0).timeMs());
        assertEquals(3000L, doc.lines().get(1).timeMs());
        assertEquals(5000L, doc.lines().get(2).timeMs());
    }

    @Test
    void appliesOffsetAndSorts() {
        var doc = LrcParser.parse("""
                [offset:200]
                [00:02.00]B
                [00:01.00]A
                """);
        assertEquals(800L, doc.lines().get(0).timeMs());
        assertEquals(1800L, doc.lines().get(1).timeMs());
        assertEquals("A", doc.lines().get(0).text());
    }

    @Test
    void skipsMalformedLines() {
        var doc = LrcParser.parse("[00:xx]坏\n没有时间\n[00:01.00]好");
        assertEquals(1, doc.lines().size());
        assertEquals("好", doc.lines().get(0).text());
    }

    @Test
    void readsGbkFallback(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gbk.lrc");
        Files.write(file, "[00:01.00]中文歌词".getBytes(Charset.forName("GBK")));
        assertEquals("中文歌词", LrcParser.readDocument(file).lines().get(0).text());
    }

    @Test
    void findsSidecarNextToAudioOrInLyricsDir(@TempDir Path dir) throws Exception {
        Path audio = dir.resolve("song.mp3");
        Files.writeString(audio, "x");
        Path lyricsDir = dir.resolve("lyrics");
        Files.createDirectories(lyricsDir);

        assertTrue(LrcParser.findSidecar(new Track(audio, "song", ""), lyricsDir) == null);

        Path lrc = lyricsDir.resolve("song.lrc");
        Files.writeString(lrc, "[00:00]hi");
        assertEquals(lrc, LrcParser.findSidecar(new Track(audio, "song", ""), lyricsDir));

        // 两边都有时，导入到 lyrics 目录的版本优先
        Path sidecar = dir.resolve("song.lrc");
        Files.writeString(sidecar, "[00:00]hi");
        assertEquals(lrc, LrcParser.findSidecar(new Track(audio, "song", ""), lyricsDir));

        Files.delete(lrc);
        assertEquals(sidecar, LrcParser.findSidecar(new Track(audio, "song", ""), lyricsDir));
    }
}
