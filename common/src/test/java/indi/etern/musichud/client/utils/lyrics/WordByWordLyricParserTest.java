package indi.etern.musichud.client.utils.lyrics;

import indi.etern.musichud.client.ui.dto.LyricLine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordByWordLyricParserTest {
    @Test
    void parsesQqPostfixTimestampsWithoutDroppingFirstCharacter() {
        WordByWordLyricParser.LyricLineMetaData line = parseSingleLine(
                "[25486,4481]爱(25486,400)像(25886,385)是(26271,495)一(26766,465)场(27231,448)小(27679,631)雨(28310,1657)");

        assertEquals("爱像是一场小雨", line.lyric());
        assertEquals(1, line.phraseEndingOffsetMap().get(Duration.ofMillis(25886)));
        assertEquals(4, line.phraseEndingOffsetMap().get(Duration.ofMillis(27231)));
        assertEquals(7, line.phraseEndingOffsetMap().get(Duration.ofMillis(29967)));
    }

    @Test
    void keepsNeteaseYrcPrefixTimestamps() {
        WordByWordLyricParser.LyricLineMetaData line = parseSingleLine(
                "[21590,1190](21590,380,0)素(21970,230,0)胚(22200,280,0)勾(22480,300,0)勒");

        assertEquals("素胚勾勒", line.lyric());
        assertEquals(1, line.phraseEndingOffsetMap().get(Duration.ofMillis(21970)));
        assertEquals(4, line.phraseEndingOffsetMap().get(Duration.ofMillis(22780)));
    }

    @Test
    void keepsTwoFieldPrefixTimestamps() {
        WordByWordLyricParser.LyricLineMetaData line = parseSingleLine(
                "[0,1000](0,500)逐字(500,500)歌词");

        assertEquals("逐字歌词", line.lyric());
        assertEquals(2, line.phraseEndingOffsetMap().get(Duration.ofMillis(500)));
        assertEquals(4, line.phraseEndingOffsetMap().get(Duration.ofMillis(1000)));
    }

    @Test
    void expandsMultipleLineTimestampsAndShiftsPhraseTiming() {
        List<WordByWordLyricParser.LyricLineMetaData> lines = parseLines(
                "[0,1000][2000,1000](0,500)逐字(500,500)歌词");

        assertEquals(2, lines.size());
        assertEquals(Duration.ZERO, lines.get(0).startTime());
        assertEquals(Duration.ofMillis(2000), lines.get(1).startTime());
        assertEquals("逐字歌词", lines.get(1).lyric());
        assertEquals(2, lines.get(1).phraseEndingOffsetMap().get(Duration.ofMillis(2500)));
        assertEquals(4, lines.get(1).phraseEndingOffsetMap().get(Duration.ofMillis(3000)));
    }

    private static WordByWordLyricParser.LyricLineMetaData parseSingleLine(String lyric) {
        List<WordByWordLyricParser.LyricLineMetaData> lines = parseLines(lyric);
        assertEquals(1, lines.size());
        return lines.getFirst();
    }

    private static List<WordByWordLyricParser.LyricLineMetaData> parseLines(String lyric) {
        List<WordByWordLyricParser.LyricLineMetaData> lines = new ArrayList<>();
        WordByWordLyricParser.matchLine(lyric, line -> {
            if (line.type() == LyricLine.Type.NORMAL) {
                lines.add(line);
            }
        });
        return lines;
    }
}
