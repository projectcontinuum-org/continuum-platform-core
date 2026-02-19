# Dynamic Row Filter

Filters table rows based on a numeric threshold comparison, keeping only rows where the specified column value is greater than the threshold.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with rows to filter |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Filtered table containing only rows where value > threshold |

## Properties
- **columnName** (string, required): The column name to compare against the threshold
- **threshold** (number, required): The numeric threshold value for comparison

## Behavior
Iterates through each row and:
1. Reads the value from the specified `columnName`
2. Converts it to a number (defaults to 0 if not a number)
3. Compares: if `value > threshold`, includes the row in output
4. If `value <= threshold`, excludes the row from output

Only matching rows are written to the output, reducing data volume for downstream processing.

## Example

**Input:**
```json
[
  {"id": 1, "age": 25, "name": "Alice"},
  {"id": 2, "age": 35, "name": "Bob"},
  {"id": 3, "age": 28, "name": "Charlie"}
]
```

**Properties:**
```json
{
  "columnName": "age",
  "threshold": 30
}
```

**Output:**
```json
[
  {"id": 2, "age": 35, "name": "Bob"}
]
```

Only Bob's row is included because age (35) > 30.
