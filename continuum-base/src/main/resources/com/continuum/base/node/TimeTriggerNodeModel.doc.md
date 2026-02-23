# Time Trigger (Start Node)

Start your workflow with timestamped rows. Perfect for testing, scheduled jobs, or generating time-series data for your workflows.

---

## What It Does

The Time Trigger is a workflow starting point that generates rows containing current timestamps. It's the "play button" for your workflow, creating initial data to flow through your pipeline.

**Key Points:**
- ✅ No input required (this is a trigger node)
- ✅ Generates configurable number of rows
- ✅ Each row contains current timestamp in ISO-8601 format
- ✅ Sequential row numbering starting from 0
- ✅ Customizable message prefix
- ⚠️ Timestamps captured at write time (slight variations between rows)

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| *(none)* | This is a trigger node - it doesn't accept inputs |

### Output Ports
| Port | Description |
|------|-------------|
| **output-1** | Stream of rows containing timestamped messages |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **message** | String | No | "Logging at" | Prefix text for each message |
| **rowCount** | Number | No | 10 | Number of rows to generate (must be > 0) |

---

## How It Works

1. **Validate rowCount** (defaults to 10 if invalid or missing)
2. **Get message prefix** (defaults to "Logging at")
3. **Generate rows** in a loop:
   - For each row (0 to rowCount-1):
     - Capture current timestamp
     - Format: `{message prefix} {ISO-8601 timestamp}`
     - Write row with sequential index

**Output Format:** Each row contains a single column named `message` with format:
```
{message} {timestamp}
```

**Example message:**
```
Logging at 2026-02-21T10:30:15.123Z
```

---

## Example

Let's generate 3 timestamped events.

**Configuration:**
- **message**: `"Event triggered at"`
- **rowCount**: `3`

**Output:**

| message |
|---------|
| Event triggered at 2026-02-21T10:30:15.123Z |
| Event triggered at 2026-02-21T10:30:15.124Z |
| Event triggered at 2026-02-21T10:30:15.125Z |

**Notice:** Timestamps vary slightly (milliseconds) as each row is written sequentially.

---

## Common Use Cases

- **Workflow testing**: Generate test data to validate your workflow
- **Scheduled jobs**: Trigger workflows at specific times
- **Time-series generation**: Create timestamp-based initial data
- **Load testing**: Generate configurable number of rows to test performance
- **Debugging**: Start workflows with known input for troubleshooting
- **Batch processing**: Initiate batch jobs with timestamp markers
- **Event simulation**: Simulate time-based events

---

## Row Count Validation

The `rowCount` property has robust validation:

**Valid Values:**
- Any positive integer: `1`, `10`, `100`, `1000`, etc.
- Accepted as Number: `10`, `42.0`
- Accepted as String: `"10"`, `"100"`

**Invalid Values (default to 10):**
- Zero or negative: `0`, `-5` → defaults to `10`
- Non-numeric strings: `"abc"`, `"ten"` → defaults to `10`
- Missing property: → defaults to `10`
- Null or empty: → defaults to `10`

**Examples:**
- `rowCount: 5` → generates 5 rows ✅
- `rowCount: "20"` → generates 20 rows ✅
- `rowCount: 0` → generates 10 rows (default) ⚠️
- `rowCount: -3` → generates 10 rows (default) ⚠️
- `rowCount: "bad"` → generates 10 rows (default) ⚠️

---

## Timestamp Format

**Format:** ISO-8601 with UTC timezone
- Pattern: `yyyy-MM-ddTHH:mm:ss.SSSZ`
- Example: `2026-02-21T10:30:15.123Z`

**Components:**
- `2026-02-21` - Date (year-month-day)
- `T` - Separator
- `10:30:15.123` - Time with milliseconds
- `Z` - UTC timezone indicator

**Parsing:** Compatible with most date parsing libraries:
- JavaScript: `new Date("2026-02-21T10:30:15.123Z")`
- Python: `datetime.fromisoformat()`
- Java: `Instant.parse()`

---

## Tips & Warnings

⚠️ **Timestamp Variation**
- Timestamps are captured when each row is written
- Expect millisecond-level differences between rows
- For identical timestamps, use a different approach

⚠️ **Not Real-Time**
- All rows are generated immediately when the node executes
- Not suitable for real-time streaming (use a different trigger for that)

⚠️ **Row Count Limits**
- Very large row counts (millions) may impact performance
- Consider splitting into smaller batches for huge datasets
- Each row is written sequentially

💡 **Default Behavior**
- If you don't configure anything, you get 10 rows with "Logging at" prefix
- Great for quick testing without configuration

💡 **Custom Messages**
- Use message property to add context
- Examples:
  - `"Workflow started at"`
  - `"Processing batch at"`
  - `"Health check at"`

💡 **As Workflow Entry Point**
- This is a **trigger node** - it starts workflows
- Connect it to processing nodes to begin data flow
- Perfect for scheduled or manual workflow execution

---

## Example Workflows

### Workflow 1: Simple Test Pipeline
```
Time Trigger (5 rows)
  → Text Normalizer
  → Output Node
```
Generate 5 test rows to validate text processing.

### Workflow 2: Load Testing
```
Time Trigger (1000 rows)
  → Conditional Splitter
  → Aggregator
  → Output Node
```
Generate 1000 rows to test workflow performance.

### Workflow 3: Scheduled Job
```
Time Trigger (1 row, message="Batch started at")
  → Database Query
  → Processing
  → Report Generator
```
Start daily batch job with timestamp marker.

---

## Advanced Example: Varying Row Counts

**Scenario:** Generate different amounts of test data

**Small Test:**
```json
{"message": "Test", "rowCount": 3}
```
Output: 3 rows (quick test)

**Medium Test:**
```json
{"message": "Load test", "rowCount": 100}
```
Output: 100 rows (performance test)

**Large Test:**
```json
{"message": "Stress test", "rowCount": 10000}
```
Output: 10,000 rows (stress test)

---

## Comparison with Other Triggers

| Trigger Type | Use Case |
|--------------|----------|
| **Time Trigger** | Generate timestamped test data |
| File Trigger | Start on file upload/change |
| HTTP Trigger | Start on API request |
| Schedule Trigger | Start at specific times |
| Manual Trigger | Start on user action |

---

## Technical Details

- **Implementation**: Extends `TriggerNodeModel` (no inputs)
- **Timestamp Source**: `java.time.Instant.now()`
- **Timestamp Format**: ISO-8601 via `Instant.toString()`
- **Validation**: Multi-type rowCount support (Number, String)
- **Default Handling**: Graceful fallback to 10 rows for invalid values
- **Performance**: Sequential write (one row at a time)
- **Memory**: Minimal (no data buffering)
