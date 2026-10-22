"""Real process-session regressions for the shared runtime supervisor."""
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch
import live_join_test as live


def running(pid):
    try:
        state = Path('/proc', str(pid), 'stat').read_text().rsplit(')', 1)[1].strip().split()[0]
    except (FileNotFoundError, ProcessLookupError):
        return False
    # An orphan zombie has terminated; only its OS reaping remains outstanding.
    return state not in ('Z', 'X')


class ProcessObservationTest(unittest.TestCase):
    def test_disappearing_proc_entry_is_terminated_but_access_errors_remain_fatal(self):
        for error in (FileNotFoundError(), ProcessLookupError()):
            with patch.object(Path, 'read_text', side_effect=error):
                self.assertFalse(running(12345))
        with patch.object(Path, 'read_text', side_effect=PermissionError()):
            with self.assertRaises(PermissionError):
                running(12345)


@unittest.skipUnless(sys.platform.startswith('linux'), 'Real owned-session checks require Linux process state')
class SharedProcessCleanupTest(unittest.TestCase):
    def run_fixture(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'child.py').write_text(
                "import os,signal,time\nfrom pathlib import Path\n"
                "signal.signal(signal.SIGTERM,signal.SIG_IGN)\n"
                "Path('child.pid').write_text(str(os.getpid()))\n"
                "while True: time.sleep(1)\n")
            ending = {
                'exited': 'sys.exit(0)\n',
                'graceful': "assert sys.stdin.readline().strip() == 'stop'\nPath('stop.received').write_text('yes')\n",
                'running': 'while True: time.sleep(1)\n',
            }[mode]
            (root / 'parent.py').write_text(
                "import subprocess,sys,time\nfrom pathlib import Path\n"
                "subprocess.Popen([sys.executable,'child.py'])\n"
                "while not Path('child.pid').is_file(): time.sleep(.01)\n" + ending)
            process = live.popen([sys.executable, str(root / 'parent.py')], root)
            output = live.OutputPump(process, 'cleanup-' + mode)
            try:
                # Allow a heavily scheduled CI host to start the synthetic child.
                # The post-stop cleanup deadline remains three seconds below.
                deadline = time.monotonic() + 15
                while not (root / 'child.pid').is_file() and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue((root / 'child.pid').is_file(), 'Child never started')
                child = int((root / 'child.pid').read_text())
                self.assertTrue(running(child))
                if mode == 'exited': process.wait(timeout=3)
                live.stop_tree(process, graceful_server=mode == 'graceful')
                output.thread.join(timeout=3)
                deadline = time.monotonic() + 3
                while running(child) and time.monotonic() < deadline: time.sleep(.01)
                self.assertIsNotNone(process.poll(), 'Parent still executing')
                self.assertFalse(running(child), 'TERM-resistant child escaped shared cleanup')
                self.assertFalse(output.thread.is_alive(), 'Output pump retained a descendant pipe')
                self.assertTrue(process.stdin.closed)
                self.assertTrue(process.stdout.closed)
                if mode == 'graceful': self.assertEqual('yes', (root / 'stop.received').read_text())
            finally:
                # Kill only this fixture's owned session if a regression fails.
                try: os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError: pass
                process.wait(timeout=3)
                output.thread.join(timeout=3)
                process.stdin.close()
                if not output.thread.is_alive(): process.stdout.close()

    def test_parent_already_exited_does_not_leave_resistant_child(self):
        self.run_fixture('exited')

    def test_graceful_server_exit_does_not_leave_resistant_child(self):
        self.run_fixture('graceful')

    def test_term_killed_parent_does_not_leave_resistant_child(self):
        self.run_fixture('running')


class WindowsProcessSafetyTest(unittest.TestCase):
    def test_exited_windows_pid_is_not_reused_as_taskkill_target(self):
        process = Mock(pid=12345); process.poll.return_value = 0
        with patch.object(live.os, 'name', 'nt'), patch.object(live.subprocess, 'run') as run:
            live.stop_tree(process)
        run.assert_not_called()
        process.stdin.close.assert_called_once()

    def test_live_windows_process_uses_bounded_native_tree_termination(self):
        process = Mock(pid=12345); process.poll.return_value = None
        with patch.object(live.os, 'name', 'nt'), patch.object(live.subprocess, 'run') as run:
            live.stop_tree(process)
        self.assertEqual(['taskkill', '/PID', '12345', '/T', '/F'], run.call_args.args[0])
        self.assertEqual(10, run.call_args.kwargs['timeout'])
        process.wait.assert_called_once_with(timeout=10)


if __name__ == '__main__': unittest.main()
