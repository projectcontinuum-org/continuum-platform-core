# Column Join Node

Joins two columns from separate left and right tables into a single output column by concatenating values with a space.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| left | Table | List<Map<String, Any>> | Left input table with rows to join |
| right | Table | List<Map<String, Any>> | Right input table with rows to join |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| output | Table | List<Map<String, Any>> | Table containing the joined column from both inputs |

## Properties
- **columnNameLeft** (string, required): The column name from the left table to join
- **columnNameRight** (string, required): The column name from the right table to join
- **outputColumnName** (string, required): The name for the output column containing joined values

## Behavior
For each pair of rows from the left and right tables:
1. Reads the value from `columnNameLeft` in the left table
2. Reads the value from `columnNameRight` in the right table
3. Concatenates them with a space separator
4. Writes the result to a new column named `outputColumnName`

The node processes rows in sequential order, joining row 0 from left with row 0 from right, and so on.

## Example

**Input (Left Table):**
```json
[
  {"name": "Alice"},
  {"name": "Bob"}
]
```

**Input (Right Table):**
```json
[
  {"city": "NY"},
  {"city": "LA"}
]
```

**Properties:**
```json
{
  "columnNameLeft": "name",
  "columnNameRight": "city",
  "outputColumnName": "fullInfo"
}
```

**Output:**
```json
[
  {"fullInfo": "Alice NY"},
  {"fullInfo": "Bob LA"}
]
```
