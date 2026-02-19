# Text Normalizer

Normalizes text by applying standard cleaning operations: trimming whitespace, converting to lowercase, and removing non-alphanumeric characters (except spaces).

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with text column to normalize |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table plus normalized text column |

## Properties
- **inputCol** (string, required): Column containing text to normalize
- **outputCol** (string, required): Column name for the normalized output

## Behavior
For each row, applies the following transformations in sequence:
1. **Trim**: Removes leading and trailing whitespace
2. **Lowercase**: Converts all characters to lowercase
3. **Strip**: Removes all non-alphanumeric characters except spaces (keeps a-z, 0-9, and spaces)

The original column is preserved, and normalized text is added as a new column.

## Use Cases
- Text preprocessing for machine learning
- Search normalization
- Data deduplication
- Consistency enforcement

## Example

**Input:**
```json
[
  {"id": 1, "text": "Hello, World! 123"},
  {"id": 2, "text": "  Foo Bar @baz  "}
]
```

**Properties:**
```json
{
  "inputCol": "text",
  "outputCol": "clean"
}
```

**Output:**
```json
[
  {"id": 1, "text": "Hello, World! 123", "clean": "hello world 123"},
  {"id": 2, "text": "  Foo Bar @baz  ", "clean": "foo bar baz"}
]
```

Note how punctuation and special characters are removed, while numbers and spaces are preserved.
