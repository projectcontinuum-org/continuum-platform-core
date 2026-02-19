# Join on Multiple Keys

Performs an inner join between two tables using composite keys (two key columns from each table), combining rows where both key pairs match.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| left | Table | List<Map<String, Any>> | Left table with rows to join |
| right | Table | List<Map<String, Any>> | Right table with rows to join |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Joined table containing merged rows where composite keys match |

## Properties
- **leftKey1** (string, required): First key column from the left table
- **leftKey2** (string, required): Second key column from the left table
- **rightKey1** (string, required): First key column from the right table
- **rightKey2** (string, required): Second key column from the right table

## Behavior
1. Reads all rows from both tables into memory
2. Builds a hash map index on the right table using (rightKey1, rightKey2) as composite key
3. For each left row:
   - Looks up matching right rows where `left[leftKey1] == right[rightKey1]` AND `left[leftKey2] == right[rightKey2]`
   - Merges matching rows (combines all columns from both)
   - Writes joined row to output

Uses efficient O(n+m) hash-based lookup instead of O(n*m) nested loops.

## Example

**Left Table:**
```json
[
  {"id": 1, "date": "2026-01-01", "name": "Alice"},
  {"id": 2, "date": "2026-01-02", "name": "Bob"}
]
```

**Right Table:**
```json
[
  {"id": 1, "date": "2026-01-01", "salary": 5000},
  {"id": 2, "date": "2026-01-02", "salary": 6000}
]
```

**Properties:**
```json
{
  "leftKey1": "id",
  "leftKey2": "date",
  "rightKey1": "id",
  "rightKey2": "date"
}
```

**Output:**
```json
[
  {"id": 1, "date": "2026-01-01", "name": "Alice", "salary": 5000},
  {"id": 2, "date": "2026-01-02", "name": "Bob", "salary": 6000}
]
```
