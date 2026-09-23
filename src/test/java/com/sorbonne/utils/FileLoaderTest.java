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
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    @Test
    void pathFastUtf8ScannerMatchesBufferedReaderIncludingBufferBoundaries() throws Exception {
        String text = "x".repeat(65_535) + "😀ab\r\n"
                + "préfixe 東京 suffixe\n"
                + "a😀b\r"
                + "ab";
        Path file = Files.createTempFile("regex-search-fast-utf8", ".txt");
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
            for (String regex : List.of("ab", "a.b", "a..b", "😀", "東京", "é", ".*ab")) {
                PreparedSearch search = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse(regex)));
                FileLoader.Counts expected;
                try (BufferedReader reader = FileLoader.open(file)) {
                    long lines = 0;
                    long matches = 0;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines++;
                        if (search.search(line)) {
                            matches++;
                        }
                    }
                    expected = new FileLoader.Counts(lines, matches);
                }
                assertEquals(expected, FileLoader.count(file, search.newCursor()), regex);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void pathFastUtf8ScannerRejectsMalformedSequencesEvenAfterAMatch() throws Exception {
        byte[][] invalid = {
                {(byte) 0x80},
                {(byte) 0xc0, (byte) 0x80},
                {(byte) 0xe0, (byte) 0x80, (byte) 0x80},
                {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                {(byte) 0xf0, (byte) 0x9f}
        };
        for (int i = 0; i < invalid.length; i++) {
            Path file = Files.createTempFile("regex-search-invalid-utf8-" + i, ".txt");
            try {
                byte[] prefix = "a\n".getBytes(StandardCharsets.UTF_8);
                byte[] content = new byte[prefix.length + invalid[i].length];
                System.arraycopy(prefix, 0, content, 0, prefix.length);
                System.arraycopy(invalid[i], 0, content, prefix.length, invalid[i].length);
                Files.write(file, content);
                assertThrows(MalformedInputException.class,
                        () -> FileLoader.count(file, KMPSearch.prepare("a").newCursor()), "cas=" + i);
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

}
