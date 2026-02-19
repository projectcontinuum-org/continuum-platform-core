# Conditional Splitter

Splits input rows into two separate output streams based on a numeric threshold comparison, enabling conditional workflow branching.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with rows to split |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| high | Table | List<Map<String, Any>> | Rows where column >= threshold |
| low | Table | List<Map<String, Any>> | Rows where column < threshold |

## Properties
- **column** (string, required): Column name to compare against threshold
- **threshold** (number, required): Split point value

## Behavior
For each input row:
1. Extracts the value from the specified `column`
2. Converts to number (defaults to 0 if not numeric)
3. Compares to `threshold`:
   - If `value >= threshold` → routes to **high** port
   - If `value < threshold` → routes to **low** port

Each output port maintains independent row numbering starting from 0.

## Use Cases
- Route high-value vs low-value transactions
- Separate pass/fail test results
- Split data for different processing pipelines
- A/B testing based on scores

## Example

**Input:**
```json
[
  {"id": 1, "value": 10},
  {"id": 2, "value": 20},
  {"id": 3, "value": 15}
]
```

**Properties:**
```json
{
  "column": "value",
  "threshold": 15
}
```

**Output (high port):**
```json
[
  {"id": 2, "value": 20},
  {"id": 3, "value": 15}
]
```

**Output (low port):**
```json
[
  {"id": 1, "value": 10}
]
```

Note: value=15 goes to "high" because 15 >= 15 is true.
