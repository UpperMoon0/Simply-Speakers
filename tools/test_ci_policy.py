import unittest
from ci_policy import requires_live


class CiPolicyTest(unittest.TestCase):
    def test_only_known_documentation_can_skip_boots_on_pr(self):
        self.assertFalse(requires_live("pull_request", ["README.md", "docs/usage.md", "TESTING.md"]))
        for path in ("build.gradle", "gradle.properties", "settings.gradle", "tools/verify.py", ".github/workflows/release.yml",
                     "common/src/main/java/New.java", "AGENTS.md", "new-unknown-file.txt", "docs/config.json"):
            self.assertTrue(requires_live("pull_request", ["README.md", path]), path)
    def test_empty_changes_manual_and_release_runs_require_full_verification(self):
        self.assertTrue(requires_live("pull_request", []))
        for event in ("workflow_dispatch", "workflow_call", "push"):
            self.assertTrue(requires_live(event, ["README.md"]))


if __name__ == "__main__": unittest.main()
