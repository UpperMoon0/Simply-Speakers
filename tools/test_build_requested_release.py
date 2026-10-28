import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build_requested_release as release


class RequestedBuildRepositoryTest(unittest.TestCase):
    def test_forge_fallback_reaches_dependencies_and_consumer_only_on_1165(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs').mkdir()
            pins = {'targets': ['forge-1.16.5', 'fabric-1.16.5'],
                    'openui': {'version': '0.0.15'}, 'packetweave': {'version': '0.1.4'}}
            (root / 'docs/requested-port-dependencies.json').write_text(json.dumps(pins))
            with patch.dict(os.environ, {'JAVA_HOME_17_X64': '/jdk17', 'JAVA_HOME_25_X64': '/jdk25'}):
                for target in pins['targets']:
                    with self.subTest(target=target), patch.object(release, 'verify'), \
                            patch.object(release, 'verify_jar'), patch.object(release.subprocess, 'run') as run:
                        release.build(target, root)
                        self.assertEqual(run.call_count, 3)
                        for call in run.call_args_list:
                            command = call.args[0]
                            self.assertEqual('--init-script' in command, target == 'forge-1.16.5')
                            if target == 'forge-1.16.5':
                                self.assertEqual(command[command.index('--init-script') + 1],
                                                 str(root / 'tools/forge_1165_repositories.gradle'))
                            self.assertTrue(call.kwargs['check'])
                            self.assertIn('--no-parallel', command)


if __name__ == '__main__':
    unittest.main()
