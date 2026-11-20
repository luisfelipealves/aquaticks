"""Verify public signing identity. Never prints or uploads the private debug key."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def check_identity(expected, key_digest, apk_digest, allow_unpinned=False):
    if key_digest != apk_digest:
        raise ValueError("APK certificate differs from debug.keystore; refusing upload")
    if expected is None:
        if not allow_unpinned:
            raise ValueError("Signing pin missing; refusing unverified APK upload")
        return
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Invalid debug certificate SHA256 pin")
    if apk_digest != expected:
        raise ValueError("Debug signing key changed or cache was lost; restore the original key. Refusing incompatible APK upload")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--allow-unpinned", action="store_true",
                        help="One-time bootstrap only; remove before delivering the APK")
    args = parser.parse_args()
    store = Path("debug.keystore")
    if not store.is_file():
        raise ValueError("Expected debug.keystore at repository root; cache path is incorrect")
    cert = subprocess.check_output(["keytool", "-exportcert", "-keystore", str(store),
                                    "-storepass", "android", "-alias", "androiddebugkey"])
    key_digest = hashlib.sha256(cert).hexdigest()
    apk = Path("app/build/outputs/apk/debug/app-debug.apk")
    output = subprocess.check_output([args.apksigner, "verify", "--print-certs", str(apk)], text=True)
    matches = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})", output)
    if len(matches) != 1:
        raise ValueError("Expected exactly one APK signer")
    pin = Path("ci/debug-certificate.sha256")
    expected = pin.read_text().strip().lower() if pin.is_file() else None
    check_identity(expected, key_digest, matches[0].lower(), args.allow_unpinned)
    report = {"certificate_sha256": key_digest, "pinned": expected is not None}
    target = Path("app/build/reports/debug-signing.json")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + "\n")
    print("DEBUG_SIGNING_IDENTITY " + json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
