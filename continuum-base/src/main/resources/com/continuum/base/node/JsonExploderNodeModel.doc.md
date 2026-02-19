# JSON Exploder

Parses JSON strings from a column and flattens the top-level keys into separate columns, expanding the table structure.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with column containing JSON strings |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Table with JSON keys expanded into separate columns |

## Properties
- **jsonCol** (string, required): Column containing JSON strings to parse and explode

## Behavior
For each row:
1. Reads the JSON string from `jsonCol`
2. Parses it using Jackson ObjectMapper
3. Extracts all top-level keys and values
4. Adds each key-value pair as a new column to the row
5. Removes the original `jsonCol` from the output

**Error Handling:**
- If JSON parsing fails, throws NodeRuntimeException with row number
- Empty JSON strings are handled gracefully (column simply removed)

## Limitations
- Only flattens one level deep (nested objects remain as objects)
- Key conflicts: JSON keys override existing column names

## Example

**Input:**
```json
[
  {"id": 1, "json": "{\"name\": \"Alice\", \"age\": 30}"},
  {"id": 2, "json": "{\"name\": \"Bob\", \"age\": 40}"}
]
```

**Properties:**
```json
{
  "jsonCol": "json"
}
```

**Output:**
```json
[
  {"id": 1, "name": "Alice", "age": 30},
  {"id": 2, "name": "Bob", "age": 40}
]
```

The JSON column is removed and its contents are expanded into `name` and `age` columns.
