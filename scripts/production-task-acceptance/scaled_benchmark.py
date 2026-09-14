"""Synthetic rows in uniquely prefixed LIKE tables; never copies customer data."""
import json
from pathlib import Path
import re
import statistics
import subprocess
import time
from mysql_check import connection, query

PREFIX = 'ptc_accept6652857_'
DIRECTORY = Path(__file__).parent
queries = {p.stem: p.read_text().strip().rstrip(';') for p in (DIRECTORY/'task-center-queries').glob('*.sql')}
tables = sorted(set(re.findall(r'\b(?:from|join)\s+([a-z][a-z_]+)\b', '\n'.join(queries.values()), re.I)) - {'leaves'})
created = []
report = {'dataset': {}, 'queries': {}, 'cleanup': False}

def execute(sql):
    cmd, env = connection()
    result = subprocess.run(cmd, env=env, input='SET SESSION MAX_EXECUTION_TIME=5000;\n'+sql,
                            capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout

try:
    for table in tables:
        assert re.fullmatch('[a-z_]+', table)
        target = PREFIX+table
        execute(f'CREATE TABLE `{target}` LIKE `{table}`;')
        created.append(target)
        columns, _ = query("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.columns WHERE table_schema=database() AND table_name='"+table+"' ORDER BY ORDINAL_POSITION")
        names, values = [], []
        overrides = {'id':'n', 'tenant_id':'IF(n<=8000,1,2)', 'project_id':'n',
                     'created_by':'1+MOD(n,10)', 'execution_id':'n', 'user_id':'1+MOD(n,10)',
                     'batch_id':'1+FLOOR((n-1)/10)', 'episode_id':'n', 'episode_no':'n',
                     'main_project_id':'n', 'run_id':'n', 'generated_by_run_id':'n',
                     'ai_call_log_id':'n', 'round_no':'1', 'operation_type':"'GENERATE_STORYBOARD'",
                     'status':"IF(MOD(n,5)=0,'FAILED','SUCCEEDED')", 'overall_progress':'100',
                     'progress':'100', 'deleted_at':'NULL', 'completed_at':"'2026-09-01 00:00:00'",
                     'created_at':"TIMESTAMPADD(SECOND,n,'2026-01-01 00:00:00')",
                     'updated_at':"'2026-09-01 00:00:00'", 'retryable':'0',
                     'shot_plan_json':"IF(MOD(n,2)=0,'{\"warnings\":[\"synthetic\"]}','{\"warnings\":[]}')"}
        for line in columns.splitlines():
            fields = line.split('\t')
            name, dtype, ctype, nullable, default, extra = fields + ['']*(6-len(fields))
            if 'GENERATED' in extra and 'DEFAULT_GENERATED' not in extra:
                continue
            value = overrides.get(name)
            if value is None:
                if nullable == 'YES' or default != '<NULL>':
                    continue
                if dtype in ('datetime','timestamp','date'):
                    value="'2026-01-01'"
                elif dtype == 'json':
                    value="'{}'"
                elif dtype == 'enum':
                    value=ctype[5:].split(',')[0].rstrip(')')
                elif dtype in ('varchar','char','text','mediumtext','longtext'):
                    value="concat('fixture-',n)" if dtype not in ('char','varchar') or int(re.search(r'\((\d+)\)',ctype)[1])>=15 else "'X'"
                elif dtype in ('binary','varbinary','blob','longblob','mediumblob'):
                    value="concat('fixture-',n)"
                else:
                    value='n'
            names.append('`'+name+'`')
            values.append(value)
        rows = 1000 if table in ('storyboard_batch','video_decomposition_batch') else 10000
        # No result rows are seeded: legacy episode retryability checks should see empty result storage.
        if table == 'video_decomposition_script_result':
            rows = 0
        digits='(select 0 d union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9)'
        numbers=f'(select 1+a.d+10*b.d+100*c.d+1000*d.d n from {digits} a cross join {digits} b cross join {digits} c cross join {digits} d) nums'
        if rows:
            execute(f'INSERT INTO `{target}` ({",".join(names)}) SELECT {",".join(values)} FROM {numbers} WHERE n<={rows};')
        execute(f'ANALYZE TABLE `{target}`;')
        report['dataset'][table]=rows
        print(json.dumps({'seeded':table,'rows':rows}),flush=True)
    for name, sql in sorted(queries.items()):
        for table in sorted(tables,key=len,reverse=True):
            sql=re.sub(r'\b'+table+r'\b',PREFIX+table,sql)
        if name == 'list_last':
            count_sql=queries['count']
            for table in sorted(tables,key=len,reverse=True):
                count_sql=re.sub(r'\b'+table+r'\b',PREFIX+table,count_sql)
            total,_=query(count_sql)
            report['root_total']=int(total)
            report['last_offset']=max(0,int(total)-20)
            sql=sql.replace('offset 100','offset '+str(report['last_offset']))
        plan, _ = query('EXPLAIN ANALYZE '+sql)
        samples=[]
        for _ in range(7):
            output, elapsed=query(sql)
            samples.append(elapsed)
        report['queries'][name]={'median_ms':statistics.median(samples),'max_ms':max(samples),
                                 'client_ms':samples,'result_rows':len(output.splitlines()),'plan':plan}
        print(json.dumps({'query':name,'median_ms':statistics.median(samples),'max_ms':max(samples)}),flush=True)
finally:
    for target in reversed(created):
        assert target.startswith(PREFIX) and re.fullmatch('[a-z0-9_]+',target)
        execute(f'DROP TABLE `{target}`;')
    report['cleanup']=True
    (DIRECTORY/'scaled-performance.json').write_text(json.dumps(report,indent=2))
