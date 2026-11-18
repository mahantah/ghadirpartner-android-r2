"""Release facade preserves existing drafts and prevents duplicate submissions."""

import json
from pathlib import Path
from typing import cast

from release_tools.apk import ApkVerifier
from release_tools.client import BazaarClient, JsonObject
from release_tools.config import ReleaseConfig


class BazaarRelease:
    """Coordinate verification, draft upload and explicitly requested review submission."""

    def __init__(self, config: ReleaseConfig, client: BazaarClient, verifier: ApkVerifier) -> None:
        self.config = config
        self.client = client
        self.verifier = verifier

    @staticmethod
    def _packages(response: JsonObject) -> list[JsonObject]:
        if response.get("type") == "not-exists":
            return []
        release = response.get("release")
        if not isinstance(release, dict) or not isinstance(release.get("packages"), list):
            raise RuntimeError("Bazaar release response has no package list")
        packages: list[JsonObject] = []
        for item in release["packages"]:
            if (
                not isinstance(item, dict)
                or not all(isinstance(k, str) for k in item)
                or type(item.get("version_code")) is not int
            ):
                raise RuntimeError("Bazaar returned invalid package metadata")
            packages.append(cast(JsonObject, item))
        return packages

    def _matches(self, packages: list[JsonObject], digest: str) -> bool:
        return (
            len(packages) == 1
            and packages[0].get("version_code") == self.config.version_code
            and packages[0].get("version_name") == self.config.version_name
            and packages[0].get("sha1_hash") == digest
        )

    def run(self, apk: Path, operation: str) -> str:
        """Validate or upload a draft, optionally submitting it for Bazaar review."""
        if operation not in {"dry-run", "draft", "review", "publish"}:
            raise ValueError("Unknown release operation")
        digest = self.verifier.verify(apk)
        if operation == "dry-run":
            return "Signed APK identity verified; no Bazaar request sent"
        # Read credentials and existing state before creating any remote draft.
        self.config.secret()
        for endpoint in ("published", "committed"):
            packages = self._packages(self.client.request(endpoint))
            if any(cast(int, p["version_code"]) >= self.config.version_code for p in packages):
                raise RuntimeError(
                    "This version or a newer one is already submitted; inspect Bazaar before retrying"
                )
        draft = self.client.request("draft")
        packages = self._packages(draft)
        if packages and not self._matches(packages, digest):
            raise RuntimeError("An unrelated draft already exists; it was preserved without changes")
        if draft.get("type") == "not-exists":
            self.client.request("create", "POST", b"{}")
        if not packages:
            upload = self.client.upload(apk)
            package = upload.get("package")
            if not isinstance(package, dict) or not self._matches([cast(JsonObject, package)], digest):
                raise RuntimeError(
                    "Uploaded APK metadata does not match the verified local file; draft was preserved"
                )
        # A second read also detects another publisher modifying the draft.
        if not self._matches(self._packages(self.client.request("draft")), digest):
            raise RuntimeError("Remote draft changed; submission was stopped")
        if operation == "draft":
            return "Verified APK staged in Bazaar; not submitted for review"
        payload = {
            "changelog_fa": self.config.changelog_fa,
            "changelog_en": self.config.changelog_en,
            "developer_note": self.config.developer_note,
            "staged_rollout_percentage": self.config.staged_rollout_percentage,
            "auto_publish": operation == "publish",
        }
        self.client.request("commit", "POST", json.dumps(payload, ensure_ascii=False).encode())
        return "Submitted for Bazaar review; auto_publish=" + str(operation == "publish").lower()
