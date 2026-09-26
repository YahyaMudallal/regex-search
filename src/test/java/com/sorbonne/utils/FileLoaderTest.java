package com.sorbonne.utils;

import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.search.PreparedSearch;
import com.sorbonne.search.SearchCursor;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileLoaderTest {
    private static final class ChunkInputStream extends ByteArrayInputStream {
        private final int chunkSize;

        private ChunkInputStream(byte[] data, int chunkSize) {
            super(data);
            this.chunkSize = chunkSize;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return super.read(buffer, offset, Math.min(length, chunkSize));
        }
    }

    @Test
    void agreesWithByteOracleAcrossEveryKindOfBoundary() throws Exception {
        Random random = new Random(20260923);
        for (String regex : List.of("ab", "a.b", "a*", "ab|xy", ".")) {
            PreparedSearch search = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse(regex)));
            for (int trial = 0; trial < 50; trial++) {
                byte[] text = new byte[random.nextInt(250)];
                int[] alphabet = {'a', 'b', 'x', 'y', 0x80, 0xff, '\r', '\n'};
                for (int i = 0; i < text.length; i++) {
                    text[i] = (byte) alphabet[random.nextInt(alphabet.length)];
                }
                FileLoader.Counts expected = oracle(text, search);
                for (int chunkSize : new int[] {1, 2, 3, 17, 64}) {
                    assertEquals(expected, FileLoader.count(new ChunkInputStream(text, chunkSize),
                            search.newCursor()), regex + " / bloc=" + chunkSize);
                }
            }
        }
    }

    @Test
    void carriesMatchesAndCrLfAcrossTheRealBufferBoundary() throws Exception {
        for (int length : new int[] {65_534, 65_535, 65_536, 131_071}) {
            byte[] prefix = new byte[length];
            Arrays.fill(prefix, (byte) 'x');
            byte[] suffix = "ab\r\nab\r\na\nb\r\n\r\nab".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] text = new byte[prefix.length + suffix.length];
            System.arraycopy(prefix, 0, text, 0, prefix.length);
            System.arraycopy(suffix, 0, text, prefix.length, suffix.length);
            for (PreparedSearch search : List.of(KMPSearch.prepare("ab"),
                    NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("ab"))))) {
                assertEquals(new FileLoader.Counts(6, 3),
                        FileLoader.count(new ChunkInputStream(text, 65_536), search.newCursor()));
            }
        }
    }

    @Test
    void wildcardConsumesExactlyOneByte() throws Exception {
        byte[] text = {(byte) 'a', (byte) 0xe9, (byte) 'b'};
        PreparedSearch one = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("a.b")));
        PreparedSearch two = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("a..b")));
        assertTrue(one.search(text));
        assertFalse(two.search(text));
    }

    @Test
    void acceptsAllByteValuesWithoutConversion() throws Exception {
        byte[] all = new byte[256];
        for (int i = 0; i < all.length; i++) {
            all[i] = (byte) i;
        }
        PreparedSearch any = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse(".")));
        assertTrue(any.search(all));
        SearchCursor cursor = any.newCursor();
        for (int symbol = 0; symbol < 256; symbol++) {
            cursor.reset();
            assertTrue(cursor.accept(symbol), "symbole=" + symbol);
        }
    }

    @Test
    void cursorsAreIndependentAndKeepSuccessUntilReset() throws Exception {
        for (PreparedSearch search : List.of(KMPSearch.prepare("ababac"),
                NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("ababac"))))) {
            SearchCursor left = search.newCursor();
            SearchCursor right = search.newCursor();
            for (byte symbol : "ababababac".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) {
                left.accept(symbol & 0xff);
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
    void pathScannerAndRawLineOutputPreserveBytes() throws Exception {
        byte[] content = {(byte) 'a', (byte) 0xff, (byte) 'b', '\r', '\n',
                (byte) 0x80, (byte) 'a', (byte) 'b', '\n', (byte) 'x'};
        Path file = Files.createTempFile("regex-search-bytes", ".txt");
        try {
            Files.write(file, content);
            PreparedSearch search = NativeSearch.prepareNfa(NFA.buildNFA(RegexParser.parse("ab")));
            assertEquals(new FileLoader.Counts(3, 1), FileLoader.count(file, search.newCursor()));
            List<byte[]> lines = new ArrayList<>();
            FileLoader.Counts counts = FileLoader.forEachMatchingLine(file, search.newCursor(),
                    (lineNumber, line, length) -> lines.add(Arrays.copyOf(line, length)));
            assertEquals(new FileLoader.Counts(3, 1), counts);
            assertEquals(1, lines.size());
            assertArrayEquals(new byte[] {(byte) 0x80, 'a', 'b'}, lines.get(0));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static FileLoader.Counts oracle(byte[] text, PreparedSearch search) {
        long lines = 0;
        long matches = 0;
        int start = 0;
        int i = 0;
        while (i < text.length) {
            if (text[i] == '\r' || text[i] == '\n') {
                byte[] line = Arrays.copyOfRange(text, start, i);
                lines++;
                if (search.search(line)) {
                    matches++;
                }
                if (text[i] == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                    i++;
                }
                start = i + 1;
            }
            i++;
        }
        if (start < text.length) {
            byte[] line = Arrays.copyOfRange(text, start, text.length);
            lines++;
            if (search.search(line)) {
                matches++;
            }
        }
        return new FileLoader.Counts(lines, matches);
    }
}
