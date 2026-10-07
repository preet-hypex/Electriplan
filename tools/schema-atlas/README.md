# Schema atlas

[`documents/schema-atlas.html`](../../documents/schema-atlas.html) is an interactive diagram of the
database: every table, column, key link (with crow's-foot cardinality), unique rule and check
constraint, grouped by domain and coloured. It is read from a **live Postgres catalogue**, not parsed
from SQL text, so it shows exactly what the database enforces.

Open it by double-clicking the file. It is self-contained (the model is embedded); only its fonts
come from the internet. Click a table for its detail, hover a line for the key link, scroll to zoom,
drag to pan, or search for any table or column.

## Rebuild it after a schema change

```bash
tools/schema-atlas/regenerate.sh
```

That starts a throwaway Postgres 17, applies every migration in
`backend/src/main/resources/db/migration`, loads the sample data from the schema-rules test (so tables
show row counts), writes `documents/schema-atlas.html` and `schema-model.json`, and removes the
container. Needs Docker and Python 3. Commit the regenerated files with the migration.

**New tables** are grouped by `modules.json` (`"table": "module"`). A table it does not list is
grouped under its migration's name, so add new tables to the map. The modules' names and colours are
in `KNOWN` in `schema-atlas-template.html`.

To draw the atlas from another database instead (e.g. your running local one):

```bash
ATLAS_PSQL="docker compose exec -T db psql -U electriplan -d electriplan" \
ATLAS_CONTEXT="the local database" \
python3 tools/schema-atlas/build_schema_atlas.py
```

| File | Role |
|---|---|
| `introspect_schema.py` | Reads the `electriplan` schema (`ATLAS_SCHEMA`) from a database into `schema-model.json` |
| `build_schema_atlas.py` | Runs the introspector and injects the model into the template |
| `schema-atlas-template.html` | The diagram: layout, search, zoom and pan, detail panel |
| `modules.json` | Which domain each table belongs to |
| `regenerate.sh` | The one-command rebuild above |

Adapted from the Planna One atlas toolkit (`repo/planna/tools`).
