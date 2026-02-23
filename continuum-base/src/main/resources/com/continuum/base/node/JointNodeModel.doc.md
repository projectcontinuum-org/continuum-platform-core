# Joint Node

Combine values from two input columns into a single output column. A specialized version of column joining that requires exactly 2 inputs and concatenates with a space.

---

## What It Does

The Joint Node takes values from two different input streams, concatenates them with a space separator, and creates a single output column. It's designed for combining two specific columns from separate data sources.

**Key Points:**
- ✅ Requires exactly 2 input configurations
- ✅ Joins values from two separate input streams
- ✅ Concatenates with a space separator
- ✅ Automatically converts all data types to text
- ✅ Processes rows pair-wise until one stream ends
- ⚠️ Missing column values become empty strings

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input-1** | First input stream |
| **input-2** | Second input stream |

### Output Ports
| Port | Description |
|------|-------------|
| **output-1** | Stream with joined column values |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **inputs** | Array | Yes | Array of exactly 2 input configurations, each with `columnName` property |
| **outputsColumnName** | String | No | Name for output column (defaults to "message") |

**Input Configuration Format:**
```json
{
  "inputs": [
    {"columnName": "column1"},
    {"columnName": "column2"}
  ]
}
```

---

## How It Works

1. **Validate configuration**
   - Check that `inputs` array exists
   - Verify it contains at least 2 elements
   - Extract `columnName` from first two entries
2. **Open both input streams**
3. **Process rows pair-wise**:
   - Read one row from input-1
   - Read one row from input-2
   - Extract values from specified columns
   - Convert to strings using `toString()`
   - Concatenate with space: `"{value1} {value2}"`
   - Write to output
4. **Stop** when either input stream ends

**Important:** Only the first 2 entries in the `inputs` array are used. Any additional entries are ignored.

---

## Example

Let's join message parts from two different streams.

**Input Stream 1:**

| msg-1 |
|-------|
| Hello |
| Good |
| Nice |

**Input Stream 2:**

| msg-2 |
|-------|
| World |
| Morning |

**Configuration:**
```json
{
  "inputs": [
    {"columnName": "msg-1"},
    {"columnName": "msg-2"}
  ],
  "outputsColumnName": "message"
}
```

**Output:**

| message |
|---------|
| Hello World |
| Good Morning |

**Why only 2 rows?** Input-2 has only 2 rows, so processing stops. "Nice" from Input-1 is never used.

---

## Common Use Cases

- **Message composition**: Combine message fragments from different sources
- **Label creation**: Merge identifiers from separate systems
- **Text merging**: Join text from two independent streams
- **Data enrichment**: Combine data from two real-time sources
- **Multi-source joining**: Pair data from two simultaneous inputs

---

## Configuration Validation

The node validates the `inputs` array:

**Valid:**
```json
{"inputs": [{"columnName": "a"}, {"columnName": "b"}]}
```
✅ Has 2 elements with columnName properties

**Valid (extra ignored):**
```json
{"inputs": [{"columnName": "a"}, {"columnName": "b"}, {"columnName": "c"}]}
```
✅ Has at least 2 elements (third is ignored)

**Invalid:**
```json
{"inputs": [{"columnName": "a"}]}
```
❌ Only 1 element - throws error: "inputs must contain at least 2 elements"

**Invalid:**
```json
{"inputs": []}
```
❌ Empty array - throws error: "inputs must contain at least 2 elements"

**Invalid:**
```json
{}
```
❌ Missing inputs - throws error: "inputs is not provided"

**Invalid:**
```json
{"inputs": [{"columnName": "a"}, {"wrong": "b"}]}
```
❌ Second element missing columnName - throws error: "Input column name 2 is not provided"

---

## Type Conversion

All values are automatically converted to strings using `toString()`:

| Input Type | Input Value | String Output |
|------------|-------------|---------------|
| String | `"hello"` | `"hello"` |
| Integer | `42` | `"42"` |
| Double | `3.14` | `"3.14"` |
| Boolean | `true` | `"true"` |
| Null | `null` | `""` (empty) |

**Examples:**

**Input-1:** `{"id": 100}`
**Input-2:** `{"status": true}`
**Output:** `{"message": "100 true"}`

**Input-1:** `{"value": 3.14}`
**Input-2:** `{"unit": "pi"}`
**Output:** `{"message": "3.14 pi"}`

---

## Tips & Warnings

⚠️ **Exactly 2 Required**
- The `inputs` array must have **at least** 2 elements
- Only the first 2 are used
- Additional entries beyond 2 are silently ignored

⚠️ **Different Stream Lengths**
- If streams have different row counts, the shorter one determines output length
- Extra rows from the longer stream are ignored
- Similar to SQL inner join on row numbers

⚠️ **Missing Columns**
- If a column doesn't exist in a row, it becomes an empty string
- No error is thrown
- `{"col": "hello"}` + missing column → `"hello "` (with trailing space)

⚠️ **Output Structure**
- Output contains **only** the joined column
- Original columns from both inputs are **not preserved**
- If you need original data, split the workflow or use a different node

⚠️ **Sequential Processing**
- Rows are processed in order: row 0, row 1, row 2, etc.
- Not a "join" in the SQL sense (no key matching)
- Simply pairs rows by position

💡 **Default Output Name**
- If `outputsColumnName` is not provided, defaults to `"message"`
- Recommended to always specify for clarity

💡 **Whitespace Handling**
- Values are joined with exactly one space
- No trimming is applied (unlike ColumnJoinNodeModel)
- Empty values: `"" + "world"` → `" world"` (leading space)

---

## Difference from ColumnJoinNodeModel

Both nodes join columns, but there are key differences:

| Feature | JointNodeModel | ColumnJoinNodeModel |
|---------|----------------|---------------------|
| Input Configuration | Array of objects | Two separate properties |
| Validation | Checks array size | Checks individual properties |
| Whitespace | No trimming | Trims result |
| Port Names | input-1, input-2 | left, right |
| Output Name Default | "message" | Required |

**When to use:**
- **JointNodeModel**: When you have dynamic input configurations or need array-based config
- **ColumnJoinNodeModel**: When you want simpler config and trimmed output

---

## Advanced Example: Multi-Type Joining

**Scenario:** Join product ID (number) with status (boolean)

**Input-1 (Products):**

| productId |
|-----------|
| 1001 |
| 1002 |
| 1003 |

**Input-2 (Status):**

| active |
|--------|
| true |
| false |

**Configuration:**
```json
{
  "inputs": [
    {"columnName": "productId"},
    {"columnName": "active"}
  ],
  "outputsColumnName": "label"
}
```

**Output:**

| label |
|-------|
| 1001 true |
| 1002 false |

Product 1003 is not in output because Input-2 ran out of rows.

---

## Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "inputs is not provided" | `inputs` property missing | Add `inputs` array to configuration |
| "inputs must contain at least 2 elements" | Array has < 2 items | Add at least 2 input configurations |
| "Input column name 1 is not provided" | First entry missing `columnName` | Add `columnName` to first input config |
| "Input column name 2 is not provided" | Second entry missing `columnName` | Add `columnName` to second input config |

---

## Technical Details

- **Base Class**: Extends `ProcessNodeModel`
- **Validation**: Runtime array bounds checking
- **Type Conversion**: Uses `toString()` for universal type support
- **Null Handling**: Null values become empty strings via `?: ""`
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup
- **Memory**: Processes one row pair at a time (memory efficient)
- **Logging**: Logs input configuration at INFO level
