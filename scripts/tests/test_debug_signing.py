"""Regression: cache loss or a changed key must not publish an incompatible APK."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "signing", Path(__file__).resolve().parents[1] / "verify_debug_signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningIdentityTest(unittest.TestCase):
    def test_matching_pinned_identity_is_accepted(self):
        signing.check_identity("a" * 64, "a" * 64, "a" * 64)

    def test_recreated_keystore_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "key changed or cache was lost"):
            signing.check_identity("a" * 64, "b" * 64, "b" * 64)

    def test_apk_signed_by_other_key_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "differs from debug.keystore"):
            signing.check_identity("a" * 64, "a" * 64, "b" * 64)

    def test_missing_pin_is_rejected_by_default(self):
        with self.assertRaisesRegex(ValueError, "pin missing"):
            signing.check_identity(None, "a" * 64, "a" * 64)

    def test_bootstrap_is_explicit_and_cannot_override_an_existing_pin(self):
        signing.check_identity(None, "a" * 64, "a" * 64, allow_unpinned=True)
        with self.assertRaisesRegex(ValueError, "key changed or cache was lost"):
            signing.check_identity("a" * 64, "b" * 64, "b" * 64, allow_unpinned=True)

    def test_malformed_pin_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid"):
            signing.check_identity("not-a-fingerprint", "a" * 64, "a" * 64)


if __name__ == "__main__":
    unittest.main()
