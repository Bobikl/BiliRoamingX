"""Read a bounded portion of the local DEX inventory."""
import argparse
import json
import re
import sys
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
p = argparse.ArgumentParser()
p.add_argument('class_pattern')
p.add_argument('--method', default='')
p.add_argument('--ops', action='store_true')
p.add_argument('--no-fields', action='store_true')
p.add_argument('--ops-match', default='')
a = p.parse_args()
data = json.loads((Path(__file__).resolve().parents[1] / 'build/player-inventory/classes.json').read_text(encoding='utf-8'))
for name, cls in data.items():
    if not re.search(a.class_pattern, name):
        continue
    print(name, 'extends', cls['super'], 'implements', cls['interfaces'])
    if not a.no_fields:
        print('fields:', cls['fields'])
    for m in cls['methods']:
        if a.method and not re.search(a.method, m['name']):
            continue
        print(m['name'], m['params'], m['returns'], 'flags', m['flags'])
        if a.ops or a.ops_match:
            print('\n'.join(op for op in m['ops'] if not a.ops_match or re.search(a.ops_match, op)))
