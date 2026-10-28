"""Summarize every retained run, without selecting favorable repetitions."""
import argparse
import csv
import json
from pathlib import Path


def quantile(values, fraction):
    ordered = sorted(values)
    return ordered[int((len(ordered) - 1) * fraction)] / 1_000_000


def report(root):
    runs = {}
    for directory in sorted(root.iterdir()):
        if not directory.is_dir():
            continue
        scenes = {}
        for path in sorted(directory.glob('*.csv')):
            rows = list(csv.DictReader(path.open()))
            scenes[path.stem.split('-', 1)[1]] = {
                field: {'median_ms': quantile([int(row[field]) for row in rows], .5),
                        'p95_ms': quantile([int(row[field]) for row in rows], .95)}
                for field in rows[0] if field != 'sample'
            }
        if scenes:
            runs[directory.name] = scenes
    (root / 'summary.json').write_text(json.dumps(runs, indent=2) + '\n')
    lines = ['# Repeated actual Minecraft measurements', '',
             'All values are milliseconds; each cell is median / p95. Every run is included.',
             'Preview GPU timestamps measure a submission span, including pipeline idle time; '
             'they are not isolated GPU busy time. Particle scope is the complete particle engine '
             'in the otherwise empty fixture; shader scope is the zone draw method.', '',
             '| Run | Scene | Whole CPU | Whole GPU | Frame interval | Preview CPU | Preview GPU span |',
             '|---|---|---:|---:|---:|---:|---:|']
    for run, scenes in runs.items():
        for scene, fields in scenes.items():
            cells = []
            for field in ('cpu_render_ns', 'gpu_render_ns', 'frame_interval_ns', 'cpu_preview_ns', 'gpu_preview_span_ns'):
                value = fields[field]
                cells.append(f"{value['median_ms']:.3f} / {value['p95_ms']:.3f}")
            lines.append('| ' + ' | '.join([run, scene] + cells) + ' |')
    (root / 'comparison.md').write_text('\n'.join(lines) + '\n')
    return runs


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    report(parser.parse_args().directory)
