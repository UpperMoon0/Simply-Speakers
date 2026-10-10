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

    def test_block_playback_cannot_certify_portable_inventory_playback(self):
        portable = {"portable_started", "portable_moved", "portable_paused", "portable_resumed",
                    "portable_stopped", "portable_restarted", "portable_removed"}
        self.assertTrue(portable.issubset(live.PHASES))
        for target in live.TARGETS:
            for markers in live.required_markers(target):
                block_evidence = [marker for marker in markers if "portable_" not in marker]
                with self.assertRaisesRegex(RuntimeError, "exited before evidence"):
                    pump(block_evidence, 0).wait_for_all(markers, 1)

    def test_playback_markers_cannot_certify_actual_context_reload(self):
        self.assertEqual({"normal_playing", "normal_paused", "portable_playing", "portable_paused"}, set(live.RELOAD_CASES))
        for target in live.TARGETS:
            client, server = live.required_markers(target)
            for markers, prefix in ((client, "SIMPLYSPEAKERS_AUDIO_RELOAD_PASS"),
                                    (server, "SIMPLYSPEAKERS_SERVER_AUDIO_RELOAD_PASS")):
                for case in live.RELOAD_CASES:
                    self.assertIn(f"{prefix} {case}", markers)
                with self.assertRaisesRegex(RuntimeError, "exited before evidence"):
                    pump([marker for marker in markers if prefix not in marker], 0).wait_for_all(markers, 1)

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

    def test_each_observer_phase_is_independently_required(self):
        markers = live.required_observer_markers()
        self.assertEqual(9, len(live.OBSERVER_PHASES))
        pump(list(markers), 0).wait_for_all(markers, 1)
        for absent in markers:
            with self.assertRaisesRegex(RuntimeError, "exited before evidence"):
                pump([marker for marker in markers if marker != absent], 0).wait_for_all(markers, 1)

    def test_carrier_evidence_cannot_certify_second_listener(self):
        client, server = live.required_markers("forge-1.20.1")
        with self.assertRaisesRegex(RuntimeError, "exited before evidence"):
            pump(list(client) + list(server), 0).wait_for_all(live.required_observer_markers(), 1)
        for phase in live.OBSERVER_PHASES:
            self.assertIn(f"SIMPLYSPEAKERS_SERVER_PHASE_PASS {phase}", server)

    def test_failure_of_either_peer_interrupts_the_wait(self):
        for failed in (0, 1):
            peers = [pump([]), pump([])]
            peers[failed].history.append(live.FAIL_MARKER + " peer failed")
            with self.assertRaisesRegex(RuntimeError, "peer failed"):
                pump([]).wait_for_all(("PASS",), 1, peers=peers)
        with self.assertRaisesRegex(RuntimeError, "game process exited"):
            pump([]).wait_for_all(("PASS",), 1, peers=(pump([]), pump([], 1)))

    def test_runtime_supervisors_have_lower_independent_heap_and_worker_limits(self):
        from pathlib import Path
        with patch.dict(live.os.environ, {}, clear=True):
            compiled = live.command(Path("fixture"), ":forge-1.20.1:classes")
            observer = live.command(Path("fixture"), ":forge-1.20.1:runLiveJoinTestObserver")
        self.assertIn("--max-workers=4", compiled)
        self.assertIn("-Dorg.gradle.jvmargs=-Xmx2048m", compiled)
        self.assertIn("--max-workers=2", observer)
        self.assertIn("-Dorg.gradle.jvmargs=-Xmx1024m", observer)

    def test_clients_have_isolated_directories_and_audible_test_options(self):
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory)
            live.prepare_client(module)
            live.prepare_client(module, "observer")
            for role in ("client", "observer"):
                options = module / "run" / "live-join" / role / "options.txt"
                self.assertIn("pauseOnLostFocus:false", options.read_text())
                self.assertIn("soundCategory_master:1.0", options.read_text())
                self.assertIn("soundCategory_record:1.0", options.read_text())
                self.assertTrue((options.parent / "tmp" / "lwjgl").is_dir())
            with self.assertRaises(ValueError):
                live.prepare_client(module, "../other")
            self.assertFalse((module / "run" / "other").exists())

    def test_native_extraction_and_temp_properties_target_each_game_not_gradle(self):
        from pathlib import Path
        root = Path(__file__).resolve().parents[1]
        config = (root / "build.gradle").read_text()
        isolated = config.split("if (name in ['runLiveJoinTestClient', 'runLiveJoinTestObserver']) {", 1)[1]
        isolated = isolated.split("tasks.register('testCore')", 1)[0]
        self.assertIn("name == 'runLiveJoinTestClient' ? 'client' : 'observer'", isolated)
        self.assertIn('project.file("run/live-join/${role}/tmp")', isolated)
        self.assertIn("systemProperty 'java.io.tmpdir', scratchDir.absolutePath", isolated)
        self.assertIn("systemProperty 'org.lwjgl.system.SharedLibraryExtractPath', nativeDir.absolutePath", isolated)
        self.assertIn("nativeDir.mkdirs()", isolated)
        self.assertIn("systemProperty 'org.lwjgl.util.DebugLoader', 'true'", isolated)
        for role in ("Client", "Observer"):
            supervisor = live.command(root, ":fabric-1.21.1:runLiveJoinTest" + role)
            self.assertFalse(any("SharedLibraryExtractPath" in argument or "java.io.tmpdir" in argument
                                 for argument in supervisor))

    def test_observer_launch_uses_its_distinct_task(self):
        from pathlib import Path
        with patch.dict(live.os.environ, {"DISPLAY": ":test"}):
            self.assertIn(":forge-1.20.1:runLiveJoinTestObserver",
                          live.client_command(Path("fixture"), "forge-1.20.1", observer=True))

    def test_both_client_runtime_dependency_graphs_are_prepared_serially(self):
        from pathlib import Path
        for module in live.TARGETS:
            with patch.object(live.subprocess, "run") as run:
                live.prepare_client_runtimes(Path("fixture"), module, 30)
            self.assertEqual(2, run.call_count)
            for call, task in zip(run.call_args_list, ("runLiveJoinTestClient", "runLiveJoinTestObserver")):
                cmd = call.args[0]
                self.assertIn(f":{module}:{task}", cmd)
                self.assertEqual(["--init-script", str(Path("fixture/tools/prepare_live_clients.gradle"))], cmd[-2:])
                self.assertNotIn("--dry-run", cmd)
                self.assertNotIn("-x", cmd)
                self.assertEqual({"cwd": Path("fixture"), "check": True, "timeout": 30}, call.kwargs)

    def test_preflight_disables_only_launch_actions_not_dependency_tasks(self):
        from pathlib import Path
        root = Path(__file__).resolve().parents[1]
        script = (root / "tools" / "prepare_live_clients.gradle").read_text()
        self.assertIn("['runLiveJoinTestClient', 'runLiveJoinTestObserver']", script)
        self.assertIn("task.enabled = false", script)
        self.assertNotIn("excludeTask", script)
        with patch.dict(live.os.environ, {"DISPLAY": ":test"}):
            for observer in (False, True):
                self.assertNotIn("--init-script", live.client_command(root, "forge-1.20.1", observer))

    def test_preflight_failure_or_timeout_prevents_any_game_launch(self):
        from pathlib import Path
        for failed_step in (1, 2):
            for error in (live.subprocess.CalledProcessError(1, "preflight"),
                          live.subprocess.TimeoutExpired("preflight", 30)):
                outcomes = [None] * failed_step + [error]
                with patch.object(live, "prepare_server"), patch.object(live, "prepare_client"), \
                     patch.object(live.subprocess, "run", side_effect=outcomes) as run, \
                     patch.object(live, "popen") as launch:
                    with self.assertRaises(type(error)):
                        live.run_target(Path("fixture"), "forge-1.20.1", 30)
                    self.assertEqual(failed_step + 1, run.call_count)
                    launch.assert_not_called()

    def test_both_preflights_finish_before_server_and_two_real_clients_launch(self):
        from pathlib import Path
        events = []
        processes = [Mock(), Mock(), Mock()]
        for process in processes: process.poll.return_value = None
        outputs = [Mock(), Mock(), Mock()]
        outputs[0].wait_for.return_value = "Done ("
        def prepare(cmd, **kwargs):
            events.append(("prepare", cmd[1]))
        def launch(cmd, root):
            events.append(("launch", cmd[1]))
            return processes[len([event for event in events if event[0] == "launch"]) - 1]
        with patch.object(live, "prepare_server"), patch.object(live, "prepare_client"), \
             patch.object(live.subprocess, "run", side_effect=prepare), \
             patch.dict(live.os.environ, {"DISPLAY": ":test"}), \
             patch.object(live, "popen", side_effect=launch), \
             patch.object(live, "OutputPump", side_effect=outputs), patch.object(live, "stop_tree"):
            live.run_target(Path("fixture"), "forge-1.20.1", 30)
        self.assertEqual([
            ("prepare", ":forge-1.20.1:classes"),
            ("prepare", ":forge-1.20.1:runLiveJoinTestClient"),
            ("prepare", ":forge-1.20.1:runLiveJoinTestObserver"),
            ("launch", ":forge-1.20.1:runLiveJoinTestServer"),
            ("launch", ":forge-1.20.1:runLiveJoinTestClient"),
            ("launch", ":forge-1.20.1:runLiveJoinTestObserver"),
        ], events)
        for output in outputs:
            output.wait_for_all.assert_called_once()

    def test_three_process_cleanup_runs_after_carrier_failure(self):
        from pathlib import Path
        server, carrier, observer = Mock(), Mock(), Mock()
        for process in (server, carrier, observer): process.poll.return_value = None
        server_output, carrier_output, observer_output = Mock(), Mock(), Mock()
        server_output.wait_for.return_value = "Done ("
        carrier_output.wait_for_all.side_effect = RuntimeError("fixture failure")
        with patch.object(live, "prepare_server"), patch.object(live, "prepare_client"), \
             patch.object(live.subprocess, "run"), patch.object(live, "client_command", return_value=["client"]), \
             patch.object(live, "popen", side_effect=[server, carrier, observer]), \
             patch.object(live, "OutputPump", side_effect=[server_output, carrier_output, observer_output]), \
             patch.object(live, "stop_tree") as stop:
            with self.assertRaisesRegex(RuntimeError, "fixture failure"):
                live.run_target(Path("fixture"), "forge-1.20.1", 30)
            self.assertEqual([unittest.mock.call(observer), unittest.mock.call(carrier),
                              unittest.mock.call(server, graceful_server=True)], stop.call_args_list)
        self.assertEqual((server_output, observer_output), carrier_output.wait_for_all.call_args.kwargs["peers"])

    def test_failed_client_cleanup_does_not_skip_other_processes(self):
        from pathlib import Path
        server, carrier, observer = Mock(), Mock(), Mock()
        for process in (server, carrier, observer): process.poll.return_value = None
        server_output, carrier_output, observer_output = Mock(), Mock(), Mock()
        server_output.wait_for.return_value = "Done ("
        with patch.object(live, "prepare_server"), patch.object(live, "prepare_client"), \
             patch.object(live.subprocess, "run"), patch.object(live, "client_command", return_value=["client"]), \
             patch.object(live, "popen", side_effect=[server, carrier, observer]), \
             patch.object(live, "OutputPump", side_effect=[server_output, carrier_output, observer_output]), \
             patch.object(live, "stop_tree", side_effect=[RuntimeError("cleanup error"), None, None]) as stop:
            with self.assertRaisesRegex(RuntimeError, "cleanup failed"):
                live.run_target(Path("fixture"), "forge-1.20.1", 30)
            self.assertEqual(3, stop.call_count)
            self.assertEqual(unittest.mock.call(server, graceful_server=True), stop.call_args_list[-1])

    @unittest.skipIf(live.os.name == "nt", "POSIX process-group behavior")
    def test_exited_supervisor_still_terminates_its_game_process_group(self):
        process = Mock(); process.poll.return_value = 1; process.pid = 12345
        with patch.object(live.os, "killpg") as kill:
            live.stop_tree(process)
        kill.assert_called_once_with(12345, live.signal.SIGTERM)

    def test_all_five_loader_configs_keep_player_names_and_directories_distinct(self):
        from pathlib import Path
        root = Path(__file__).resolve().parents[1]
        for target in live.TARGETS:
            config = (root / target / "build.gradle").read_text()
            self.assertIn("liveJoinTestObserver", config)
            self.assertIn("SSCarrier", config)
            self.assertIn("SSObserver", config)
            self.assertIn("run/live-join/client", config)
            self.assertIn("run/live-join/observer", config)
            self.assertIn("simplyspeakers.livePlaybackRole", config)

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
