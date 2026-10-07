import queue
import threading
import unittest
from unittest.mock import Mock, patch
import live_join_test as live


def pump(lines, exit_code=None):
    result = live.OutputPump.__new__(live.OutputPump)
    result.prefix = "fixture"; result.history = lines
    result.process = Mock(); result.process.poll.return_value = exit_code
    result.thread = Mock(); result.lines = queue.Queue()
    return result


class LiveHarnessTest(unittest.TestCase):
    def test_join_marker_alone_cannot_certify_playback(self):
        client, _ = live.required_markers("forge-1.20.1")
        with self.assertRaisesRegex(RuntimeError, "exited before evidence"):
            pump([live.PASS_MARKER], 0).wait_for_all(client, 1)

    def test_every_phase_is_required_even_when_final_marker_exists(self):
        for target in live.TARGETS:
            client, server = live.required_markers(target)
            for markers in (client, server):
                pump(list(markers), 0).wait_for_all(markers, 1)
                for absent in markers:
                    with self.assertRaises(RuntimeError):
                        pump([marker for marker in markers if marker != absent], 0).wait_for_all(markers, 1)

    def test_failure_overrides_pass_evidence(self):
        with self.assertRaisesRegex(RuntimeError, live.FAIL_MARKER):
            pump(["PASS", live.FAIL_MARKER + " decoder failed"], 0).wait_for_all(("PASS",), 1)

    def test_no_evidence_times_out_without_retry(self):
        with self.assertRaisesRegex(RuntimeError, "timed out"):
            pump([]).wait_for_all(("PASS",), 0)

    def test_loader_crash_fails_immediately_even_if_supervising_gradle_is_alive(self):
        with self.assertRaisesRegex(RuntimeError, "Exception in thread"):
            pump(['Exception in thread "main" loader failure']).wait_for_all(("PASS",), 1)

    def test_peer_failure_cannot_leave_client_waiting_for_the_entire_timeout(self):
        with self.assertRaisesRegex(RuntimeError, "Build failed"):
            pump([]).wait_for_all(("PASS",), 1, peer=pump(["FAILURE: Build failed"]))

    def test_only_supported_targets_require_peripheral_evidence(self):
        for target in live.TARGETS:
            _, server = live.required_markers(target)
            self.assertEqual("SIMPLYSPEAKERS_PERIPHERAL_PASS" in server, target != "neoforge-26.1.2")

    def test_compile_and_live_commands_bound_gradle_workers(self):
        from pathlib import Path
        command = live.command(Path("fixture"), ":forge-1.20.1:classes")
        self.assertIn("--max-workers=4", command)

    def test_server_preparation_resets_only_the_owned_fixture_world(self):
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory)
            server = module / "run" / "live-join" / "server"
            world = server / "live-join-world"
            world.mkdir(parents=True)
            (world / "old-emitter").write_text("stale")
            other = server / "user-world"
            other.mkdir()
            live.prepare_server(module)
            self.assertFalse(world.exists())
            self.assertTrue(other.exists())
            self.assertIn("minecraft:flat", (server / "server.properties").read_text())


if __name__ == "__main__": unittest.main()
