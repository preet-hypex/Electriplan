# Rule packs

The engine's rules are data: YAML files in
[`backend/src/main/resources/rulepacks/`](../backend/src/main/resources/rulepacks/), checked
against JSON Schemas, loaded once when the API starts, and given to every design stage as a
`RuleSet`. Code implements *kinds* of rule; the numbers, clause references and company preferences
live here ([engine plan §2.3–2.4, §6.9](electrical-engine-plan.md#2-ground-rules)). Built in E0-S3.

```
rulepacks/
  schema/
    meta.schema.json         what meta.yaml must look like
    rule-file.schema.json    what a rule file must look like
  au-residential/
    meta.yaml                id, version, status, standards editions, files, state sign-offs
    wet-areas.yaml  protection.yaml  outlets.yaml  switches.yaml  smoke.yaml    national rules
    state/VIC.yaml           Victoria's additions and overrides
```

## A rule

```yaml
- id: wet.gpo.zone2                   # unique, dotted, lowercase
  tier: mandatory                     # mandatory | regulatory | policy
  kind: exclusion                     # exclusion | clearance | count_per | value | table | limit | requires
  cite: "AS/NZS 3000:2018 cl. 6.2.4"  # where the value comes from; never the standard's text
  message: "Socket outlets and switches are not permitted in zone 2 of a bath or shower"
  verified: false                     # checked against the licensed standard? (default false: the plan's ⚠)
  applies_to: [gpo-single, gpo-double, switch]    # everything else is a parameter of the kind
  zone: bath-zone-2
```

`id`, `tier`, `kind` and `cite` are required; a rule without one is refused. Parameters
(`value_mm`, `applies_to`...) may be text, numbers, true/false, lists or maps, never null. Policy
rules' ids start with `policy.`.

| Tier | Is | Lives in | Who may change it |
|---|---|---|---|
| `mandatory` | The Wiring Rules, NCC, state law. A violation is an error and blocks export | national or state files | a state file may change its values (`override`), never make it `policy` |
| `regulatory` | State variations, distributor service rules | state files only | — |
| `policy` | Good practice, company preference. A violation is a warning | national or state files | a state file; a company (values only) |

## Precedence

For a house in a state, the rules are:

1. **National**: every rule in the files `meta.yaml` lists under `national`.
2. **The state's file** adds its own rules, and replaces a national rule with the same id **only if
   it says `override: true`**. A state rule that reuses a national id without saying so, an
   override with nothing to replace, an override of a different kind, and an override that makes a
   mandatory rule a policy rule are all refused when the pack loads.
3. **Company policy** (`RuleSet.withCompanyPolicy`) changes the **values** of **policy** rules:
   never a mandatory or regulatory rule, never a rule's tier, kind or citation, only values the
   rule has, and only to values of the same type. The changed rule's `source` names the company.
   (Where companies keep their policy is E0-S7.)

Rules are sorted by id, so the same pack, state and policy always give the same `RuleSet`.

## Which states the engine designs

**The states whose file is signed off, and unchanged since.** No code names a state
(`NoStateInCodeTest` fails if any does). `meta.yaml` records each sign-off:

```yaml
states:
  VIC:
    file: state/VIC.yaml
    signOff:
      by: Pat Sparks              # a licensed electrician from that state
      licence: REC-12345
      date: 2026-11-02
      sha256: 3a7bd3e2360a3d...   # of state/VIC.yaml as signed: shasum -a 256 state/VIC.yaml
```

Change the file afterwards and the sign-off no longer matches: the state stops being supported
(the API logs why at startup) until it is signed off again. A state with no file is never
designed. Asking for one throws `UnsupportedStateException`: *"Electriplan does not design NSW
houses yet: the au-residential rule pack has no rules for it"*.

**Development.** Nothing is signed off yet (E16-S4), so with production settings the engine
designs no state at all. `RULES_ALLOW_UNSIGNED_STATES=true` (set in `docker-compose.yml` for local
use, `electriplan.rules.allow-unsigned-states`) also designs states with an unsigned file; their
`RuleSet` says `signedOff: false`, and the API warns at startup. Never set it in production.

## Adding or changing rules

1. Edit or add the YAML. A new national file goes in `meta.yaml`'s `national` list; a new state is
   `state/<STATE>.yaml` plus an entry under `states`.
2. Run the API tests (`./scripts/test-api.sh`, or `-Dtest='com.hypex.electriplan.rules.**.*'`).
   `AuResidentialPackTest` loads the real pack; a pack with any problem also stops the API from
   starting, listing every problem with its file and rule:
   ```
   Rule pack au-residential is not valid:
     - wet-areas.yaml: $.rules[4]: required property 'cite' not found (rule wet.gpo.zone2)
     - state/VIC.yaml: rule wet.zone.height has the id of a national rule (au-residential/wet-areas.yaml): add 'override: true' to replace it, or give it its own id
   ```
3. A changed state file needs signing off again.
4. Mark a value `verified: true` only once it has been checked against a licensed copy of the
   standard (E0-S8). The standards register and the release checks are E0-S6.

## In code

| Type | What |
|---|---|
| `RuleBook` (Spring bean) | The loaded pack; `designableStates()`, `rulesFor(state)` |
| `RuleSet` | The rules for one state: `rule(id)`, `find(id)`, `tier(tier)`, `ref()` (what a design records), `signedOff()`, `withCompanyPolicy(policy)` |
| `Rule` | `id`, `tier`, `kind`, `cite`, `message`, `verified`, `parameters`, `source`; `number("value_mm")`, `text("zone")` |
| `RulePackLoader` | Plain Java: reads and checks a pack from a `RulePackSource` (classpath, or in memory for tests) |

A design stage reads its rules from `context.rules()`; `DesignInput` refuses rules for a different
state from the brief's.
