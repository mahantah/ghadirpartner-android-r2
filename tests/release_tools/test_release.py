"""Exercise release state, identity validation and authentication boundaries offline."""

import hashlib
import json
import os
import threading
import unittest
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from release_tools.apk import ApkVerifier
from release_tools.client import BazaarClient, JsonObject
from release_tools.config import ConfigLoader
from release_tools.service import BazaarRelease

ROOT = Path(__file__).resolve().parents[2]


class FixtureVerifier(ApkVerifier):
    """Provide a deterministic verified APK for facade-only state tests."""

    def verify(self, apk: Path) -> str:
        """Return the fixture file's digest without invoking Android tools."""
        return hashlib.sha1(apk.read_bytes()).hexdigest()


class FixtureClient(BazaarClient):
    """Model documented Bazaar responses and record external mutations."""

    def __init__(self) -> None:
        super().__init__(ConfigLoader(ROOT).load())
        self.packages: list[JsonObject] = []
        self.draft_exists = False
        self.mutations: list[str] = []
        self.payload: JsonObject = {}
        self.existing_code = 0

    def request(
        self,
        endpoint: str,
        method: str = "GET",
        body: bytes | None = None,
        content_type: str = "application/json",
    ) -> JsonObject:
        """Record writes and return the current fixture release state."""
        if method == "POST":
            self.mutations.append(endpoint)
            if endpoint == "create":
                self.draft_exists = True
            if endpoint == "commit":
                self.payload = json.loads(body or b"{}")
            return {"type": "success"}
        if endpoint == "draft":
            return (
                {"type": "success", "release": {"packages": self.packages}}
                if self.draft_exists
                else {"type": "not-exists"}
            )
        return {"type": "success", "release": {"packages": [{"version_code": self.existing_code}]}}

    def upload(self, apk: Path) -> JsonObject:
        """Return the uploaded fixture's metadata with the expected API keys."""
        self.mutations.append("upload")
        package: JsonObject = {
            "version_code": self.config.version_code,
            "version_name": self.config.version_name,
            "sha1_hash": hashlib.sha1(apk.read_bytes()).hexdigest(),
        }
        self.packages = [package]
        return {"type": "success", "package": package}


class ReleaseTest(unittest.TestCase):
    """Check retries, foreign drafts, release versions and explicit submission modes."""

    def setUp(self) -> None:
        """Create an isolated signed-file fixture and dependency graph."""
        self.directory = TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "app.apk"
        self.apk.write_bytes(b"apk fixture")
        self.client = FixtureClient()
        self.config = self.client.config
        self.facade = BazaarRelease(
            self.config, self.client, FixtureVerifier(self.config, Path("aapt"), Path("apksigner"))
        )
        self.environment = patch.dict(os.environ, {self.config.secret_environment: "fixture-secret"})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_dry_run_never_reads_a_secret_or_contacts_bazaar(self) -> None:
        """Allow offline package validation before an API token is configured."""
        with patch.dict(os.environ, {}, clear=True):
            self.facade.run(self.apk, "dry-run")
        self.assertEqual(self.client.mutations, [])

    def test_draft_is_uploaded_once_and_reused(self) -> None:
        """A repeat run must preserve the identical draft without another POST."""
        self.facade.run(self.apk, "draft")
        self.facade.run(self.apk, "draft")
        self.assertEqual(self.client.mutations, ["create", "upload"])

    def test_foreign_draft_is_preserved(self) -> None:
        """Never delete or overwrite a draft created for a different package file."""
        self.client.draft_exists = True
        self.client.packages = [{"version_code": 1, "sha1_hash": "different"}]
        with self.assertRaisesRegex(RuntimeError, "unrelated draft"):
            self.facade.run(self.apk, "publish")
        self.assertEqual(self.client.mutations, [])

    def test_submitted_version_is_not_uploaded_again(self) -> None:
        """Reject already-submitted versions before mutating remote state."""
        self.client.existing_code = self.config.version_code
        with self.assertRaisesRegex(RuntimeError, "already submitted"):
            self.facade.run(self.apk, "draft")
        self.assertEqual(self.client.mutations, [])

    def test_review_does_not_enable_automatic_publication(self) -> None:
        """Review submission must retain a separate manual publication decision."""
        self.facade.run(self.apk, "review")
        self.assertIs(self.client.payload["auto_publish"], False)
        self.assertEqual(self.client.payload["changelog_fa"], self.config.changelog_fa)

    def test_publish_explicitly_enables_publication_after_review(self) -> None:
        """Only the publish operation may request automatic release after approval."""
        self.facade.run(self.apk, "publish")
        self.assertIs(self.client.payload["auto_publish"], True)

    def test_bad_upload_metadata_stops_submission(self) -> None:
        """Do not commit a release unless the server confirms the exact APK hash."""
        with patch.object(
            self.client, "upload", return_value={"type": "success", "package": {"version_code": 1}}
        ):
            with self.assertRaisesRegex(RuntimeError, "does not match"):
                self.facade.run(self.apk, "publish")
        self.assertNotIn("commit", self.client.mutations)

    def test_redirect_cannot_receive_the_api_secret(self) -> None:
        """Exercise urllib's real redirect handling against a local HTTP fixture."""
        visited: list[str] = []

        class Handler(BaseHTTPRequestHandler):
            """Offer a redirect target that must never be visited."""

            def do_GET(self) -> None:
                """Redirect the API request to a synthetic credential sink."""
                visited.append(self.path)
                self.send_response(302)
                self.send_header("Location", "/sink")
                self.end_headers()

            def log_message(self, format: str, *args: object) -> None:
                """Suppress fixture server logs containing request details."""

        with HTTPServer(("127.0.0.1", 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                config = replace(self.config, api_base=f"http://127.0.0.1:{server.server_port}/")
                with self.assertRaisesRegex(RuntimeError, "HTTP 302"):
                    BazaarClient(config).request("published")
            finally:
                server.shutdown()
                thread.join()
        self.assertEqual(visited, ["/" + self.config.endpoints["published"]])

    def test_configuration_rejects_plaintext_secrets_and_external_hosts(self) -> None:
        """Reject unsafe YAML before any credential-bearing operation can start."""
        import yaml

        raw: object = yaml.safe_load((ROOT / "config/bazaar-release.yaml").read_text())
        assert isinstance(raw, dict)
        for key, value in (("api_secret", "literal-secret"), ("api_base", "https://example.com/v1/")):
            with self.subTest(key=key):
                directory = Path(self.directory.name) / key
                (directory / "config").mkdir(parents=True)
                data = dict(raw)
                data[key] = value
                (directory / "config/bazaar-release.yaml").write_text(yaml.safe_dump(data))
                (directory / "config/order-flow.yaml").write_text(
                    (ROOT / "config/order-flow.yaml").read_text()
                )
                with self.assertRaises(ValueError):
                    ConfigLoader(directory).load()

    def test_documented_multipart_fields_and_authentication_header(self) -> None:
        """Send a real multipart request to a local server without contacting Bazaar."""
        received: list[tuple[str, str | None, bytes]] = []

        class Handler(BaseHTTPRequestHandler):
            """Capture the wire-level APK upload contract."""

            def do_POST(self) -> None:
                """Read the upload body and return a documented success response."""
                received.append(
                    (
                        self.path,
                        self.headers.get("CAFEBAZAAR-PISHKHAN-API-SECRET"),
                        self.rfile.read(int(self.headers["Content-Length"])),
                    )
                )
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"type":"success","package":{}}')

            def log_message(self, format: str, *args: object) -> None:
                """Keep fixture request credentials out of the test log."""

        with HTTPServer(("127.0.0.1", 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                config = replace(self.config, api_base=f"http://127.0.0.1:{server.server_port}/")
                BazaarClient(config).upload(self.apk)
            finally:
                server.shutdown()
                thread.join()
        path, header, body = received[0]
        self.assertEqual(path, "/" + self.config.endpoints["upload"])
        self.assertEqual(header, "fixture-secret")
        self.assertIn(b'name="architecture"\r\n\r\nall', body)
        self.assertIn(b'name="apk"; filename="app-release.apk"', body)
        self.assertIn(self.apk.read_bytes(), body)

    def test_apk_identity_and_certificate_are_checked(self) -> None:
        """Reject debug packages and alternate signing certificates offline."""
        metadata = f"package: name='{self.config.package_name}' versionCode='{self.config.version_code}' versionName='{self.config.version_name}'"
        report = f"Signer #1 certificate SHA-256 digest: {self.config.certificate_sha256}\nVerified using v2 scheme (APK Signature Scheme v2): true"
        verifier = ApkVerifier(self.config, Path("aapt"), Path("apksigner"))
        with patch("subprocess.check_output", side_effect=[metadata, report]):
            self.assertEqual(verifier.verify(self.apk), hashlib.sha1(self.apk.read_bytes()).hexdigest())
        with patch("subprocess.check_output", return_value=metadata + "\napplication-debuggable"):
            with self.assertRaisesRegex(ValueError, "debuggable"):
                verifier.verify(self.apk)
        with patch(
            "subprocess.check_output",
            side_effect=[metadata, report.replace(self.config.certificate_sha256, "0" * 64)],
        ):
            with self.assertRaisesRegex(ValueError, "certificate"):
                verifier.verify(self.apk)

    def test_secret_is_environment_only(self) -> None:
        """Reject missing credentials without echoing an actual token."""
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, self.config.secret_environment):
                self.config.secret()
