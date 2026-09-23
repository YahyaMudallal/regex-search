package com.sorbonne.utils;

import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.search.PreparedSearch;
import com.sorbonne.search.SearchCursor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileLoaderTest {
    private static final class ChunkReader extends StringReader {
        private final int chunkSize;

        private ChunkReader(String text, int chunkSize) {
            super(text);
            this.chunkSize = chunkSize;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            return super.read(buffer, offset, Math.min(length, chunkSize));
        }
    }

    @Test
    void agreesWithReadLineAcrossEveryKindOfBoundary() throws Exception {
        Random random = new Random(20260923);
        for (String regex : List.of("ab", "a.b", "a*", "ab|é", "😀")) {
            // Java regex compte les points de code pour '.', le projet les char UTF-16.
            String alphabet = regex.contains(".") ? "ab\r\né" : "ab\r\né😀";
            PreparedSearch automaton = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse(regex)));
            Pattern oracle = Pattern.compile(regex, Pattern.DOTALL);
            for (int trial = 0; trial < 50; trial++) {
                StringBuilder text = new StringBuilder();
                for (int i = 0, length = random.nextInt(250); i < length; i++) {
                    text.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                long lines = 0;
                long matches = 0;
                try (BufferedReader reader = new BufferedReader(new StringReader(text.toString()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines++;
                        if (oracle.matcher(line).find()) {
                            matches++;
                        }
                    }
                }
                FileLoader.Counts expected = new FileLoader.Counts(lines, matches);
                for (int chunkSize : new int[] {1, 2, 3, 17, 256}) {
                    assertEquals(expected, FileLoader.count(new ChunkReader(text.toString(), chunkSize),
                            automaton.newCursor()), regex + " / bloc=" + chunkSize);
                    if (regex.equals("ab") || regex.equals("😀")) {
                        assertEquals(expected, FileLoader.count(new ChunkReader(text.toString(), chunkSize),
                                KMPSearch.prepare(regex).newCursor()));
                    }
                }
            }
        }
    }

    @Test
    void carriesMatchesAndCrLfAcrossTheRealBufferBoundary() throws Exception {
        for (int length : new int[] {65_534, 65_535, 65_536, 131_071}) {
            String text = "x".repeat(length) + "ab\r\nab\r\na\nb\r\n\r\nab";
            for (PreparedSearch search : List.of(KMPSearch.prepare("ab"),
                    NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("ab"))))) {
                assertEquals(new FileLoader.Counts(6, 3),
                        FileLoader.count(new StringReader(text), search.newCursor()));
            }
        }
        String crAtBoundary = "x".repeat(65_535) + "\r\n\r\n";
        assertEquals(new FileLoader.Counts(2, 2),
                FileLoader.count(new StringReader(crAtBoundary), KMPSearch.prepare("").newCursor()));
    }

    @Test
    void wildcardKeepsUtf16SemanticsAcrossSurrogateBoundaries() throws Exception {
        for (String regex : List.of("a.b", "a..b", "😀")) {
            PreparedSearch search = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse(regex)));
            long expected = regex.equals("a.b") ? 0 : 1;
            assertEquals(new FileLoader.Counts(1, expected),
                    FileLoader.count(new ChunkReader("a😀b", 1), search.newCursor()));
        }
    }

    @Test
    void cursorsAreIndependentAndKeepSuccessUntilReset() throws Exception {
        for (PreparedSearch search : List.of(KMPSearch.prepare("ababac"),
                NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("ababac"))))) {
            SearchCursor left = search.newCursor();
            SearchCursor right = search.newCursor();
            for (char symbol : "ababababac".toCharArray()) {
                left.accept(symbol);
                right.accept('x');
            }
            assertTrue(left.matches());
            assertFalse(right.matches());
            assertTrue(left.accept('x'));
            left.reset();
            assertFalse(left.matches());
            assertFalse(left.accept('c'));
        }
    }
}
