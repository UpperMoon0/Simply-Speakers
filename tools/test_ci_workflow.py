"""Lock scenario equivalence, independent scheduling and fail-closed dependency reuse."""
import json
from pathlib import Path
import re
import unittest

import cc_lua_test as cc
import live_join_test as live
import openui_cache
import verify

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/live-join-test.yml").read_text()
COVERAGE = json.loads((ROOT / "tools/ci_coverage_manifest.json").read_text())


def job(name):
    match = re.search(r"^  " + re.escape(name) + r":\n(.*?)(?=^  [a-z][a-z-]*:|\Z)", WORKFLOW, re.M | re.S)
    if match is None:
        raise AssertionError(f"Missing job {name}")
    return match[1]


class CiWorkflowTest(unittest.TestCase):
    def test_native_sessions_and_all_required_markers_unchanged(self):
        self.assertEqual(list(COVERAGE["targets"]), list(verify.TARGETS))
        self.assertEqual(list(live.TARGETS), list(verify.TARGETS))
        self.assertEqual(list(openui_cache.TARGETS), list(verify.TARGETS))
        self.assertEqual(list(verify.ADAPTER_TASKS), COVERAGE["adapter_tasks"])
        self.assertEqual(live.DEFAULT_TIMEOUT, COVERAGE["live_timeout_seconds"])
        for target, expected in COVERAGE["targets"].items():
            with self.subTest(target=target):
                self.assertEqual(expected["layers"], ["build", "live"] + (["cc"] if target in cc.TARGETS else []))
                self.assertEqual(expected["live_processes"], ["server", "carrier", "observer"])
                self.assertEqual(list(live.required_markers(target)[0]), expected["required_carrier_markers"])
                self.assertEqual(list(live.required_markers(target)[1]), expected["required_server_markers"])
                self.assertEqual(list(live.required_observer_markers()), expected["required_observer_markers"])
                plan = verify.layer_plan("full", [target])
                self.assertEqual([name for name, _ in plan], COVERAGE["common_layers"] + ["build", f"live-{target}"] + ([f"cc-{target}"] if target in cc.TARGETS else []))

    def test_adapter_and_live_jobs_are_independent_and_bounded(self):
        self.assertIn("needs: test-core", job("prepare-openui"))
        self.assertIn("needs: prepare-openui", job("test-shared"))
        native = job("live-join")
        self.assertIn("needs: [policy, prepare-openui]", native)
        self.assertNotIn("test-shared", native)
        self.assertIn("if: needs.policy.outputs.live_required == 'true'", native)
        self.assertIn("max-parallel: " + str(COVERAGE["maximum_live_jobs"]), native)
        self.assertIn("fail-fast: false", native)
        self.assertIn("matrix: ${{ fromJSON(needs.policy.outputs.matrix) }}", native)
        for layer in ("build", "live", "cc"):
            self.assertEqual(native.count(f'run: python3 tools/verify.py {layer} --target "${{{{ matrix.target }}}}"'), 1)
        self.assertIn("if: matrix.target != 'neoforge-26.1.2'", native)
        for name, timeout in COVERAGE["workflow_timeout_minutes"].items():
            self.assertIn(f"timeout-minutes: {timeout}", job(name))

    def test_gate_keeps_all_job_success_and_exact_source_receipts(self):
        gate = job("verification-gate")
        self.assertIn("needs: [policy, test-core, test-shared, live-join]", gate)
        self.assertIn("if: always()", gate)
        self.assertIn('test "$POLICY" = success && test "$CORE" = success && test "$SHARED" = success', gate)
        self.assertIn('if [ "$REQUIRED" = true ]; then test "$LIVE" = success; fi', gate)
        self.assertIn("python3 tools/verify.py gate --release", gate)
        self.assertIn("python3 tools/verify.py gate --core-only", gate)
        for name in ("test-core", "test-shared", "live-join", "verification-gate"):
            self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", job(name))

    def test_cache_and_artifact_consumers_always_validate(self):
        action = (ROOT / ".github/actions/restore-openui/action.yml").read_text()
        self.assertIn("uses: actions/cache/restore@v4", action)
        self.assertNotIn("restore-keys:", action)
        self.assertNotIn("actions/cache/save", action)
        self.assertIn("if: steps.cache.outputs.cache-hit != 'true'", action)
        self.assertIn("name: openui-verification-m2", action)
        self.assertIn("run: python3 tools/openui_cache.py validate", action)
        for name in ("test-shared", "live-join"):
            consumer = job(name)
            self.assertIn("uses: ./.github/actions/restore-openui", consumer)
            self.assertIn("cache-key: ${{ needs.prepare-openui.outputs.cache-key }}", consumer)
            self.assertLess(consumer.index("actions/setup-java"), consumer.index("restore-openui"))
            self.assertLess(consumer.index("restore-openui"), consumer.index("python3 tools/verify.py"))
        producer = job("prepare-openui")
        self.assertIn("run: python3 tools/openui_cache.py create --checkout .openui-mc", producer)
        self.assertIn("ref: " + openui_cache.SOURCE_PIN, producer)
        self.assertIn("java-version: 21", producer)
        self.assertIn("distribution: temurin", producer)
        self.assertLess(producer.index("openui_cache.py validate"), producer.index("actions/cache/save@v4"))
        self.assertLess(producer.index("openui_cache.py validate"), producer.index("actions/upload-artifact@v4"))
        self.assertNotIn("restore-keys:", producer)
        for target in verify.TARGETS:
            self.assertIn(f":{target}:publishToMavenLocal", producer)


if __name__ == "__main__":
    unittest.main()
