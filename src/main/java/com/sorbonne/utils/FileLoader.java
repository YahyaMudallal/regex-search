package com.sorbonne.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;

import com.sorbonne.search.SearchCursor;

/** Lecture bufferisee des fichiers comme suites d'octets, sans conversion de caracteres. */
public final class FileLoader {
    private static final int BUFFER_SIZE = 64 * 1024;

    private FileLoader() {
    }

    /** Compteurs sans stockage du contenu des lignes. */
    public record Counts(long totalLines, long matchingLines) {
    }

    /** Consommateur d'une ligne brute, sans son separateur. */
    @FunctionalInterface
    public interface MatchingLineConsumer {
        void accept(long lineNumber, byte[] line, int length) throws IOException;
    }

    /**
     * Parcourt directement les octets du fichier. LF, CR et CRLF delimitent les lignes.
     * Le moteur recoit des blocs contigus sans copie et le cout hors recherche reste O(C + L).
     */
    public static Counts count(Path file, SearchCursor cursor) throws IOException {
        Objects.requireNonNull(file, "Le fichier ne doit pas etre nul");
        Objects.requireNonNull(cursor, "Le curseur ne doit pas etre nul");
        requireRegularFile(file);
        try (InputStream input = Files.newInputStream(file)) {
            return count(input, cursor);
        }
    }

    /** Variante testable sur un flux deja ouvert ; le flux reste ouvert. */
    static Counts count(InputStream input, SearchCursor cursor) throws IOException {
        Objects.requireNonNull(input, "Le flux ne doit pas etre nul");
        Objects.requireNonNull(cursor, "Le curseur ne doit pas etre nul");
        cursor.reset();
        byte[] buffer = new byte[BUFFER_SIZE];
        long lines = 0;
        long matching = 0;
        boolean hasContent = false;
        boolean afterCR = false;
        boolean found = cursor.matches();

        int length;
        while ((length = input.read(buffer)) != -1) {
            int index = 0;
            if (afterCR) {
                if (length > 0 && buffer[0] == '\n') {
                    index = 1;
                }
                afterCR = false;
            }

            while (index < length) {
                int start = index;
                while (index < length && buffer[index] != '\r' && buffer[index] != '\n') {
                    index++;
                }
                int runLength = index - start;
                if (runLength > 0) {
                    hasContent = true;
                    if (!found) {
                        found = cursor.accept(buffer, start, runLength);
                    }
                }
                if (index == length) {
                    break;
                }

                byte separator = buffer[index++];
                lines++;
                if (found) {
                    matching++;
                }
                hasContent = false;
                cursor.reset();
                found = cursor.matches();

                if (separator == '\r') {
                    if (index < length && buffer[index] == '\n') {
                        index++;
                    } else if (index == length) {
                        afterCR = true;
                    }
                }
            }
        }

        if (hasContent) {
            lines++;
            if (found) {
                matching++;
            }
        }
        return new Counts(lines, matching);
    }

    /**
     * Meme parcours que {@link #count(Path, SearchCursor)}, mais conserve uniquement
     * la ligne courante pour pouvoir restituer exactement ses octets lorsqu'elle correspond.
     */
    public static Counts forEachMatchingLine(Path file, SearchCursor cursor,
            MatchingLineConsumer consumer) throws IOException {
        Objects.requireNonNull(file, "Le fichier ne doit pas etre nul");
        Objects.requireNonNull(cursor, "Le curseur ne doit pas etre nul");
        Objects.requireNonNull(consumer, "Le consommateur ne doit pas etre nul");
        requireRegularFile(file);

        cursor.reset();
        byte[] inputBuffer = new byte[BUFFER_SIZE];
        byte[] line = new byte[256];
        int lineLength = 0;
        long lines = 0;
        long matching = 0;
        boolean hasContent = false;
        boolean afterCR = false;
        boolean found = cursor.matches();

        try (InputStream input = Files.newInputStream(file)) {
            int length;
            while ((length = input.read(inputBuffer)) != -1) {
                int index = 0;
                if (afterCR) {
                    if (length > 0 && inputBuffer[0] == '\n') {
                        index = 1;
                    }
                    afterCR = false;
                }

                while (index < length) {
                    int start = index;
                    while (index < length && inputBuffer[index] != '\r' && inputBuffer[index] != '\n') {
                        index++;
                    }
                    int runLength = index - start;
                    if (runLength > 0) {
                        hasContent = true;
                        int needed = lineLength + runLength;
                        if (needed > line.length) {
                            int capacity = line.length;
                            while (capacity < needed) {
                                capacity = Math.max(capacity << 1, needed);
                            }
                            line = Arrays.copyOf(line, capacity);
                        }
                        System.arraycopy(inputBuffer, start, line, lineLength, runLength);
                        lineLength = needed;
                        if (!found) {
                            found = cursor.accept(inputBuffer, start, runLength);
                        }
                    }
                    if (index == length) {
                        break;
                    }

                    byte separator = inputBuffer[index++];
                    lines++;
                    if (found) {
                        matching++;
                        consumer.accept(lines, line, lineLength);
                    }
                    hasContent = false;
                    lineLength = 0;
                    cursor.reset();
                    found = cursor.matches();

                    if (separator == '\r') {
                        if (index < length && inputBuffer[index] == '\n') {
                            index++;
                        } else if (index == length) {
                            afterCR = true;
                        }
                    }
                }
            }
        }

        if (hasContent) {
            lines++;
            if (found) {
                matching++;
                consumer.accept(lines, line, lineLength);
            }
        }
        return new Counts(lines, matching);
    }

    private static void requireRegularFile(Path file) throws IOException {
        if (!Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
            throw new IOException("Le chemin ne designe pas un fichier ordinaire : " + file);
        }
    }
}
