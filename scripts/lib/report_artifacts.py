#!/usr/bin/env python3
"""Maintenance locale du protocole : nettoyer ou republier la dernière campagne validée."""

import argparse
import fcntl
from pathlib import Path

from fixed_report import ROOT, clean_generated, publish_assets, validate_markdown_assets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("clean", "publish", "freeze"),
                        help="freeze est conservé comme alias historique de publish")
    args = parser.parse_args()
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    with (cache / "report.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error("Une campagne ou une maintenance du rapport est en cours.")
        try:
            if args.action == "clean":
                clean_generated()
                print("Sorties locales supprimées ; docs/assets reste intact.")
            else:
                publish_assets(ROOT / "target/report")
                validate_markdown_assets()
                print("Dernière campagne validée republiée atomiquement dans docs/assets/.")
        except (ValueError, OSError) as error:
            parser.error(str(error))


if __name__ == "__main__":
    main()
