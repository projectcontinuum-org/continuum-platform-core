# Time Window Aggregator

Groups time-series data into fixed-size time windows and aggregates values by summing within each window.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with timestamp and numeric value columns |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Aggregated table with window_start and sum_value columns |

## Properties
- **timeCol** (string, required): Column containing timestamps (format: "yyyy-MM-dd HH:mm:ss")
- **valueCol** (string, required): Numeric column to aggregate (sum)
- **windowSize** (number, required): Window size in minutes (minimum: 1)

## Behavior
1. Parses timestamps from `timeCol`
2. Floors each timestamp to the nearest `windowSize`-minute boundary
3. Groups all rows that fall into the same window
4. Sums the `valueCol` values within each window
5. Outputs one row per window with:
   - `window_start`: The start time of the window
   - `sum_value`: The aggregated sum for that window

Results are sorted by window start time.

## Example

**Input:**
```json
[
  {"time": "2026-01-01 10:00:00", "value": 10},
  {"time": "2026-01-01 10:02:00", "value": 20},
  {"time": "2026-01-01 10:06:00", "value": 30}
]
```

**Properties:**
```json
{
  "timeCol": "time",
  "valueCol": "value",
  "windowSize": 5
}
```

**Output:**
```json
[
  {"window_start": "2026-01-01 10:00:00", "sum_value": 30},
  {"window_start": "2026-01-01 10:05:00", "sum_value": 30}
]
```

First two records (10:00, 10:02) fall in window [10:00-10:05) → sum = 30
Third record (10:06) falls in window [10:05-10:10) → sum = 30
