import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch
import verify


class VerificationPolicyTest(unittest.TestCase):
    def test_core_cannot_launch_games_or_configure_loader_tests(self):
        plan = verify.layer_plan("core", list(verify.TARGETS))
        self.assertEqual([name for name, _ in plan], ["harness", "core"])
        self.assertIn(":common:test", plan[1][1])
        self.assertNotIn("live_join_test.py", str(plan))

    def test_full_runs_core_once_then_adapters_and_one_live_boot_per_target(self):
        names = [name for name, _ in verify.layer_plan("full", list(verify.TARGETS))]
        self.assertEqual(names[:4], ["harness", "core", "adapters", "build"])
        self.assertEqual(names[4:], [f"live-{t}" for t in verify.TARGETS] + [f"cc-{t}" for t in verify.TARGETS if t!="neoforge-26.1.2"])
        self.assertEqual(names.count("core"), 1)

    def test_cc_plan_uses_real_lua_runner_on_supported_loaders_only(self):
        plan = verify.layer_plan("cc", list(verify.TARGETS))
        self.assertEqual(len(plan), 4)
        for name, command in plan:
            self.assertNotIn("26.1.2", name)
            self.assertIn("tools/cc_lua_test.py", command)
            self.assertIn("--target", command)
        self.assertEqual(verify.layer_plan("cc", ["neoforge-26.1.2"]), [])

    def test_adapter_plan_covers_all_versions_without_duplicate_pure_tests(self):
        command = verify.layer_plan("adapters", [])[0][1]
        for task in verify.ADAPTER_TASKS: self.assertIn(task, command)
        self.assertNotIn(":common:test", command)

    def test_receipts_reject_missing_failed_wrong_layer_and_stale_sources(self):
        identity = {"commit": "head", "source_sha256": "source", "dirty": False}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "Missing"):
                verify.validate_receipts(root, identity, ["core"])
            receipt = {**identity, "layer": "core", "status": "passed"}
            verify.write_receipt(root / "core.json", receipt)
            verify.validate_receipts(root, identity, ["core"], release=True)
            for field, value in (("status", "failed"), ("layer", "other"), ("commit", "old"), ("source_sha256", "old")):
                verify.write_receipt(root / "core.json", {**receipt, field: value})
                with self.assertRaises(RuntimeError): verify.validate_receipts(root, identity, ["core"])

    def test_modified_results_are_local_only(self):
        identity = {"commit": "head", "source_sha256": "source", "dirty": True}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            verify.write_receipt(root / "core.json", {**identity, "layer": "core", "status": "passed"})
            verify.validate_receipts(root, identity, ["core"])
            with self.assertRaisesRegex(RuntimeError, "clean"):
                verify.validate_receipts(root, identity, ["core"], release=True)

    def test_second_runner_cannot_take_checkout_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with verify.checkout_lock(root):
                with self.assertRaisesRegex(RuntimeError, "Another"):
                    with verify.checkout_lock(root): pass

    def test_receipt_writes_are_valid_and_leave_no_temporary_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            verify.write_receipt(path, {"status": "failed"})
            verify.write_receipt(path, {"status": "passed"})
            self.assertEqual(json.loads(path.read_text())["status"], "passed")
            self.assertFalse(path.with_suffix(".tmp").exists())

    def test_failed_process_invalidates_previous_success_and_retains_output(self):
        identity = {"commit": "head", "source_sha256": "source", "dirty": True}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            verify.write_receipt(root / "core.json", {**identity, "layer": "core", "status": "passed"})
            with patch.object(verify, "ROOT", root), self.assertRaisesRegex(RuntimeError, "layer failed"):
                verify.run_layer("core", [sys.executable, "-c", "print('failure evidence'); raise SystemExit(2)"], identity, root)
            self.assertEqual(json.loads((root / "core.json").read_text())["status"], "failed")
            self.assertIn("failure evidence", (root / "core.log").read_text())

    def test_successful_process_cannot_certify_sources_changed_mid_run(self):
        identity = {"commit": "head", "source_sha256": "source", "dirty": True}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(verify, "ROOT", root), patch.object(verify, "checkout_identity", return_value={**identity, "source_sha256": "changed"}):
                with self.assertRaisesRegex(RuntimeError, "Sources changed"):
                    verify.run_layer("core", [sys.executable, "-c", "print('passed tests')"], identity, root)
            self.assertEqual(json.loads((root / "core.json").read_text())["status"], "failed")

    def test_hung_layer_is_bounded_and_records_failure(self):
        identity = {"commit": "head", "source_sha256": "source", "dirty": True}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(verify, "ROOT", root), self.assertRaisesRegex(RuntimeError, "timed out"):
                verify.run_layer("core", [sys.executable, "-c", "import time; time.sleep(30)"], identity, root, timeout=0.1)
            self.assertEqual(json.loads((root / "core.json").read_text())["status"], "failed")


if __name__ == "__main__": unittest.main()
