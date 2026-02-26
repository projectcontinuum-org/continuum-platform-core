# Column Splitter (Split Node)

Split text from a single column into multiple output columns by separating on spaces. Dynamically creates output ports based on how many parts are found in your data.

---

## What It Does

The Column Splitter takes a text column and splits it into separate parts using space as a delimiter. It intelligently creates output ports as needed - if it encounters data with 2 parts, it creates 2 outputs; if it later finds 3 parts, it adds a third output port dynamically.

**Key Points:**
- ✅ Splits text on space character
- ✅ Dynamically creates output ports as needed
- ✅ Maximum 2 parts per split (limit = 2)
- ✅ Each output contains one part of the split
- ✅ Only creates ports when data is available
- ⚠️ Port creation is intentional for conditional flow control

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input-1** | Input stream with text column to split |

### Output Ports
| Port | Description |
|------|-------------|
| **output-1** | First part of the split text |
| **output-2** | Second part of the split text (created dynamically if needed) |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **columnName** | String | Yes | Name of the column containing text to split |
| **outputs** | Array | Yes | Array defining output column names (one per expected part) |

**Output Configuration Format:**
```json
{
  "columnName": "message",
  "outputs": [
    {"columnName": "message-1"},
    {"columnName": "message-2"}
  ]
}
```

---

## How It Works

1. **Read each row** from input stream
2. **Extract text** from specified column
3. **Split on space** with limit of 2 (max 2 parts)
4. **Check parts count** - if more parts than output ports exist:
   - **Create new output port** dynamically
5. **Write each part** to its corresponding output port
6. **Repeat** for all rows

**Important Split Behavior:**
- Uses `.split(" ", limit = 2)` which splits into **at most 2 parts**
- First part: Everything before first space
- Second part: Everything after first space (may contain more spaces)

---

## Dynamic Port Creation

This node intentionally creates output ports **only when data is available**. This design enables conditional workflow execution:

- If input has 1-part text → Only `output-1` created
- If input has 2-part text → Both `output-1` and `output-2` created
- Workflow only propagates down paths where ports exist

**Design Intent:** Prevents workflow from continuing on paths where no data exists, functioning like a conditional gate.

---

## Example

Let's split full names into first and last names.

**Input:**

| id | fullName |
|----|----------|
| 1 | John Doe |
| 2 | Jane Smith |
| 3 | Alice |

**Configuration:**
```json
{
  "columnName": "fullName",
  "outputs": [
    {"columnName": "firstName"},
    {"columnName": "lastName"}
  ]
}
```

**Output-1 (First Part):**

| firstName |
|-----------|
| John |
| Jane |
| Alice |

**Output-2 (Second Part):**

| lastName |
|----------|
| Doe |
| Smith |
| *(empty - Alice has no last name)* |

**Note:** Output-2 is created because at least one row (John/Jane) has a second part.

---

## Common Use Cases

- **Name splitting**: Split full names into first/last names
- **Address parsing**: Split combined address into street and city
- **Code splitting**: Separate prefix codes from IDs
- **Key-value pairs**: Split "key value" format strings
- **Command parsing**: Split command from arguments
- **Path splitting**: Separate directory from filename

---

## Understanding the Split Limit

The split uses `limit = 2`, which means it splits into **at most 2 parts**:

**Examples:**

| Input | Part 1 | Part 2 |
|-------|--------|--------|
| `"Hello World"` | `"Hello"` | `"World"` |
| `"Hello World Today"` | `"Hello"` | `"World Today"` |
| `"Hello"` | `"Hello"` | *(none)* |
| `"A B C D"` | `"A"` | `"B C D"` |

**Key Point:** Only the first space acts as a separator. All subsequent spaces remain in the second part.

---

## Advanced Example: Command Parsing

**Scenario:** Split commands from their arguments

**Input:**

| command |
|---------|
| PRINT Hello World |
| SAVE myfile.txt |
| DELETE temp_data extra args |

**Configuration:**
```json
{
  "columnName": "command",
  "outputs": [
    {"columnName": "action"},
    {"columnName": "args"}
  ]
}
```

**Output-1 (action):**

| action |
|--------|
| PRINT |
| SAVE |
| DELETE |

**Output-2 (args):**

| args |
|------|
| Hello World |
| myfile.txt |
| temp_data extra args |

---

## Tips & Warnings

⚠️ **Split Limit**
- Maximum 2 parts per split (hardcoded `limit = 2`)
- Cannot split into 3+ separate parts
- Use multiple Split nodes in sequence for more parts

⚠️ **Missing Column**
- If specified column doesn't exist, throws error
- Error message: "Input column name is not provided"

⚠️ **Empty Values**
- Empty strings still create output (empty parts)
- No special handling for null values

⚠️ **Output Configuration**
- Must provide at least one output configuration
- Output column names defined upfront in `outputs` array
- Cannot be changed during execution

💡 **Dynamic Port Creation**
- Ports are created lazily as data requires them
- First row determines initial port count
- Additional ports added if subsequent rows have more parts

💡 **Space as Delimiter**
- Only space character `" "` is the delimiter
- Tabs, newlines, or other whitespace are NOT delimiters
- Multiple consecutive spaces create empty parts

💡 **Use Cases for Limit = 2**
- Separating command from arguments
- Splitting "key value" pairs
- Extracting prefix from remaining text

---

## Multi-Step Splitting

To split into more than 2 parts, chain multiple Split nodes:

**Example:** Split "A B C D" into 4 parts

**Step 1:** First Split Node
- Input: "A B C D"
- Output-1: "A"
- Output-2: "B C D"

**Step 2:** Second Split Node (on Output-2)
- Input: "B C D"
- Output-1: "B"
- Output-2: "C D"

**Step 3:** Third Split Node (on Output-2)
- Input: "C D"
- Output-1: "C"
- Output-2: "D"

---

## Conditional Workflow Pattern

The dynamic port creation enables conditional workflows:

**Workflow Example:**
```
Input → Split Node
  ├─ Output-1 → Always exists
  └─ Output-2 → Only if 2-part data exists
      └─ Processing Node → Only runs if Output-2 created
```

**Use Case:** Process second part only if it exists
- Single-word inputs: Only process first output
- Two-word inputs: Process both outputs

---

## Example: Handling Missing Parts

**Input:**

| message |
|---------|
| Hello World |
| Goodbye |
| Good Morning Everyone |

**Split Results:**

| Row | Part 1 | Part 2 |
|-----|--------|--------|
| 1 | Hello | World |
| 2 | Goodbye | *(missing)* |
| 3 | Good | Morning Everyone |

**Output Ports:**
- **Output-1**: Created (all rows have part 1)
- **Output-2**: Created (rows 1 and 3 have part 2)

**Row 2 Behavior:**
- Output-1 gets "Goodbye"
- Output-2 has no data for row 2 (index mismatch possible)

---

## Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Input column name is not provided" | `columnName` property missing or column doesn't exist in row | Verify column name and ensure it exists in input data |
| "Data port required" | Input port not connected | Connect input stream to input-1 port |

---

## Comparison with Other Nodes

| Feature | Split Node | Text Normalizer | Kotlin Script |
|---------|------------|-----------------|---------------|
| **Purpose** | Split text into parts | Clean/normalize text | Custom logic |
| **Delimiter** | Space only | N/A | Custom |
| **Max Parts** | 2 | N/A | Unlimited |
| **Dynamic Outputs** | Yes | No | No |
| **Complexity** | Simple | Simple | Complex |

**When to use Split Node:**
- Split on space delimiter
- Maximum 2 parts
- Dynamic output handling

**When to use alternatives:**
- Custom delimiter → Use Kotlin Script
- 3+ parts → Chain multiple Split nodes
- Text cleaning → Use Text Normalizer

---

## Technical Details

- **Delimiter**: Single space character `" "`
- **Split Limit**: 2 (hardcoded)
- **Split Method**: Kotlin `String.split(" ", limit = 2)`
- **Port Creation**: Dynamic, lazy initialization
- **Row Numbering**: Sequential per output (independent counters)
- **Resource Management**: `.use {}` blocks for input reader
- **Output Management**: Writers created as needed, closed at end
- **Error Handling**: Exceptions stop execution immediately
