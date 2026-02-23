# Time Window Aggregator

Group time-series data into fixed time windows and aggregate values. Perfect for creating time-based summaries, analyzing trends over intervals, or preparing data for time-series visualization.

---

## What It Does

The Time Window Aggregator takes time-series data (rows with timestamps and numeric values) and groups them into fixed-size time windows (e.g., every 5 minutes, every 15 minutes). For each window, it sums all the values that fall within that period.

**Key Points:**
- ✅ Groups data by time windows (1-59 minutes)
- ✅ Automatically calculates window boundaries
- ✅ Sums values within each window
- ✅ Outputs one row per window
- ⚠️ Windows are calculated within the hour (not across hour boundaries)
- ⚠️ Requires all data in memory (not suitable for unbounded streams)

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table with timestamp and numeric value columns |

### Output Ports
| Port | Description |
|------|-------------|
| **data** | Aggregated table with window_start and sum_value columns |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **timeCol** | String | Yes | Column containing timestamps (format: "yyyy-MM-dd HH:mm:ss") |
| **valueCol** | String | Yes | Numeric column to aggregate (sum) |
| **windowSize** | Number | Yes | Window size in minutes (must be between 1 and 59) |

---

## How It Works

1. **Parse timestamps** from the specified column
2. **Floor each timestamp** to the nearest window boundary
   - Example with 5-minute windows:
     - 14:03:45 → 14:00:00
     - 14:07:12 → 14:05:00
     - 14:59:59 → 14:55:00
3. **Group rows** that fall into the same window
4. **Sum values** for each group
5. **Output one row** per window with:
   - `window_start`: Start time of the window
   - `sum_value`: Total sum for that window
6. **Sort results** by window start time

---

## Window Calculation Algorithm

The window start is calculated using this formula:

```
minute = timestamp.minute
floored_minute = (minute / windowSize) * windowSize
window_start = timestamp with minute set to floored_minute, seconds and nanoseconds set to 0
```

**Example with 5-minute windows:**
- 10:03:45 → minute=3, floored=(3/5)*5=0 → window starts at 10:00:00
- 10:07:30 → minute=7, floored=(7/5)*5=5 → window starts at 10:05:00
- 10:12:15 → minute=12, floored=(12/5)*5=10 → window starts at 10:10:00

**Important:** Windows are calculated within the hour. A 5-minute window will never span across the hour boundary (e.g., 09:55-10:00).

---

## Example

Let's aggregate transaction amounts into 5-minute windows.

**Input:**

| time | amount |
|------|--------|
| 2026-01-01 10:00:00 | 10 |
| 2026-01-01 10:02:00 | 20 |
| 2026-01-01 10:06:00 | 30 |
| 2026-01-01 10:08:00 | 15 |

**Configuration:**
- **timeCol**: `time`
- **valueCol**: `amount`
- **windowSize**: `5`

**Output:**

| window_start | sum_value |
|--------------|-----------|
| 2026-01-01 10:00:00 | 30 |
| 2026-01-01 10:05:00 | 45 |

**Explanation:**
- **Window 1** (10:00:00 - 10:04:59):
  - 10:00:00 → amount 10
  - 10:02:00 → amount 20
  - **Total:** 30

- **Window 2** (10:05:00 - 10:09:59):
  - 10:06:00 → amount 30
  - 10:08:00 → amount 15
  - **Total:** 45

---

## Common Use Cases

- **Traffic analysis**: Sum requests per 5-minute interval
- **Financial reporting**: Aggregate transactions by time windows
- **IoT sensor data**: Group sensor readings into time buckets
- **Performance monitoring**: Calculate metrics per time window
- **Resource usage**: Sum CPU/memory usage over intervals
- **Sales analytics**: Track revenue over time periods
- **Log aggregation**: Count events per time window

---

## Understanding Window Boundaries

Windows are **left-inclusive, right-exclusive**: `[start, end)`

```
5-minute window examples:
[10:00:00, 10:05:00) - includes 10:04:59.999, excludes 10:05:00
[10:05:00, 10:10:00) - includes 10:09:59.999, excludes 10:10:00
[10:10:00, 10:15:00) - includes 10:14:59.999, excludes 10:15:00
```

**Edge Case:** Time exactly on the boundary (e.g., 10:05:00) belongs to the **new** window, not the previous one.

---

## Tips & Warnings

⚠️ **Window Size Limits**
- Window size must be between **1 and 59 minutes**
- Windows are calculated within the hour only
- Cannot create windows that span across hour boundaries

⚠️ **Timestamp Format**
- Timestamps must be in format: `"yyyy-MM-dd HH:mm:ss"`
- Examples: `"2026-01-01 10:30:00"`, `"2024-12-31 23:59:59"`
- Invalid formats will cause errors

⚠️ **Memory Usage**
- All data is loaded into memory for grouping
- Not suitable for unbounded or very large streaming datasets
- Consider batch processing for large historical data

⚠️ **Missing or Invalid Values**
- Missing timestamps will cause errors
- Non-numeric values in the value column will cause errors
- Ensure data quality before using this node

💡 **Choosing Window Size**
- Smaller windows (1-5 min): More granular but more output rows
- Medium windows (10-15 min): Balanced granularity
- Larger windows (30-60 min): High-level trends, fewer rows

💡 **Output Columns**
- Output contains **only** two columns: `window_start` and `sum_value`
- Original columns are not preserved
- If you need original data, split the workflow

💡 **Empty Windows**
- If no data falls in a time window, that window is **not included** in the output
- Output only contains windows that have at least one data point

---

## Advanced Example: Hour Boundary Behavior

**Input:**

| time | value |
|------|-------|
| 2026-01-01 09:57:00 | 10 |
| 2026-01-01 09:59:00 | 20 |
| 2026-01-01 10:01:00 | 30 |

**Configuration:**
- **windowSize**: `5`

**Output:**

| window_start | sum_value |
|--------------|-----------|
| 2026-01-01 09:55:00 | 30 |
| 2026-01-01 10:00:00 | 30 |

**Explanation:**
- 09:57 and 09:59 both fall in window [09:55:00, 10:00:00) → sum = 30
- 10:01 falls in window [10:00:00, 10:05:00) → sum = 30
- The windows do **not** span across the hour boundary

---

## Technical Details

- **Algorithm**: In-memory grouping with time-based bucketing
- **Time Library**: Uses Java `LocalDateTime` (no timezone support)
- **Timestamp Parsing**: Uses `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`
- **Windowing**: Integer division for within-hour bucketing
- **Sorting**: Results sorted by window_start timestamp
- **Aggregation**: Simple summation of numeric values
