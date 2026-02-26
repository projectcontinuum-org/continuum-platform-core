# Text Normalizer

Clean and standardize text data by removing special characters, converting to lowercase, and trimming whitespace. Essential for text processing, search normalization, and data consistency.

---

## What It Does

The Text Normalizer applies a series of text cleaning transformations to make text consistent and ready for analysis, comparison, or machine learning. It's the go-to node for preparing messy text data.

**Key Points:**
- ✅ Trims leading and trailing whitespace
- ✅ Converts all text to lowercase
- ✅ Removes special characters (keeps only letters, numbers, and spaces)
- ✅ Preserves the original column
- ✅ Adds normalized text as a new column

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table with text column to normalize |

### Output Ports
| Port | Description |
|------|-------------|
| **data** | Original table plus a new column with normalized text |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **inputCol** | String | Yes | Name of the column containing text to normalize |
| **outputCol** | String | Yes | Name for the new column that will contain normalized text |

---

## How It Works

The node applies three transformations **in sequence**:

1. **Trim Whitespace**
   - Removes spaces/tabs from the beginning and end
   - `"  hello  "` → `"hello"`

2. **Convert to Lowercase**
   - Changes all uppercase letters to lowercase
   - `"Hello World"` → `"hello world"`

3. **Remove Special Characters**
   - Keeps only: letters (a-z), numbers (0-9), and spaces
   - Removes: punctuation, symbols, emojis, etc.
   - `"hello@world!"` → `"helloworld"`
   - `"test-123"` → `"test123"`

**Important:** The original column is preserved unchanged. The normalized result is added as a **new column**.

---

## Example

Let's normalize messy user input for consistent search.

**Input:**

| id | comment |
|----|---------|
| 1 | Hello, World! 123 |
| 2 |   Foo Bar @baz   |
| 3 | Test-Case_Example! |

**Configuration:**
- **inputCol**: `comment`
- **outputCol**: `normalized`

**Output:**

| id | comment | normalized |
|----|---------|------------|
| 1 | Hello, World! 123 | hello world 123 |
| 2 |   Foo Bar @baz   | foo bar baz |
| 3 | Test-Case_Example! | testcaseexample |

**Transformation Breakdown:**

| Original | After Trim | After Lowercase | After Strip |
|----------|------------|-----------------|-------------|
| `"Hello, World! 123"` | `"Hello, World! 123"` | `"hello, world! 123"` | `"hello world 123"` |
| `"  Foo Bar @baz  "` | `"Foo Bar @baz"` | `"foo bar @baz"` | `"foo bar baz"` |
| `"Test-Case_Example!"` | `"Test-Case_Example!"` | `"test-case_example!"` | `"testcaseexample"` |

---

## Common Use Cases

- **Search normalization**: Make search queries case-insensitive and punctuation-agnostic
- **Data deduplication**: Identify duplicate records with minor formatting differences
- **Text preprocessing**: Prepare text for machine learning models
- **Data quality**: Standardize text input from various sources
- **Comparison**: Enable accurate text matching regardless of formatting
- **Data cleaning**: Remove unwanted characters from user input
- **Analytics**: Group similar text values together

---

## Character Handling

**Kept Characters:**
- Letters: `a-z A-Z` (converted to lowercase)
- Numbers: `0-9`
- Spaces: ` ` (single space)

**Removed Characters:**
- Punctuation: `. , ! ? ; : ' "`
- Symbols: `@ # $ % ^ & * ( ) - _ = + [ ] { } | \ / < >`
- Special characters: `~ `` ¡ ¢ £ ¤ ¥ ¦ §` etc.
- Emojis and Unicode symbols: `😀 ★ ♥ ©` etc.

**Examples:**
- `"hello@example.com"` → `"helloexamplecom"`
- `"price: $99.99"` → `"price 9999"`
- `"(555) 123-4567"` → `"555 1234567"`

---

## Tips & Warnings

⚠️ **Information Loss**
- Special characters are permanently removed
- `"user@domain.com"` becomes `"userdomaincom"` (email structure lost)
- `"$100.50"` becomes `"10050"` (currency and decimal lost)
- Consider if you need special characters before normalizing

⚠️ **Numbers and Math**
- Mathematical symbols are removed: `"2+2=4"` → `"224"`
- Negative signs removed: `"-5"` → `"5"`
- Decimals removed: `"3.14"` → `"314"`

⚠️ **Original Data Preserved**
- The input column remains unchanged
- You can still access original data if needed
- Normalized data is in a separate column

💡 **Use for Comparison, Not Display**
- Normalized text is great for matching and searching
- Original text is better for display to users
- Use normalized version for "find", original for "show"

💡 **Multiple Spaces**
- Multiple consecutive spaces are preserved
- `"hello    world"` → `"hello    world"` (spaces kept)
- Consider additional trimming if needed

💡 **Empty Results**
- Text with only special characters becomes empty
- `"@#$%"` → `""` (empty string)
- Check for empty strings if this is a concern

---

## Advanced Example: Search Use Case

**Scenario:** Enable flexible product search

**Input (Product Database):**

| id | name |
|----|------|
| 1 | iPhone 15 Pro |
| 2 | I-Phone 15pro |
| 3 | IPHONE-15 (PRO) |

**Normalization:**

| id | name | searchable |
|----|------|------------|
| 1 | iPhone 15 Pro | iphone 15 pro |
| 2 | I-Phone 15pro | iphone 15pro |
| 3 | IPHONE-15 (PRO) | iphone15 pro |

**Search Query:** User searches for `"iphone 15 pro"`
- Normalize query → `"iphone 15 pro"`
- Match against `searchable` column
- All 3 products found (despite different formatting)

---

## Comparison with Other Approaches

| Approach | Result of "Hello, World!" |
|----------|---------------------------|
| **Text Normalizer** | `"hello world"` |
| Lowercase only | `"hello, world!"` |
| Trim only | `"Hello, World!"` |
| Remove punctuation only | `"Hello World"` |

The Text Normalizer applies all three transformations for maximum standardization.

---

## Technical Details

- **Trim Algorithm**: Uses Kotlin `trim()` function
- **Lowercase**: Uses Kotlin `lowercase()` function (locale-aware)
- **Character Removal**: Regex pattern `[^a-zA-Z0-9 ]` replaced with empty string
- **Regex Explanation**: `^` (not), `a-zA-Z0-9 ` (allowed characters)
- **Performance**: Processes one row at a time (memory efficient)
- **Original Preservation**: Original column value is never modified
