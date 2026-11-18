"""Validated release policy and environment-only API credentials."""

import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import cast
from urllib.parse import urlparse

import yaml


@dataclass(frozen=True)
class ReleaseConfig:
    """Describe the app identity, API contract and release metadata."""

    api_base: str
    secret_environment: str
    auth_header: str
    package_name: str
    certificate_sha256: str
    architecture: str
    timeout_seconds: int
    maximum_apk_bytes: int
    staged_rollout_percentage: int
    changelog_fa: str
    changelog_en: str
    developer_note: str
    endpoints: dict[str, str]
    version_name: str
    version_code: int

    def secret(self) -> str:
        """Read the API credential only when a network operation is requested."""
        value = os.environ.get(self.secret_environment, "").strip()
        if not value or any(c in value for c in "\r\n"):
            raise ValueError(f"Configure the {self.secret_environment} environment secret")
        return value


class ConfigLoader:
    """Load the existing app version and strictly validate release-only policy."""

    def __init__(self, repository: Path) -> None:
        self.repository = repository

    @staticmethod
    def _mapping(path: Path) -> dict[str, object]:
        value: object = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict) or not all(isinstance(k, str) for k in value):
            raise ValueError(f"Expected a YAML mapping: {path.name}")
        return cast(dict[str, object], value)

    def load(self) -> ReleaseConfig:
        """Reject malformed configuration before signing or contacting Bazaar."""
        data = self._mapping(self.repository / "config/bazaar-release.yaml")
        flow = self._mapping(self.repository / "config/order-flow.yaml")
        expected = {
            "api_base",
            "api_secret",
            "auth_header",
            "package_name",
            "certificate_sha256",
            "architecture",
            "timeout_seconds",
            "maximum_apk_bytes",
            "staged_rollout_percentage",
            "changelog_fa",
            "changelog_en",
            "developer_note",
            "endpoints",
        }
        if data.keys() != expected:
            raise ValueError("Unknown or missing Bazaar release configuration keys")

        def text(key: str) -> str:
            value = data[key]
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"Invalid {key}")
            return value.strip()

        def integer(key: str, low: int, high: int) -> int:
            value = data[key]
            if type(value) is not int or not low <= value <= high:
                raise ValueError(f"Invalid {key}")
            return value

        base = text("api_base")
        url = urlparse(base)
        if (
            url.scheme != "https"
            or url.hostname != "api.pishkhan.cafebazaar.ir"
            or url.path != "/v1/"
            or url.query
            or url.fragment
            or url.username
            or url.port
        ):
            raise ValueError("The API credential may only be sent to the official Bazaar v1 endpoint")
        secret = re.fullmatch(r"\$\{([A-Z][A-Z0-9_]*)\}", text("api_secret"))
        certificate = text("certificate_sha256")
        if secret is None or re.fullmatch(r"[0-9a-f]{64}", certificate) is None:
            raise ValueError("Use an environment secret reference and a SHA-256 certificate fingerprint")
        architecture = text("architecture")
        if architecture not in {"all", "armeabi-v7a", "arm64-v8a"}:
            raise ValueError("Invalid APK architecture")
        endpoints = data["endpoints"]
        if not isinstance(endpoints, dict) or endpoints.keys() != {
            "create",
            "upload",
            "commit",
            "draft",
            "committed",
            "published",
        }:
            raise ValueError("Invalid release endpoints")
        paths: dict[str, str] = {}
        for key, value in endpoints.items():
            if (
                not isinstance(key, str)
                or not isinstance(value, str)
                or not re.fullmatch(r"apps/releases/(?:[a-z-]+/)?", value)
            ):
                raise ValueError("Invalid release endpoint path")
            paths[key] = value
        name, code = flow.get("version_name"), flow.get("version_code")
        if not isinstance(name, str) or not name or type(code) is not int or code <= 0:
            raise ValueError("Invalid app version")
        header = text("auth_header")
        package = text("package_name")
        if header != "CAFEBAZAAR-PISHKHAN-API-SECRET" or not re.fullmatch(
            r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+", package
        ):
            raise ValueError("Invalid authentication header or package identity")
        return ReleaseConfig(
            base,
            secret.group(1),
            header,
            package,
            certificate,
            architecture,
            integer("timeout_seconds", 1, 300),
            integer("maximum_apk_bytes", 1, 1_000_000_000),
            integer("staged_rollout_percentage", 1, 100),
            text("changelog_fa"),
            text("changelog_en"),
            text("developer_note"),
            paths,
            name,
            code,
        )
