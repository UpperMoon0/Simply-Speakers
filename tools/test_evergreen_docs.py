"""Keep release-specific dependency pins out of evergreen documentation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
EVERGREEN = (
    "README.md",
    "curseforge.md",
    "docs/packetweave-integration.md",
    "docs/listener_audio.md",
    "docs/requested-generations.md",
)
PINNED_RELEASE = re.compile(r"(?<![0-9])(?:[0-9]+\\.){2}[0-9]+(?![0-9])")


class EvergreenDocumentationTest(unittest.TestCase):
    def test_user_facing_guides_have_no_pinned_releases(self):
        for path in EVERGREEN:
            with self.subTest(path=path):
                content = (ROOT / path).read_text(encoding="utf-8")
                self.assertIsNone(PINNED_RELEASE.search(content),
                    f"{path}: keep exact releases in changelog/ or loader metadata")

    def test_dependency_requirements_remain_actionable(self):
        for path in ("README.md", "curseforge.md", "docs/packetweave-integration.md"):
            with self.subTest(path=path):
                content = (ROOT / path).read_text(encoding="utf-8").lower()
                for expected in ("packetweave", "client", "server", "loader"):
                    self.assertIn(expected, content)

    def test_versioned_acceptance_snapshot_preserves_source_pins(self):
        content = (ROOT / "changelog/0.10.3-requested-generations.md").read_text(encoding="utf-8")
        self.assertIn("PacketWeave 0.1.5", content)
        self.assertIn("OpenUI MC 0.0.15", content)
        self.assertIn("../docs/requested-port-dependencies.json", content)


if __name__ == "__main__":
    unittest.main()
