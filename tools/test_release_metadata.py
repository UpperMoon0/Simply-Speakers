import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
import check_release_metadata as check

class ReleaseMetadataTest(unittest.TestCase):
    def test_all_loader_dependencies_are_required_in_publication(self):
        check.validate()

    def test_missing_patchouli_is_rejected(self):
        original = Path.read_text
        def read(path, *args, **kwargs):
            text = original(path, *args, **kwargs)
            if path.name == "release.yml":
                text = text.replace(',{"slug":"patchouli","type":"requiredDependency"}', '')
            return text
        with patch.object(Path, "read_text", read):
            with self.assertRaisesRegex(ValueError, "patchouli"):
                check.validate()

    def test_stale_openui_cache_is_rejected(self):
        original = Path.read_text
        def read(path, *args, **kwargs):
            text = original(path, *args, **kwargs)
            if path.name == "release.yml":
                import re
                text = re.sub(r"key: openui-maven-[0-9a-f]{40}", "key: openui-maven-" + "a" * 40, text, count=1)
            return text
        with patch.object(Path, "read_text", read):
            with self.assertRaisesRegex(ValueError, "cache keys"):
                check.validate()

    def test_wrong_openui_checkout_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory)
            (checkout / "gradle.properties").write_text("mod_version = 0.0.7")
            with self.assertRaisesRegex(ValueError, "does not match"):
                check.validate_openui(checkout)
            version = check.property_value(check.ROOT / "gradle.properties", "openui_version")
            (checkout / "gradle.properties").write_text("mod_version = " + version)
            check.validate_openui(checkout)

if __name__ == "__main__": unittest.main()
