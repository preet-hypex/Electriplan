#!/usr/bin/env python3
"""Introspect a Postgres schema into schema-model.json for the atlas.

Adapted from the Planna One atlas toolkit (repo/planna/tools) for plannaSaas:
the schema is configurable (ATLAS_SCHEMA, default "app") and tables can be
grouped into modules by a JSON map (ATLAS_MODULES), since one migration may
hold several domains.

Reads the REAL catalog (pg_catalog) of the database built from final-schema/,
so the atlas shows exactly what Postgres enforces: columns+types, PKs, FKs
(with nullability -> optionality, uniqueness -> 1:1), unique constraints,
partial unique indexes, checks, comments, and live dev-data row counts.

Connection (in priority order):
  ATLAS_PSQL="psql -h 127.0.0.1 -p 5433 -U plannasaas -d plannasaas"   # any psql command
  (default) the local Docker database: docker compose exec -T db psql -U plannasaas -d plannasaas

Module attribution:
  ATLAS_MIGRATIONS=<dir of Flyway V*.sql>  -> module = migration name suffix
  (default) backend/src/main/resources/db/migration

Schema:   ATLAS_SCHEMA=app (default)
Modules:  ATLAS_MODULES=<json file {"table": "module"}> (default modules.json
          next to this script); tables it does not list fall back to the
          migration name.
Output:   ATLAS_OUT_DIR/schema-model.json
"""
import json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DA   = os.environ.get('ATLAS_OUT_DIR') or HERE
SCHEMA = os.environ.get('ATLAS_SCHEMA', 'app')
if not re.fullmatch(r'[a-z_][a-z0-9_]*', SCHEMA):
    sys.exit(f'ATLAS_SCHEMA must be a plain identifier, not {SCHEMA!r}')
PSQL = os.environ.get('ATLAS_PSQL', '').split() or \
       ['docker', 'compose', 'exec', '-T', 'db', 'psql', '-U', 'plannasaas', '-d', 'plannasaas']

def psql(sql):
    r = subprocess.run(PSQL + ['-X', '-Atc', sql], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f'psql failed: {r.stderr[:400]}')
    return r.stdout.strip()

def creates(path):
    # Accept both "CREATE TABLE t" and schema-qualified "CREATE TABLE app.t".
    return re.findall(r'^CREATE TABLE (?:IF NOT EXISTS )?(?:\w+\.)?(\w+)', open(path).read(), re.M)

table_module = {}
mig_dir = os.environ.get('ATLAS_MIGRATIONS') or os.path.normpath(
    os.path.join(HERE, '..', '..', 'backend', 'src', 'main', 'resources', 'db', 'migration'))
if mig_dir:
    # module = Flyway migration name suffix; future migrations map themselves
    ALIAS = {'supabase_user': 'platform'}
    def vkey(f): return int(re.match(r'V(\d+)__', f).group(1))
    for f in sorted((f for f in os.listdir(mig_dir) if re.match(r'V\d+__.+\.sql$', f)), key=vkey):
        mod = re.match(r'V\d+__(.+)\.sql$', f).group(1)
        for t in creates(os.path.join(mig_dir, f)):
            table_module[t] = ALIAS.get(mod, mod)
else:
    MODULES = ['platform', 'product', 'inventory', 'sales', 'pricing',
               'procurement', 'financials', 'audit']
    for i, mod in enumerate(MODULES, 1):
        for t in creates(os.path.join(DA, 'final-schema', f'{i:03d}_{mod}.sql')):
            table_module[t] = mod

modules_file = os.environ.get('ATLAS_MODULES') or os.path.join(HERE, 'modules.json')
if os.path.exists(modules_file):
    table_module.update(json.load(open(modules_file)))

model = json.loads(psql(r"""
WITH cols AS (
  SELECT c.oid, a.attnum, a.attname,
         pg_catalog.format_type(a.atttypid, a.atttypmod) AS typ,
         NOT a.attnotnull AS nullable,
         a.attidentity <> '' AS identity,
         pg_get_expr(d.adbin, d.adrelid) AS dflt,
         col_description(c.oid, a.attnum) AS comment
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = '__SCHEMA__'
  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
  LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
  WHERE c.relkind = 'r'
),
pk AS (
  SELECT conrelid AS oid, conkey FROM pg_constraint WHERE contype = 'p'
),
uq AS (  -- unique constraints AND unique indexes (incl. partial)
  SELECT i.indrelid AS oid,
         (SELECT array_agg(a.attname ORDER BY k.ord)
            FROM unnest(i.indkey::int[]) WITH ORDINALITY k(attnum, ord)
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum) AS cols,
         pg_get_expr(i.indpred, i.indrelid) AS pred,
         ic.relname AS name
  FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid
  JOIN pg_class tc ON tc.oid = i.indrelid
  JOIN pg_namespace n ON n.oid = tc.relnamespace AND n.nspname = '__SCHEMA__'
  WHERE i.indisunique AND NOT i.indisprimary
),
ck AS (
  SELECT conrelid AS oid, conname, pg_get_constraintdef(pg_constraint.oid) AS def
  FROM pg_constraint
  JOIN pg_class c ON c.oid = conrelid
  JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = '__SCHEMA__'
  WHERE contype = 'c'
)
SELECT json_build_object(
 'tables', (
  SELECT json_agg(json_build_object(
    'name', c.relname,
    'comment', obj_description(c.oid),
    'columns', (SELECT json_agg(json_build_object(
                  'name', attname, 'type', typ, 'nullable', nullable,
                  'identity', identity, 'default', dflt, 'comment', comment)
                ORDER BY attnum) FROM cols WHERE cols.oid = c.oid),
    'pk', (SELECT array_agg(a.attname ORDER BY k.ord)
             FROM pk, unnest(pk.conkey) WITH ORDINALITY k(attnum, ord)
             JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = k.attnum
            WHERE pk.oid = c.oid),
    'uniques', (SELECT json_agg(json_build_object('cols', cols, 'pred', pred, 'name', name))
                  FROM uq WHERE uq.oid = c.oid),
    'checks', (SELECT json_agg(json_build_object('name', conname, 'def', def))
                 FROM ck WHERE ck.oid = c.oid)
  ) ORDER BY c.relname)
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = '__SCHEMA__' AND c.relkind = 'r'
 ),
 'fks', (
  SELECT json_agg(json_build_object(
    'child', cc.relname,
    'childCols', (SELECT array_agg(a.attname ORDER BY k.ord)
                    FROM unnest(f.conkey) WITH ORDINALITY k(attnum, ord)
                    JOIN pg_attribute a ON a.attrelid = f.conrelid AND a.attnum = k.attnum),
    'parent', pc.relname,
    'parentCols', (SELECT array_agg(a.attname ORDER BY k.ord)
                     FROM unnest(f.confkey) WITH ORDINALITY k(attnum, ord)
                     JOIN pg_attribute a ON a.attrelid = f.confrelid AND a.attnum = k.attnum),
    'onDelete', CASE f.confdeltype WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL'
                                   WHEN 'r' THEN 'RESTRICT' ELSE NULL END,
    'name', f.conname))
  FROM pg_constraint f
  JOIN pg_class cc ON cc.oid = f.conrelid
  JOIN pg_class pc ON pc.oid = f.confrelid
  JOIN pg_namespace n ON n.oid = cc.relnamespace AND n.nspname = '__SCHEMA__'
  WHERE f.contype = 'f'
 )
)""".replace('__SCHEMA__', SCHEMA)))

# live row counts (exact; a few dozen tables is cheap)
counts_sql = ' UNION ALL '.join(
    f"SELECT '{t['name']}', count(*) FROM {SCHEMA}.{t['name']}" for t in model['tables'])
counts = dict((line.split('|')[0], int(line.split('|')[1]))
              for line in psql(counts_sql).splitlines() if line)

for t in model['tables']:
    t['module'] = table_module.get(t['name'], 'migration')
    t['rows'] = counts.get(t['name'], 0)

out = os.path.join(DA, 'schema-model.json')
json.dump(model, open(out, 'w'))
nfk = len(model['fks'])
print(f"{len(model['tables'])} tables, {nfk} FKs, "
      f"{sum(len(t['columns']) for t in model['tables'])} columns -> {out}")
