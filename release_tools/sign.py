"""Sign a CI-built APK using private GitHub secrets and the existing certificate."""

import argparse
import base64
import os
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory

from release_tools.apk import ApkVerifier
from release_tools.config import ConfigLoader


class SigningCommand:
    """Keep the private keystore inside a temporary directory for one signing step."""

    def run(self) -> None:
        """Sign and validate an APK without printing or retaining signing secrets."""
        parser = argparse.ArgumentParser()
        parser.add_argument("--input", type=Path, required=True)
        parser.add_argument("--output", type=Path, required=True)
        parser.add_argument("--aapt", type=Path, required=True)
        parser.add_argument("--apksigner", type=Path, required=True)
        args = parser.parse_args()
        encoded = os.environ.get("GHADIR_RELEASE_KEYSTORE_BASE64", "")
        password = os.environ.get("GHADIR_RELEASE_KEYSTORE_PASSWORD", "")
        if not encoded or not password:
            raise ValueError(
                "Configure GHADIR_RELEASE_KEYSTORE_BASE64 and GHADIR_RELEASE_KEYSTORE_PASSWORD secrets"
            )
        config = ConfigLoader(Path(__file__).resolve().parent.parent).load()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(prefix="ghadir-sign-") as directory:
            keystore = Path(directory) / "release.p12"
            keystore.write_bytes(base64.b64decode(encoded, validate=True))
            subprocess.run(
                [
                    str(args.apksigner),
                    "sign",
                    "--ks",
                    str(keystore),
                    "--ks-pass",
                    "env:GHADIR_RELEASE_KEYSTORE_PASSWORD",
                    "--out",
                    str(args.output),
                    str(args.input),
                ],
                check=True,
            )
        ApkVerifier(config, args.aapt, args.apksigner).verify(args.output)
        print("Release APK signed and verified with the existing application certificate")


if __name__ == "__main__":
    SigningCommand().run()
