"""Hashes only. Raw application rows never leave the host or enter the report."""
import hashlib
import json
from pathlib import Path
import sys
from mysql_check import query

tables=['ai_execution_task','ai_image_task','ai_video_task','review_task','script_ai_operation',
        'video_decomposition_batch','video_decomposition_episode','video_decomposition_analysis',
        'video_decomposition_script_result','ai_point_reservation','point_ledger',
        'storyboard_batch','storyboard_batch_item','ai_image_result','ai_video_result','script_analysis_result']
report={}
for table in tables:
    print(json.dumps({'hashing':table}),flush=True)
    columns,_=query("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=database() AND table_name='"+table+"' ORDER BY ORDINAL_POSITION")
    assert columns,table
    fields=','.join('`'+name+'`' for name in columns.splitlines())
    value,_=query(f'SELECT id,SHA2(CAST(JSON_ARRAY({fields}) AS CHAR),256) FROM `{table}` ORDER BY id')
    count,_=query(f'SELECT count(*) FROM `{table}`')
    report[table]={'rows':int(count),'sha256':hashlib.sha256(value.encode()).hexdigest()}
# A legacy pending row is touched by the existing 8-second scheduler; retain its meaningful state.
value,_=query('SELECT id,tenant_id,status,execution_id FROM script_analysis_task ORDER BY id')
report['script_analysis_task_states']={'sha256':hashlib.sha256(value.encode()).hexdigest()}
value,_=query('SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
report['migrations']={'sha256':hashlib.sha256(value.encode()).hexdigest()}
path=Path(__file__).parent/('snapshot-'+sys.argv[1]+'.json')
path.write_text(json.dumps(report,indent=2))
print(json.dumps(report))
