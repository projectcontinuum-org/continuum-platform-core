# Pivot Columns

Transforms a table by pivoting: unique values from a pivot column become new column headers, with values from a value column filling the cells, grouped by an index column.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table in long format with rows to pivot |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Pivoted table in wide format with unique column values as headers |

## Properties
- **indexCol** (string, required): Column to use as row identifier/index
- **pivotCol** (string, required): Column whose unique values become new column names
- **valueCol** (string, required): Column containing values to fill the pivoted cells

## Behavior
1. Groups all rows by `indexCol` value
2. Identifies all unique values in `pivotCol`
3. For each group, creates a new row with:
   - The `indexCol` value
   - One column for each unique `pivotCol` value
   - Fills cells with corresponding `valueCol` values

This transforms long-format data (many rows) into wide-format data (fewer rows, more columns).

## Example

**Input:**
```json
[
  {"day": "Mon", "type": "sales", "value": 100},
  {"day": "Mon", "type": "costs", "value": 50},
  {"day": "Tue", "type": "sales", "value": 200},
  {"day": "Tue", "type": "costs", "value": 75}
]
```

**Properties:**
```json
{
  "indexCol": "day",
  "pivotCol": "type",
  "valueCol": "value"
}
```

**Output:**
```json
[
  {"day": "Mon", "sales": 100, "costs": 50},
  {"day": "Tue", "sales": 200, "costs": 75}
]
```

The "type" values (sales, costs) became column names, grouped by day.
