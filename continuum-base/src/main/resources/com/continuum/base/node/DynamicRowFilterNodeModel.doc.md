# Dynamic Row Filter

Filter your data to keep only rows that meet a numeric threshold requirement. Perfect for finding high-value transactions, filtering by score, or isolating records above a certain limit.

---

## What It Does

The Dynamic Row Filter examines each row in your input table and keeps only those where a numeric column value is **strictly greater than** a threshold you specify. All other rows are discarded.

**Key Points:**
- ✅ Only rows with value **> threshold** pass through (not equal to)
- ✅ All columns from matching rows are preserved
- ✅ Row numbering is sequential starting from 0
- ⚠️ Missing or non-numeric values default to 0 with a warning

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table with rows to filter |

### Output Ports
| Port | Description |
|------|-------------|
| **data** | Filtered table containing only rows that passed the threshold |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **columnName** | String | Yes | The name of the numeric column to compare |
| **threshold** | Number | Yes | The threshold value (only values **greater than** this pass) |

---

## How It Works

1. **Read each row** from the input table
2. **Extract the value** from the column you specified
3. **Convert to number** (if the column is missing or not numeric, it defaults to 0)
4. **Compare**: If `value > threshold`, the row passes through
5. **Output** only the rows that passed the test

**Important:** The comparison is **strictly greater than** (>), not greater than or equal (>=). So if threshold is 30, values of exactly 30 will be filtered out.

---

## Example

Let's say you want to find all people over 30 years old.

**Input Table:**

| id | age | name |
|----|-----|------|
| 1 | 25 | Alice |
| 2 | 35 | Bob |
| 3 | 30 | Charlie |

**Configuration:**
- **columnName**: `age`
- **threshold**: `30`

**Output Table:**

| id | age | name |
|----|-----|------|
| 2 | 35 | Bob |

**Why?**
- Alice (25): 25 > 30 = ❌ Excluded
- Bob (35): 35 > 30 = ✅ Included
- Charlie (30): 30 > 30 = ❌ Excluded (equal doesn't pass)

---

## Common Use Cases

- **High-value filtering**: Keep only transactions above $1000
- **Performance thresholds**: Filter test results to show only scores above 80%
- **Age restrictions**: Select records for people over a certain age
- **Quality control**: Keep only products with ratings above 4.0
- **Data reduction**: Reduce dataset size by removing low-value records

---

## Tips & Warnings

⚠️ **Important Comparison Behavior**
- The filter uses **strictly greater than** (>), not (>=)
- If you need "greater than or equal", set threshold to 0.99 less than your target

⚠️ **Missing Columns**
- If a row doesn't have the specified column, it defaults to 0
- A warning will be logged for each missing column

⚠️ **Non-Numeric Values**
- Text, booleans, or null values default to 0
- Make sure your column contains numeric data

💡 **Performance Tip**
- This node reduces data volume, making downstream processing faster
- Place it early in your workflow to improve overall performance

---

## Technical Details

- **Algorithm**: Sequential row-by-row evaluation with streaming output
- **Type Conversion**: Uses safe Number type casting with 0.0 default
- **Logging**: Warns when columns are missing or non-numeric
- **Memory**: Processes one row at a time (suitable for large datasets)
