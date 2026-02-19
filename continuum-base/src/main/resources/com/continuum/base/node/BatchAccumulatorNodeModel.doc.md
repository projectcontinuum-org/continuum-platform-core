# Batch Accumulator

Groups consecutive rows into fixed-size batches and adds batch metadata (batch ID and row count) to each row.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with rows to batch |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table plus batch_id and row_count columns |

## Properties
- **batchSize** (number, required): Number of rows per batch (minimum: 1)

## Behavior
Uses a two-pass algorithm:

**First Pass:**
1. Reads all rows into memory
2. Calculates total number of batches needed
3. Determines row count for each batch (last batch may be smaller)

**Second Pass:**
1. For each row, calculates: `batch_id = floor(row_index / batchSize) + 1`
2. Adds two new columns:
   - `batch_id` (integer): Batch number starting from 1
   - `row_count` (integer): Total rows in this batch
3. Writes enriched row to output

## Use Cases
- Prepare data for batch processing
- Pagination for API requests
- Distributed processing coordination
- Rate limiting and throttling

## Example 1: Equal Batches

**Input:**
```json
[
  {"id": 1},
  {"id": 2},
  {"id": 3},
  {"id": 4}
]
```

**Properties:**
```json
{
  "batchSize": 2
}
```

**Output:**
```json
[
  {"id": 1, "batch_id": 1, "row_count": 2},
  {"id": 2, "batch_id": 1, "row_count": 2},
  {"id": 3, "batch_id": 2, "row_count": 2},
  {"id": 4, "batch_id": 2, "row_count": 2}
]
```

## Example 2: Partial Last Batch

**Input:** 5 rows, batchSize=2

**Output:**
```json
[
  {"id": 1, "batch_id": 1, "row_count": 2},
  {"id": 2, "batch_id": 1, "row_count": 2},
  {"id": 3, "batch_id": 2, "row_count": 2},
  {"id": 4, "batch_id": 2, "row_count": 2},
  {"id": 5, "batch_id": 3, "row_count": 1}
]
```

Note: Last batch has only 1 row, reflected in `row_count`.
