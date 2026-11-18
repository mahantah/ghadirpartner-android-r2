"""Bazaar API I/O with strict response checks and no secret-bearing redirects."""

import json
import secrets
from pathlib import Path
from typing import cast
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, OpenerDirector, Request, build_opener

from release_tools.config import ReleaseConfig

JsonObject = dict[str, object]


class NoRedirects(HTTPRedirectHandler):
    """Prevent API credentials being forwarded to a redirect target."""

    def redirect_request(
        self, req: Request, fp: object, code: int, msg: str, headers: object, newurl: str
    ) -> None:
        """Reject all redirects instead of forwarding authentication headers."""
        return None


class BazaarClient:
    """Expose the documented APK release endpoints without automatic POST retries."""

    def __init__(self, config: ReleaseConfig, opener: OpenerDirector | None = None) -> None:
        self.config = config
        self.opener = opener if opener is not None else build_opener(NoRedirects())

    def request(
        self,
        endpoint: str,
        method: str = "GET",
        body: bytes | None = None,
        content_type: str = "application/json",
    ) -> JsonObject:
        """Execute one request, redacting credentials and untrusted response bodies."""
        request = Request(self.config.api_base + self.config.endpoints[endpoint], data=body, method=method)
        request.add_header(self.config.auth_header, self.config.secret())
        request.add_header("Content-Type", content_type)
        request.add_header("Accept", "application/json")
        try:
            with self.opener.open(request, timeout=self.config.timeout_seconds) as response:
                value: object = json.loads(response.read())
        except HTTPError as error:
            raise RuntimeError(
                f"Bazaar {endpoint} returned HTTP {error.code}; check release status before retrying"
            ) from None
        except (URLError, TimeoutError, OSError) as error:
            raise RuntimeError(
                f"Bazaar {endpoint} transport failed ({type(error).__name__}); check release status before retrying"
            ) from None
        except (ValueError, UnicodeError):
            raise RuntimeError(f"Bazaar {endpoint} returned invalid JSON") from None
        if not isinstance(value, dict) or not all(isinstance(k, str) for k in value):
            raise RuntimeError("Bazaar response must be a JSON object")
        result = cast(JsonObject, value)
        if result.get("type") not in {"success", "not-exists"}:
            raise RuntimeError(f"Bazaar {endpoint} did not report success")
        if result.get("type") == "not-exists" and method != "GET":
            raise RuntimeError(f"Bazaar {endpoint} did not complete the requested operation")
        return result

    def upload(self, apk: Path) -> JsonObject:
        """Upload a universal APK using the documented multipart field names."""
        boundary = "ghadir-" + secrets.token_hex(24)
        body = (
            (
                f'--{boundary}\r\nContent-Disposition: form-data; name="architecture"\r\n\r\n{self.config.architecture}\r\n'
                f'--{boundary}\r\nContent-Disposition: form-data; name="apk"; filename="app-release.apk"\r\n'
                "Content-Type: application/vnd.android.package-archive\r\n\r\n"
            ).encode()
            + apk.read_bytes()
            + f"\r\n--{boundary}--\r\n".encode()
        )
        return self.request("upload", "POST", body, f"multipart/form-data; boundary={boundary}")
