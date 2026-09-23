package com.sorbonne.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import com.sorbonne.search.SearchCursor;

/** Ouvre les fichiers texte en UTF-8 sans charger leur contenu entier en mémoire. */
public final class FileLoader {
    /** Tampon de 64 K caractères ; un compromis à mesurer selon la machine et le corpus. */
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
        try (BufferedReader reader = open(file)) {
            return count(reader, cursor);
        }
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
