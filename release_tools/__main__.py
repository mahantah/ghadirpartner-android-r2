"""Command-line entry point for the release facade."""

import argparse
from pathlib import Path

from release_tools.apk import ApkVerifier
from release_tools.client import BazaarClient
from release_tools.config import ConfigLoader
from release_tools.service import BazaarRelease


class ReleaseCommand:
    """Compose release dependencies at the command boundary."""

    def run(self) -> None:
        """Parse validated CLI choices and invoke only the release facade."""
        parser = argparse.ArgumentParser(description="Prepare Ghadir Partner APK releases for Cafe Bazaar")
        parser.add_argument("--apk", type=Path, required=True)
        parser.add_argument("--aapt", type=Path, required=True)
        parser.add_argument("--apksigner", type=Path, required=True)
        parser.add_argument(
            "--operation", choices=("dry-run", "draft", "review", "publish"), default="dry-run"
        )
        args = parser.parse_args()
        config = ConfigLoader(Path(__file__).resolve().parent.parent).load()
        facade = BazaarRelease(config, BazaarClient(config), ApkVerifier(config, args.aapt, args.apksigner))
        print(facade.run(args.apk, args.operation))


if __name__ == "__main__":
    ReleaseCommand().run()
