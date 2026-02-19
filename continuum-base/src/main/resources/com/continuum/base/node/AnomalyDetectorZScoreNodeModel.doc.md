# Anomaly Detector (Z-Score)

Detects statistical outliers in numeric data using the Z-score method, flagging values that deviate significantly from the mean.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with numeric column to analyze |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table plus is_outlier boolean column |

## Properties
- **valueCol** (string, required): The numeric column to analyze for outliers

## Behavior
Uses a two-pass algorithm:

**First Pass:**
1. Collects all values from `valueCol`
2. Calculates mean (μ) and standard deviation (σ)

**Second Pass:**
1. For each row, calculates Z-score: `z = (value - μ) / σ`
2. Flags as outlier if `|z| > 2.0`
3. Adds `is_outlier` boolean column to each row

**Edge Cases:**
- If σ = 0 (all values identical), all rows flagged as `is_outlier: false`
- Non-numeric values treated as 0

## Statistical Method

Z-score measures how many standard deviations a value is from the mean:
- |z| ≤ 2: Normal value (within 95% of data)
- |z| > 2: Outlier (beyond 95% of data)

## Example

**Input:**
```json
[
  {"id": 1, "value": 10},
  {"id": 2, "value": 20},
  {"id": 3, "value": 100}
]
```

**Properties:**
```json
{
  "valueCol": "value"
}
```

**Statistics:**
- Mean: 43.33
- Std Dev: 40.28
- Z-scores: -0.83, -0.58, 1.41

**Output:**
```json
[
  {"id": 1, "value": 10, "is_outlier": false},
  {"id": 2, "value": 20, "is_outlier": false},
  {"id": 3, "value": 100, "is_outlier": false}
]
```

In this example, even 100 is not flagged because |z| = 1.41 < 2.0.
