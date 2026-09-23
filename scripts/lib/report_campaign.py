"""Empreintes et corpus repetes, partages par la campagne fixe."""

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
    """Repete un corpus binaire par blocs, avec une fin de ligne finale stable."""
    lines = 0
    last = None
    corpora = {1: source}
    with tempfile.TemporaryFile() as normalized:
        with source.open("rb") as reader:
            while chunk := reader.read(64 * 1024):
                normalized.write(chunk)
                lines += chunk.count(b"\n")
                last = chunk[-1]
        if last is not None and last != 0x0A:
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
