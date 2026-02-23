# Column Join Node

Combine text from two separate tables into a single output column. Perfect for creating full names from first and last names, merging addresses, or joining any two text columns from different data sources.

---

## What It Does

The Column Join Node takes one column from a left table and one column from a right table, and concatenates them together with a space in between. It processes rows in pairs (row 0 from left + row 0 from right, row 1 + row 1, etc.) and stops when either table runs out of rows.

**Key Points:**
- ✅ Joins columns from two separate input tables
- ✅ Concatenates with a space separator
- ✅ Automatically converts all data types to text (numbers, booleans, etc.)
- ✅ Processes rows pair-wise in sequential order
- ⚠️ Stops when the shorter table ends
- ⚠️ Missing column values become empty strings

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **left** | Left input table containing the first column to join |
| **right** | Right input table containing the second column to join |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | New table with the joined column (original columns are not included) |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **columnNameLeft** | String | Yes | Name of the column from the left table |
| **columnNameRight** | String | Yes | Name of the column from the right table |
| **outputColumnName** | String | Yes | Name for the new output column containing joined values |

---

## How It Works

1. **Read row 0** from both the left and right tables
2. **Extract values** from the specified columns
3. **Convert to text** (numbers like 42 become "42", booleans like true become "true")
4. **Concatenate** with a space: `"{left value} {right value}"`
5. **Trim whitespace** from the result
6. **Write** to a new row with only the joined column
7. **Repeat** for row 1, row 2, etc. until one table ends

**Row Numbering:** Output rows are numbered sequentially starting from 0.

---

## Example

Let's join first names and last names from two different tables.

**Left Table (first names):**

| name |
|------|
| Alice |
| Bob |
| Charlie |

**Right Table (last names):**

| city |
|------|
| NYC |
| LA |

**Configuration:**
- **columnNameLeft**: `name`
- **columnNameRight**: `city`
- **outputColumnName**: `fullInfo`

**Output Table:**

| fullInfo |
|----------|
| Alice NYC |
| Bob LA |

**Why only 2 rows?**
The right table only has 2 rows, so processing stops after the second row. Charlie from the left table is never processed.

---

## Common Use Cases

- **Name merging**: Combine first and last names
- **Address building**: Join street names with city names
- **Label creation**: Merge product codes with descriptions
- **Data enrichment**: Combine data from two separate sources
- **Report formatting**: Create formatted strings from multiple columns

---

## Tips & Warnings

⚠️ **Different Table Lengths**
- If tables have different numbers of rows, the extra rows from the longer table are **ignored**
- Similar to a SQL inner join on row numbers

⚠️ **Missing Columns**
- If a column doesn't exist in a row, it's treated as an empty string
- No error is thrown

⚠️ **Type Conversion**
- All data types are automatically converted to strings using `toString()`
- Numbers: `42` → `"42"`
- Booleans: `true` → `"true"`
- Null values: → `""` (empty string)

💡 **Output Structure**
- The output contains **only** the joined column
- Original columns from left and right tables are **not preserved**
- If you need the original data, use a different node or split the workflow

💡 **Separator**
- Values are joined with a single space character
- Trailing/leading whitespace is automatically trimmed
- Empty values don't add extra spaces

---

## Example: Joining Numbers and Text

**Left Table:**

| id |
|----|
| 100 |
| 200 |

**Right Table:**

| status |
|--------|
| active |

**Configuration:**
- **columnNameLeft**: `id`
- **columnNameRight**: `status`
- **outputColumnName**: `label`

**Output:**

| label |
|-------|
| 100 active |

Note: The number 100 was automatically converted to the string "100".

---

## Technical Details

- **Algorithm**: Pair-wise row processing with streaming readers
- **Type Safety**: Uses `toString()` for universal type conversion
- **Whitespace**: Final result is trimmed using `.trim()`
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup
- **Memory**: Processes one row at a time (suitable for large datasets)
