# Conditional Splitter

Split your data into two separate streams based on a numeric threshold. Route high-value and low-value records to different parts of your workflow for specialized processing.

---

## What It Does

The Conditional Splitter examines each row and routes it to one of two output ports based on whether a numeric value meets a threshold. It's like a traffic controller that directs data down different paths based on a simple rule.

**Key Points:**
- ✅ Routes rows to **high** port if value >= threshold
- ✅ Routes rows to **low** port if value < threshold
- ✅ Each output maintains independent row numbering (starts from 0)
- ✅ All row data is preserved in both outputs
- ⚠️ Missing or non-numeric columns default to 0 with a warning

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table with rows to split |

### Output Ports
| Port | Description |
|------|-------------|
| **high** | Rows where column value **>=** threshold (greater than or equal) |
| **low** | Rows where column value **<** threshold (less than) |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **column** | String | Yes | Name of the numeric column to compare |
| **threshold** | Number | Yes | The split point value |

---

## How It Works

1. **Read each row** from the input
2. **Extract the value** from the specified column
3. **Convert to number** (if missing or non-numeric, defaults to 0)
4. **Compare and route**:
   - If `value >= threshold` → send to **high** output
   - If `value < threshold` → send to **low** output
5. **Maintain separate counters** for each output stream

**Important:** Each output port has its own sequential row numbering starting from 0, independent of the other port.

---

## Example

Let's split transactions into high-value (>= $50) and low-value (< $50) categories.

**Input:**

| id | amount | item |
|----|--------|------|
| 1 | 100 | Laptop |
| 2 | 25 | Book |
| 3 | 50 | Headphones |
| 4 | 10 | Cable |

**Configuration:**
- **column**: `amount`
- **threshold**: `50`

**High Output (amount >= 50):**

| id | amount | item |
|----|--------|------|
| 1 | 100 | Laptop |
| 3 | 50 | Headphones |

**Low Output (amount < 50):**

| id | amount | item |
|----|--------|------|
| 2 | 25 | Book |
| 4 | 10 | Cable |

**Why?**
- Laptop (100): 100 >= 50 = ✅ High
- Book (25): 25 < 50 = ✅ Low
- Headphones (50): 50 >= 50 = ✅ High (equal counts as high!)
- Cable (10): 10 < 50 = ✅ Low

---

## Common Use Cases

- **Value-based routing**: Separate high-value vs low-value transactions
- **Performance tiers**: Split test scores into pass/fail categories
- **Priority queues**: Route urgent vs normal priority items
- **A/B testing**: Separate data based on experiment scores
- **Quality control**: Split products into acceptable vs defective
- **Risk assessment**: Divide into high-risk and low-risk categories
- **Age filtering**: Split data into different age brackets

---

## Understanding the Threshold

The comparison uses **>=** for the high output and **<** for the low output:

```
Value Range          Output Port
-------------        -----------
< threshold          LOW
= threshold          HIGH
> threshold          HIGH
```

**Example with threshold = 15:**
- 14.9 → LOW
- 15.0 → HIGH
- 15.1 → HIGH

---

## Tips & Warnings

⚠️ **Missing Columns**
- If a row doesn't have the specified column, it defaults to 0
- A warning is logged for each missing column
- Value of 0 will be routed to LOW if threshold > 0, or HIGH if threshold <= 0

⚠️ **Non-Numeric Values**
- Text, booleans, or null values default to 0
- Make sure your column contains numeric data

⚠️ **Independent Row Numbering**
- Each output port maintains its **own** row count
- High output row numbers: 0, 1, 2, ...
- Low output row numbers: 0, 1, 2, ... (independent of high)
- This is **not** the original row number from the input

💡 **All Data Preserved**
- Both outputs contain **all columns** from the input
- Only the routing decision is made, no data is lost or modified

💡 **Workflow Branching**
- This node enables conditional workflow execution
- Connect high and low outputs to different downstream nodes
- Process each category with specialized logic

---

## Advanced Example: Multi-Tier Processing

**Scenario**: Process orders differently based on value

**Input:**

| order | value |
|-------|-------|
| A | 1000 |
| B | 50 |
| C | 500 |

**Configuration:**
- **column**: `value`
- **threshold**: `200`

**Workflow:**
- **High output** (>= 200) → Route to "Premium Processing" node
  - Order A (1000)
  - Order C (500)

- **Low output** (< 200) → Route to "Standard Processing" node
  - Order B (50)

---

## Technical Details

- **Algorithm**: Sequential row-by-row evaluation with dual-stream output
- **Type Conversion**: Safe Number type casting with 0.0 default
- **Logging**: Warns when columns are missing or non-numeric
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Resource Management**: Both output writers are opened upfront using `.use {}` blocks
