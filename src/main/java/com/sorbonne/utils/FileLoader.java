package com.sorbonne.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import com.sorbonne.search.SearchCursor;

/** Ouvre les fichiers texte en UTF-8 sans charger leur contenu entier en mémoire. */
public final class FileLoader {
    /** Bloc de 64 K unités : caractères pour Reader, octets pour le chemin UTF-8 rapide. */
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Classe utilitaire sans instance. */
    private FileLoader() {
    }

    /**
     * Ouvre un lecteur bufferisé que l'appelant doit fermer avec try-with-resources.
     *
     * <p>Le décodeur signale les séquences UTF-8 invalides au lieu de les remplacer.
     * {@link BufferedReader#readLine()} retire les séparateurs LF, CR ou CRLF ;
     * une dernière ligne sans séparateur est également lue. Le tampon limite
     * les petits accès, mais une ligne très longue doit encore tenir en mémoire.</p>
     *
     * @param file fichier ordinaire à ouvrir, éventuellement via un lien symbolique
     * @return lecteur prêt à parcourir le texte ; aucun contenu n'est encore lu
     * @throws IOException si le fichier est absent, inaccessible ou n'est pas ordinaire
     * @throws NullPointerException si le chemin est nul
     */
    public static BufferedReader open(Path file) throws IOException {
        Objects.requireNonNull(file, "Le fichier ne doit pas être nul");
        if (!Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
            throw new IOException("Le chemin ne désigne pas un fichier ordinaire : " + file);
        }
        return new BufferedReader(new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8.newDecoder()), BUFFER_SIZE);
    }

    /** Compteurs sans stockage du contenu des lignes. */
    public record Counts(long totalLines, long matchingLines) {
    }

    /** O(C + L) temps et O(B) mémoire hors moteur, même pour une très longue ligne. */
    public static Counts count(Path file, SearchCursor cursor) throws IOException {
        Objects.requireNonNull(file, "Le fichier ne doit pas être nul");
        Objects.requireNonNull(cursor, "Le curseur ne doit pas être nul");
        if (!Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
            throw new IOException("Le chemin ne désigne pas un fichier ordinaire : " + file);
        }
        try (InputStream input = Files.newInputStream(file)) {
            return countUtf8(input, cursor);
        }
    }

    /**
     * Chemin de production : décodeur UTF-8 strict intégré au scan. Les longues
     * plages ASCII sont transmises au moteur sous forme d'octets, sans créer de
     * {@code char[]} intermédiaire. Les séquences multioctets conservent exactement
     * la sémantique UTF-16 du moteur, y compris les paires de substituts.
     */
    private static Counts countUtf8(InputStream input, SearchCursor cursor) throws IOException {
        cursor.reset();
        byte[] buffer = new byte[BUFFER_SIZE];
        long lines = 0;
        long matching = 0;
        boolean hasContent = false;
        boolean afterCR = false;
        boolean found = cursor.matches();

        int remaining = 0;
        int codePoint = 0;
        int minimum = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            int index = 0;
            if (afterCR) {
                if (length > 0 && buffer[0] == '\n') {
                    index = 1;
                }
                afterCR = false;
            }

            if (remaining > 0) {
                while (index < length && remaining > 0) {
                    int next = buffer[index++] & 0xff;
                    if ((next & 0xc0) != 0x80) {
                        throw malformedUtf8();
                    }
                    codePoint = (codePoint << 6) | (next & 0x3f);
                    remaining--;
                }
                if (remaining == 0) {
                    validateCodePoint(codePoint, minimum);
                    hasContent = true;
                    if (!found) {
                        found = acceptCodePoint(cursor, codePoint);
                    }
                } else {
                    continue;
                }
            }

            int asciiStart = index;
            while (index < length) {
                int value = buffer[index] & 0xff;
                if (value < 0x80 && value != '\r' && value != '\n') {
                    index++;
                    continue;
                }

                int asciiLength = index - asciiStart;
                if (asciiLength > 0) {
                    hasContent = true;
                    if (!found) {
                        found = cursor.acceptAscii(buffer, asciiStart, asciiLength);
                    }
                }

                if (value == '\r' || value == '\n') {
                    lines++;
                    if (found) {
                        matching++;
                    }
                    hasContent = false;
                    cursor.reset();
                    found = cursor.matches();
                    if (value == '\r') {
                        if (index + 1 < length && buffer[index + 1] == '\n') {
                            index++;
                        } else if (index + 1 == length) {
                            afterCR = true;
                        }
                    }
                    index++;
                    asciiStart = index;
                    continue;
                }

                if (value >= 0xc2 && value <= 0xdf) {
                    codePoint = value & 0x1f;
                    remaining = 1;
                    minimum = 0x80;
                } else if (value >= 0xe0 && value <= 0xef) {
                    codePoint = value & 0x0f;
                    remaining = 2;
                    minimum = 0x800;
                } else if (value >= 0xf0 && value <= 0xf4) {
                    codePoint = value & 0x07;
                    remaining = 3;
                    minimum = 0x10000;
                } else {
                    throw malformedUtf8();
                }
                index++;
                while (index < length && remaining > 0) {
                    int next = buffer[index++] & 0xff;
                    if ((next & 0xc0) != 0x80) {
                        throw malformedUtf8();
                    }
                    codePoint = (codePoint << 6) | (next & 0x3f);
                    remaining--;
                }
                if (remaining == 0) {
                    validateCodePoint(codePoint, minimum);
                    hasContent = true;
                    if (!found) {
                        found = acceptCodePoint(cursor, codePoint);
                    }
                    asciiStart = index;
                } else {
                    asciiStart = index;
                    break;
                }
            }

            if (remaining == 0) {
                int asciiLength = length - asciiStart;
                if (asciiLength > 0) {
                    hasContent = true;
                    if (!found) {
                        found = cursor.acceptAscii(buffer, asciiStart, asciiLength);
                    }
                }
            }
        }

        if (remaining != 0) {
            throw malformedUtf8();
        }
        if (hasContent) {
            lines++;
            if (found) {
                matching++;
            }
        }
        return new Counts(lines, matching);
    }

    private static boolean acceptCodePoint(SearchCursor cursor, int codePoint) {
        if (codePoint <= Character.MAX_VALUE) {
            return cursor.accept((char) codePoint);
        }
        int value = codePoint - Character.MIN_SUPPLEMENTARY_CODE_POINT;
        char high = (char) (Character.MIN_HIGH_SURROGATE + (value >>> 10));
        char low = (char) (Character.MIN_LOW_SURROGATE + (value & 0x3ff));
        return cursor.accept(high) || cursor.accept(low);
    }

    private static void validateCodePoint(int codePoint, int minimum) throws MalformedInputException {
        if (codePoint < minimum || codePoint > Character.MAX_CODE_POINT
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
            throw malformedUtf8();
        }
    }

    private static MalformedInputException malformedUtf8() {
        return new MalformedInputException(1);
    }

    /**
     * Parcours par blocs ; mêmes frontières LF, CR et CRLF que readLine().
     * Le lecteur fourni reste ouvert. Toutes les données sont décodées même
     * après une correspondance, afin de conserver les erreurs UTF-8.
     */
    public static Counts count(Reader reader, SearchCursor cursor) throws IOException {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(cursor);
        cursor.reset();
        char[] buffer = new char[BUFFER_SIZE];
        long lines = 0;
        long matching = 0;
        boolean hasContent = false;
        boolean afterCR = false;
        boolean found = cursor.matches();
        int length;
        while ((length = reader.read(buffer)) != -1) {
            for (int i = 0; i < length; i++) {
                char symbol = buffer[i];
                if (afterCR && symbol == '\n') {
                    afterCR = false;
                    continue;
                }
                afterCR = symbol == '\r';
                if (symbol == '\r' || symbol == '\n') {
                    lines++;
                    if (found) {
                        matching++;
                    }
                    hasContent = false;
                    cursor.reset();
                    found = cursor.matches();
                } else {
                    hasContent = true;
                    if (!found) {
                        found = cursor.accept(symbol);
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
}
