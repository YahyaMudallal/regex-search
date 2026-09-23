"""Empreintes et corpus répétés, partagés par la campagne fixe."""

import hashlib
import shutil
import tempfile


def sha256_file(path):
    """Hash par blocs, sans charger un corpus entier."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(64 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_scaled_corpora(source, directory, factors=(8, 32)):
    """Normalise sur disque puis répète par blocs, en mémoire O(taille du bloc)."""
    lines = 0
    last = ""
    corpora = {1: source}
    with tempfile.TemporaryFile() as normalized:
        with source.open(encoding="utf-8", newline=None) as reader:
            while chunk := reader.read(64 * 1024):
                normalized.write(chunk.encode("utf-8"))
                lines += chunk.count("\n")
                last = chunk[-1]
        if last and last != "\n":
            normalized.write(b"\n")
            lines += 1
        for factor in factors:
            path = directory / f"pride-{factor}x.txt"
            with path.open("wb") as writer:
                for _ in range(factor):
                    normalized.seek(0)
                    shutil.copyfileobj(normalized, writer, length=64 * 1024)
            corpora[factor] = path
    return corpora, lines
