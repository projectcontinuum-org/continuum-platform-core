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

## Complex Examples

### Example 1: Time-Series Data with Sensor Readings

Joining sensor measurements with calibration data where both sensor ID and timestamp must match.

**Left Table (Sensor Readings):**
```json
[
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T10:00:00", "raw_value": 23.5, "location": "Room A"},
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T11:00:00", "raw_value": 24.1, "location": "Room A"},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T10:00:00", "raw_value": 21.8, "location": "Room B"},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T11:00:00", "raw_value": 22.3, "location": "Room B"}
]
```

**Right Table (Calibration Data):**
```json
[
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T10:00:00", "calibration_offset": 0.5, "accuracy": 0.98},
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T11:00:00", "calibration_offset": 0.6, "accuracy": 0.97},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T10:00:00", "calibration_offset": -0.3, "accuracy": 0.99},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T11:00:00", "calibration_offset": -0.2, "accuracy": 0.99}
]
```

**Properties:**
```json
{
  "leftKey1": "sensor_id",
  "leftKey2": "timestamp",
  "rightKey1": "sensor_id",
  "rightKey2": "timestamp"
}
```

**Output:**
```json
[
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T10:00:00", "raw_value": 23.5, "location": "Room A", "calibration_offset": 0.5, "accuracy": 0.98},
  {"sensor_id": "TEMP_001", "timestamp": "2026-01-15T11:00:00", "raw_value": 24.1, "location": "Room A", "calibration_offset": 0.6, "accuracy": 0.97},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T10:00:00", "raw_value": 21.8, "location": "Room B", "calibration_offset": -0.3, "accuracy": 0.99},
  {"sensor_id": "TEMP_002", "timestamp": "2026-01-15T11:00:00", "raw_value": 22.3, "location": "Room B", "calibration_offset": -0.2, "accuracy": 0.99}
]
```

### Example 2: Multi-Region Financial Transactions

Joining transaction records with exchange rates where both currency pair and date must match.

**Left Table (Transactions):**
```json
[
  {"transaction_id": "TXN001", "from_currency": "USD", "to_currency": "EUR", "date": "2026-02-20", "amount": 1000},
  {"transaction_id": "TXN002", "from_currency": "USD", "to_currency": "GBP", "date": "2026-02-20", "amount": 500},
  {"transaction_id": "TXN003", "from_currency": "USD", "to_currency": "EUR", "date": "2026-02-21", "amount": 1500},
  {"transaction_id": "TXN004", "from_currency": "EUR", "to_currency": "GBP", "date": "2026-02-20", "amount": 800},
  {"transaction_id": "TXN005", "from_currency": "USD", "to_currency": "JPY", "date": "2026-02-20", "amount": 2000}
]
```

**Right Table (Exchange Rates):**
```json
[
  {"base": "USD", "target": "EUR", "rate_date": "2026-02-20", "rate": 0.92, "spread": 0.002},
  {"base": "USD", "target": "GBP", "rate_date": "2026-02-20", "rate": 0.79, "spread": 0.003},
  {"base": "USD", "target": "EUR", "rate_date": "2026-02-21", "rate": 0.93, "spread": 0.002},
  {"base": "EUR", "target": "GBP", "rate_date": "2026-02-20", "rate": 0.86, "spread": 0.0025}
]
```

**Properties:**
```json
{
  "leftKey1": "from_currency",
  "leftKey2": "date",
  "rightKey1": "base",
  "rightKey2": "rate_date"
}
```

**Output (note TXN005 is excluded - no matching rate for USD->JPY on 2026-02-20):**
```json
[
  {"transaction_id": "TXN001", "from_currency": "USD", "to_currency": "EUR", "date": "2026-02-20", "amount": 1000, "base": "USD", "target": "EUR", "rate_date": "2026-02-20", "rate": 0.92, "spread": 0.002},
  {"transaction_id": "TXN002", "from_currency": "USD", "to_currency": "GBP", "date": "2026-02-20", "amount": 500, "base": "USD", "target": "GBP", "rate_date": "2026-02-20", "rate": 0.79, "spread": 0.003},
  {"transaction_id": "TXN003", "from_currency": "USD", "to_currency": "EUR", "date": "2026-02-21", "amount": 1500, "base": "USD", "target": "EUR", "rate_date": "2026-02-21", "rate": 0.93, "spread": 0.002},
  {"transaction_id": "TXN004", "from_currency": "EUR", "to_currency": "GBP", "date": "2026-02-20", "amount": 800, "base": "EUR", "target": "GBP", "rate_date": "2026-02-20", "rate": 0.86, "spread": 0.0025}
]
```

### Example 3: Inventory Management Across Warehouses

Joining product inventory with pricing tiers where both product SKU and warehouse location must match.

**Left Table (Inventory):**
```json
[
  {"sku": "LAPTOP-X1", "warehouse": "NYC", "quantity": 150, "last_restock": "2026-02-15", "shelf": "A12"},
  {"sku": "LAPTOP-X1", "warehouse": "LAX", "quantity": 200, "last_restock": "2026-02-18", "shelf": "B03"},
  {"sku": "MONITOR-Z3", "warehouse": "NYC", "quantity": 75, "last_restock": "2026-02-10", "shelf": "C21"},
  {"sku": "MONITOR-Z3", "warehouse": "LAX", "quantity": 90, "last_restock": "2026-02-12", "shelf": "D15"},
  {"sku": "KEYBOARD-K5", "warehouse": "NYC", "quantity": 500, "last_restock": "2026-02-19", "shelf": "E05"}
]
```

**Right Table (Regional Pricing):**
```json
[
  {"product_sku": "LAPTOP-X1", "region": "NYC", "price": 1299.99, "discount_tier": "standard", "tax_rate": 0.08875},
  {"product_sku": "LAPTOP-X1", "region": "LAX", "price": 1249.99, "discount_tier": "promotional", "tax_rate": 0.0725},
  {"product_sku": "MONITOR-Z3", "region": "NYC", "price": 399.99, "discount_tier": "standard", "tax_rate": 0.08875},
  {"product_sku": "MONITOR-Z3", "region": "LAX", "price": 389.99, "discount_tier": "standard", "tax_rate": 0.0725},
  {"product_sku": "MOUSE-M2", "region": "NYC", "price": 49.99, "discount_tier": "clearance", "tax_rate": 0.08875}
]
```

**Properties:**
```json
{
  "leftKey1": "sku",
  "leftKey2": "warehouse",
  "rightKey1": "product_sku",
  "rightKey2": "region"
}
```

**Output (KEYBOARD-K5 excluded - no pricing data for NYC region):**
```json
[
  {"sku": "LAPTOP-X1", "warehouse": "NYC", "quantity": 150, "last_restock": "2026-02-15", "shelf": "A12", "product_sku": "LAPTOP-X1", "region": "NYC", "price": 1299.99, "discount_tier": "standard", "tax_rate": 0.08875},
  {"sku": "LAPTOP-X1", "warehouse": "LAX", "quantity": 200, "last_restock": "2026-02-18", "shelf": "B03", "product_sku": "LAPTOP-X1", "region": "LAX", "price": 1249.99, "discount_tier": "promotional", "tax_rate": 0.0725},
  {"sku": "MONITOR-Z3", "warehouse": "NYC", "quantity": 75, "last_restock": "2026-02-10", "shelf": "C21", "product_sku": "MONITOR-Z3", "region": "NYC", "price": 399.99, "discount_tier": "standard", "tax_rate": 0.08875},
  {"sku": "MONITOR-Z3", "warehouse": "LAX", "quantity": 90, "last_restock": "2026-02-12", "shelf": "D15", "product_sku": "MONITOR-Z3", "region": "LAX", "price": 389.99, "discount_tier": "standard", "tax_rate": 0.0725}
]
```

## Notes
- This is an **inner join** - only rows with matching composite keys in both tables appear in output
- Rows without matches in either table are excluded from the result
- Column name conflicts are resolved by keeping both columns (as seen in the examples where key columns appear twice with their original names)
- Performance is O(n+m) where n is left table size and m is right table size
