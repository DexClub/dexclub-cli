# Find Query Recipes

Use this reference when constructing or repairing `find_classes`, `find_methods`, or `find_fields` calls. Read [find-query-fields.md](find-query-fields.md) for the complete public field and enum inventory.

## Contents

- Contract shape
- Common recipes
- Resource ID usage in Dex
- Combining constraints
- Paging and projection
- Repairing rejected queries

## Contract Shape

Construct a `dexclub-query` JSON document and write it to a file before calling the MCP tool.
The examples below show the document's `query` value; wrap the selected value as follows:

```json
{
  "format": "dexclub-query",
  "formatVersion": 1,
  "kind": "find_methods",
  "query": { ... }
}
```

Pass the absolute path to that file in the tool's required `query_file` argument. For example:

```json
{
  "session_id": "session-id",
  "query_file": "D:/analysis/.dexclub/queries/find-login.query.json",
  "version": "<skill version>"
}
```

The file itself contains:

```json
{
  "format": "dexclub-query",
  "formatVersion": 1,
  "kind": "find_methods",
  "query": {
    "searchPackages": ["com.example"],
    "matcher": {}
  }
}
```

The file document shown above is the complete query document. Pass `brief`, `limit`, `offset`, and `fields` as MCP tool arguments, not document fields. The recipes below show only the value of `query`.

All three query roots support:

- `searchPackages`
- `excludePackages`
- `ignorePackagesCase`
- `matcher`
- `findFirst`

Use the live tool schema for the complete recursive Matcher structure and enum values. Do not add `searchInClasses`, `searchInMethods`, or `searchInFields`.

## Common Recipes

### Find methods using a code string

Use a method `usingStrings` matcher for logs, URLs, protocol keys, exception text, and other literals referenced by code.

<!-- query-example:find_methods -->
```json
{
  "matcher": {
    "usingStrings": [
      {
        "value": "billing_error",
        "matchType": "Contains",
        "ignoreCase": false
      }
    ]
  }
}
```

### Find a method by owner and name

Combine method and declaring-class constraints instead of post-filtering result descriptors.

<!-- query-example:find_methods -->
```json
{
  "searchPackages": ["com.example"],
  "matcher": {
    "name": {
      "value": "submit",
      "matchType": "Contains"
    },
    "declaredClass": {
      "className": {
        "value": "Payment",
        "matchType": "Contains"
      }
    }
  }
}
```

### Find a class by package and name

Use root package filters to reduce the native search space before applying structural matchers.

<!-- query-example:find_classes -->
```json
{
  "searchPackages": ["com.example.feature"],
  "excludePackages": ["com.example.feature.generated"],
  "matcher": {
    "className": {
      "value": "Controller",
      "matchType": "EndsWith"
    }
  }
}
```

### Find a field by owner and field name

Use `find_fields` when the clue is state, a constant, a field type, or a read/write relationship.

<!-- query-example:find_fields -->
```json
{
  "matcher": {
    "name": {
      "value": "token",
      "matchType": "Contains",
      "ignoreCase": true
    },
    "declaredClass": {
      "className": {
        "value": "auth",
        "matchType": "Contains",
        "ignoreCase": true
      }
    }
  }
}
```

### Find methods that invoke a known shape

Start relationship queries one layer deep. Add nested relationships only when they test a concrete hypothesis.

<!-- query-example:find_methods -->
```json
{
  "matcher": {
    "invokeMethods": {
      "methods": [
        {
          "name": {
            "value": "verify",
            "matchType": "Equals"
          },
          "declaredClass": {
            "className": {
              "value": "Signature",
              "matchType": "Contains"
            }
          }
        }
      ],
      "matchType": "Contains"
    }
  }
}
```

Use the same pattern with `callerMethods`, `readMethods`, `writeMethods`, nested fields, annotations, parameters, return types, access flags, opcodes, or numeric constants when the live schema exposes them.

## Resource ID Usage in Dex

Android resource IDs are normally referenced from Dex as 32-bit integer constants. To locate methods that directly use a known resId:

1. remove the `0x` prefix
2. parse the remaining hexadecimal digits as an unsigned 32-bit value
3. convert the same 32-bit bit pattern to a signed integer
4. write that signed value as a decimal JSON number in `usingNumbers[].intValue`

JSON does not accept hexadecimal number literals. Do not pass the resId as a string and do not use `longValue` for a 32-bit resource ID.

<!-- resource-id-conversion:0x7f0a0123=2131362083 -->
For example, `0x7f0a0123` becomes decimal `2131362083`:

<!-- query-example:find_methods -->
```json
{
  "matcher": {
    "usingNumbers": [
      {
        "intValue": 2131362083
      }
    ]
  }
}
```

For a value above `0x7fffffff`, subtract `4294967296` to preserve the signed 32-bit bit pattern.

<!-- resource-id-conversion:0x80000000=-2147483648 -->
For example, `0x80000000` becomes `-2147483648`, not a positive `longValue`.

This query finds direct numeric constants, not every semantic resource reference. It can miss non-final `R` fields, values passed through other fields or arrays, computed IDs, `Resources.getIdentifier`, and native-code references.

If the direct numeric query has no result:

1. use `find_fields` to confirm the corresponding `R$<type>` field
2. use the same `FieldMatcher` shape inside `find_methods.matcher.usingFields`
3. set `usingType` to `Read` when looking for field reads

<!-- query-example:find_methods -->
```json
{
  "matcher": {
    "usingFields": [
      {
        "field": {
          "name": {
            "value": "submit_button",
            "matchType": "Equals"
          },
          "declaredClass": {
            "className": {
              "value": "R$id",
              "matchType": "EndsWith"
            }
          }
        },
        "usingType": "Read"
      }
    ]
  }
}
```

## Combining Constraints

- Prefer one meaningful compound query over several broad queries followed by client-side substring filtering.
- Put package inclusion and exclusion at the query root.
- Put names, strings, types, members, annotations, and relationships under `matcher`.
- Begin with the strongest one or two independent constraints.
- Add recursive constraints incrementally so a rejected or empty query remains diagnosable.
- Use `findFirst=true` only for an existence check or a target expected to be unique.
- Avoid empty `{}` queries unless inventory enumeration is the explicit task.

## Paging and Projection

- Use `brief=true` for initial candidate discovery.
- Omit `fields` when brief defaults already provide the next-step identifier.
- Set a small `limit`, such as 10 or 20, for a weak clue.
- Otherwise accept the default 50; the maximum is 200.
- Request `methodHandle` or `classHandle` only with `session_id`.
- Fields have no handle projection.

Treat one page as a window, not a representative sample. The result order is not a relevance score, so an item at offset 800 can be more useful than every item in the first window.

When `hasMore=true`, choose one of these modes explicitly.

### Independent-Clue Narrowing

Add a package, owner, name, string, number, type, annotation, field, caller, or callee constraint supported by evidence that did not come only from superficial similarity in the current page. Rerun from `offset=0` after changing the query. Do not turn a first-page pattern into a self-confirming Matcher.

### Exhaustive Coverage

Use this mode when missing a candidate is more costly than several additional calls and the result set is manageable.

1. Keep the same `session_id`, target snapshot, `query_file` document, `brief`, and `fields` across pages.
2. Prefer `brief=true`, minimal projection, and `limit=200`.
3. Start at `offset=0` and advance by the number of returned items until `hasMore=false`.
4. Track candidates by their complete identity, including source location when duplicate descriptors can occur.
5. Inspect or export only the shortlist produced by the scan.

If the target is refreshed or the query changes, discard the old coverage state and restart at `offset=0`.

### Stratified Exploration

Use this mode only when the result set is too large to exhaust immediately. Sample separated windows, for example at offsets `0`, `total/4`, `total/2`, `3*total/4`, and `max(0, total-limit)`. Deduplicate overlapping windows and use the sample only to discover package, owner, name, or structural clusters for a narrower query.

Stratified exploration does not establish absence, uniqueness, prevalence, or complete coverage.

### Stopping and Conclusions

- Stop with complete coverage only after `hasMore=false`.
- Stop early when an independently narrowed result is small enough and the candidate is verified with `inspect_method` or an appropriate export.
- For an existence question, one verified hit can prove existence but not uniqueness or primacy.
- Never claim that an item is the main or real implementation merely because it appears in the first window.
- When stopping with `hasMore=true`, state the covered count, such as `examined 200/1247`, and keep the conclusion at candidate level.
- Never claim absence from sampling or an incomplete page sequence.

## Repairing Rejected Queries

1. Inspect the current tool schema.
2. Confirm `query_file` points to a UTF-8 query document whose envelope `query` value is an object, not an escaped JSON string.
3. Remove unknown root properties and all `searchIn*` properties.
4. Check exact property spelling and enum casing.
5. Reduce recursive matchers to one valid constraint.
6. Add remaining constraints back one at a time.

Do not replace a rejected complete query with removed flattened parameters or using-strings tools.
