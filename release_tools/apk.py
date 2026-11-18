"""Verify signed release identity before an APK can leave the build machine."""

import hashlib
import re
import subprocess
from pathlib import Path

from release_tools.config import ReleaseConfig


class ApkVerifier:
    """Verify Android metadata and the existing app signing certificate."""

    def __init__(self, config: ReleaseConfig, aapt: Path, apksigner: Path) -> None:
        self.config = config
        self.aapt = aapt
        self.apksigner = apksigner

    def verify(self, apk: Path) -> str:
        """Return the APK SHA-1 after rejecting unsigned, debug or wrong-app files."""
        if (
            not apk.is_file()
            or apk.suffix != ".apk"
            or not 0 < apk.stat().st_size <= self.config.maximum_apk_bytes
        ):
            raise ValueError("APK file is missing, empty, oversized or has the wrong extension")
        metadata = subprocess.check_output([str(self.aapt), "dump", "badging", str(apk)], text=True)
        package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", metadata)
        expected = (self.config.package_name, str(self.config.version_code), self.config.version_name)
        if package is None or package.groups() != expected or "application-debuggable" in metadata:
            raise ValueError(
                "APK package/version does not match the configured release, or APK is debuggable"
            )
        report = subprocess.check_output(
            [str(self.apksigner), "verify", "--verbose", "--print-certs", str(apk)], text=True
        )
        certs = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)", report)
        if certs != [self.config.certificate_sha256] or "APK Signature Scheme v2): true" not in report:
            raise ValueError("APK must use the existing release signing certificate and v2 signature")
        return hashlib.sha1(apk.read_bytes()).hexdigest()
