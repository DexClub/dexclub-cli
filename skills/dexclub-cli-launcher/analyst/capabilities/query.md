# Query Capability

Use query commands when you already have a search target or a narrowing signal.

## Available commands

- `find-class`
- `find-method`
- `find-field`

## Core references

- Human-readable matcher reference: `../references/query-json.md`
- Machine-readable schema: `../references/query-json.schema.json`

## Practical defaults

- For `usingStrings`, prefer `Contains` with `ignoreCase: true` unless exact matching is explicitly required.
- For class, method, and field names, prefer `Equals` unless the target naming is unstable.
- Use `searchPackages` before adding deep nested matchers when the package is known.
- Use `invokeMethods` to find candidate callers of a known target method.
- Use `callerMethods` to constrain callees when you already know who calls them.
- Use `usingNumbers` only when you already have candidate constants; it is a filter, not a full constant enumerator.

## Query examples

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-class \
  --input /path/to/app.apk \
  --query-json '{"matcher":{"className":{"value":"MainActivity","matchType":"Equals"}}}'
```

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-method \
  --input /path/to/classes.dex \
  --query-json '{"matcher":{"usingStrings":[{"value":"login","matchType":"Contains","ignoreCase":true}]}}'
```

## Analyst helpers

Build query JSON:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/build_query.py method \
  --declared-class com.example.MainActivity \
  --using-string login
```

Build and execute in one step:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/run_find.py method \
  --input /path/to/app.apk \
  --declared-class com.example.MainActivity \
  --using-string login \
  --limit 20 \
  --output-format json
```
