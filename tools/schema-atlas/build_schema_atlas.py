#!/usr/bin/env python3
"""Build the schema atlas HTML from a live database + the template.

Steps: introspect_schema.py (real catalog + row counts) -> inject the model
JSON and the subtitle into schema-atlas-template.html.

Usage: ATLAS_PSQL="docker exec <container> psql -U plannasaas -d plannasaas" python3 build_schema_atlas.py
Output: ATLAS_OUTPUT (default documents/schema-atlas.html in the repository)
"""
import json, os, subprocess, sys, datetime

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
DA   = os.environ.get('ATLAS_OUT_DIR') or HERE

r = subprocess.run([sys.executable, os.path.join(HERE, 'introspect_schema.py')],
                   capture_output=True, text=True)
if r.returncode != 0:
    sys.exit(f'introspection failed: {r.stderr[:400] or r.stdout[:400]}')
print(r.stdout.strip())

model = json.load(open(os.path.join(DA, 'schema-model.json')))
ntab = len(model['tables'])
nfk  = len(model['fks'])
ncol = sum(len(t['columns']) for t in model['tables'])
today = datetime.date.today().strftime('%d %b %Y')
ctx = os.environ.get('ATLAS_CONTEXT', 'the live loaded review database')
subtitle = (f'<b>{ntab} tables · {nfk} key links · {ncol} columns</b> — read from '
            f'{ctx} ({today}): real constraints, real row counts.')

tpl = open(os.path.join(HERE, 'schema-atlas-template.html')).read()
# "</" only occurs inside JSON strings; escape it so comment content can
# never terminate the inline <script> block early.
out = tpl.replace('__SUBTITLE__', subtitle) \
         .replace('__MODEL_JSON__',
                  json.dumps(model, separators=(',', ':')).replace('</', '<\\/'))
dst = os.environ.get('ATLAS_OUTPUT') or os.path.join(REPO, 'documents', 'schema-atlas.html')
open(dst, 'w').write(out)
print(f'wrote {dst} ({len(out)//1024} KB)')
