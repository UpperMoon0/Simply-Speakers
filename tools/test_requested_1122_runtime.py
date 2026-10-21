import tempfile
import os
import signal
import subprocess
import sys
import time
import unittest
from pathlib import Path
import requested_1122_runtime as native

class Requested1122RuntimeTest(unittest.TestCase):
    def test_contract_requires_real_client_and_server_evidence(self):
        for phase in native.PHASES:
            self.assertTrue(any('CLIENT_PHASE_PASS ' + phase in marker for marker in native.CLIENT_MARKERS))
            self.assertTrue(any('SERVER_PHASE_PASS ' + phase in marker for marker in native.SERVER_MARKERS))
        for case in ('paused', 'playing'):
            self.assertTrue(any('AUDIO_RELOAD_PASS ' + case in marker for marker in native.CLIENT_MARKERS))
        self.assertTrue(any('AUDIO_UNLOAD_PASS' in marker for marker in native.CLIENT_MARKERS))
        self.assertTrue(any('UI_PASS' in marker for marker in native.CLIENT_MARKERS))
        self.assertTrue(any('major=8 ' in marker for marker in native.CLIENT_MARKERS))
    def test_missing_evidence_or_failure_is_rejected(self):
        with self.assertRaises(RuntimeError): native.validate_log('', native.CLIENT_MARKERS)
        complete = '\n'.join(native.CLIENT_MARKERS)
        native.validate_log(complete, native.CLIENT_MARKERS)
        with self.assertRaises(RuntimeError): native.validate_log(complete + '\nSIMPLYSPEAKERS_VERIFY_FAIL fault', native.CLIENT_MARKERS)
    def test_never_accepts_minecraft_eula(self):
        with tempfile.TemporaryDirectory() as root:
            module = Path(root)
            with self.assertRaisesRegex(RuntimeError, 'EULA'): native.prepare(module)
            self.assertFalse((module / 'run/legacy-runtime/server/eula.txt').exists())
    def test_fixture_stays_on_loopback_and_preserves_existing_eula(self):
        with tempfile.TemporaryDirectory() as root:
            module = Path(root); server = module / 'run/legacy-runtime/server'; server.mkdir(parents=True)
            eula = server / 'eula.txt'; eula.write_text('# accepted previously\neula=true\n')
            native.prepare(module)
            self.assertEqual('# accepted previously\neula=true\n', eula.read_text())
            config = (server / 'server.properties').read_text()
            self.assertIn('server-ip=127.0.0.1', config)
            self.assertIn('server-port=25576', config)
            self.assertIn('max-players=1', config)
    def test_run_tasks_and_heap_are_bounded(self):
        command = native.command(Path('/fixture'), 'runLegacyRuntimeClient', Path('/openui.jar'), Path('/packetweave.jar'))
        self.assertIn('--max-workers=2', command)
        self.assertIn('-Dorg.gradle.jvmargs=-Xmx256m -XX:ActiveProcessorCount=2', command)
        self.assertIn('-PopenuiJar=/openui.jar', command)
        self.assertIn('-PpacketweaveJar=/packetweave.jar', command)
        with self.assertRaises(ValueError): native.command(Path('/fixture'), 'publish', Path('/a'), Path('/b'))
    def test_preflight_disables_both_real_game_tasks(self):
        script = (native.ROOT / 'tools/prepare_requested_1122_runtime.gradle').read_text()
        for task in ('runLegacyRuntimeClient', 'runLegacyRuntimeServer'): self.assertIn(task, script)
        self.assertIn('task.enabled = false', script)
        self.assertIn('tasks.all', script)

    @staticmethod
    def process_running(pid):
        # Linux retains a dead orphan as a zombie until PID 1 reaps it; it is no
        # longer executing and cannot hold the build lease or spawn more work.
        try:
            status = Path('/proc', str(pid), 'stat').read_text()
        except FileNotFoundError:
            return False
        return status.rsplit(')', 1)[1].strip().split()[0] not in ('Z', 'X')

    def assert_processes_stopped(self, pids):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline and any(self.process_running(pid) for pid in pids):
            time.sleep(.02)
        self.assertFalse([pid for pid in pids if self.process_running(pid)], 'Preparation descendants still executing')

    def preparation_process_fixture(self, root, exit_parent):
        child = root / 'child.py'
        child.write_text("import os,signal,time\nfrom pathlib import Path\n"
                         "signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
                         "Path('child.pid').write_text(str(os.getpid()))\n"
                         "while True: time.sleep(1)\n")
        parent = root / 'parent.py'
        parent.write_text("import os,subprocess,sys,time\nfrom pathlib import Path\n"
                          "Path('parent.pid').write_text(str(os.getpid()))\n"
                          "subprocess.Popen([sys.executable, 'child.py'])\n"
                          "while not Path('child.pid').is_file(): time.sleep(.01)\n" +
                          ("sys.exit(0)\n" if exit_parent else "while True: time.sleep(1)\n"))
        return [sys.executable, str(parent)]

    def run_process_cleanup_case(self, exit_parent):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            command = self.preparation_process_fixture(root, exit_parent)
            pids = []
            try:
                if exit_parent:
                    native.run_preparation(command, root, 2, 'fixture-exited-parent')
                else:
                    with self.assertRaises(subprocess.TimeoutExpired):
                        native.run_preparation(command, root, 1, 'fixture-timeout')
                pids = [int((root / name).read_text()) for name in ('parent.pid', 'child.pid')]
                self.assert_processes_stopped(pids)
            finally:
                # Only these fixture-owned PIDs are eligible for test cleanup.
                for name in ('parent.pid', 'child.pid'):
                    path = root / name
                    if path.is_file():
                        pid = int(path.read_text())
                        if self.process_running(pid):
                            try: os.kill(pid, signal.SIGKILL)
                            except ProcessLookupError: pass

    @unittest.skipUnless(sys.platform.startswith('linux'), 'Real session cleanup contract uses Linux process state')
    def test_preparation_timeout_kills_parent_and_term_ignoring_child(self):
        self.run_process_cleanup_case(exit_parent=False)

    @unittest.skipUnless(sys.platform.startswith('linux'), 'Real session cleanup contract uses Linux process state')
    def test_completed_preparation_cleans_outliving_child(self):
        self.run_process_cleanup_case(exit_parent=True)

    def test_unsupported_process_tree_backend_rejected_before_spawn(self):
        from unittest.mock import patch
        with patch.object(native.os, 'name', 'nt'), patch.object(native.live, 'popen') as popen:
            with self.assertRaisesRegex(RuntimeError, 'POSIX'):
                native.run_preparation(['unused'], native.ROOT, 1, 'unsupported')
            popen.assert_not_called()

    def test_every_gradle_preparation_uses_bounded_session_helper(self):
        import inspect
        source = inspect.getsource(native.run)
        self.assertNotIn('subprocess.run(', source)
        self.assertEqual(2, source.count('run_preparation('))

if __name__ == '__main__': unittest.main()
