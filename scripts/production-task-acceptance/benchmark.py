"""Bounded serial benchmark, capturing plans but no application row bodies."""
import json
from pathlib import Path
import statistics
import sys
from mysql_check import query

directory = Path(sys.argv[1])
report = {}
for path in sorted(directory.glob('*.sql')):
    sql = path.read_text().strip().rstrip(';')
    plan, _ = query('EXPLAIN ANALYZE '+sql)
    samples = []
    row_count = 0
    for _ in range(7):
        output, elapsed = query(sql)
        samples.append(elapsed)
        row_count = len(output.splitlines()) if output else 0
    report[path.stem] = {'client_ms': samples, 'median_ms': statistics.median(samples),
                         'max_ms': max(samples), 'result_rows': row_count, 'plan': plan}
    print(json.dumps({'query':path.stem, 'median_ms': statistics.median(samples), 'max_ms':max(samples), 'result_rows':row_count}), flush=True)
(directory.parent/'performance.json').write_text(json.dumps(report, indent=2))
