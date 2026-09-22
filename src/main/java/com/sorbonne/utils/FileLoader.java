package com.sorbonne.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

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
}
