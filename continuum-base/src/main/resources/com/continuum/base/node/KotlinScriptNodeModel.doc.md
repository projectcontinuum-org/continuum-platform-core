# Kotlin Script

Execute custom Kotlin code on each row to transform, enrich, or compute data dynamically. Perfect for custom business logic, data transformations, or calculations that aren't available in standard nodes.

---

## What It Does

The Kotlin Script node runs a Kotlin script on each row of your input data, allowing you to write custom transformation logic. The script has access to the current row data and can perform any computation, returning a result that's added as a new column.

**Key Points:**
- ✅ Execute custom Kotlin code per row
- ✅ Access row data via `row` variable
- ✅ Script result added as `script_result` column
- ✅ Full Kotlin language support
- ✅ Original columns preserved
- ⚠️ Script errors will stop processing

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table to process with Kotlin script |

### Output Ports
| Port | Description |
|------|-------------|
| **data** | Enriched table with original data plus `script_result` column |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **script** | String | Yes | Kotlin script to evaluate (minimum 1 character) |

---

## How It Works

1. **Read each row** from the input table
2. **Create script context** with `row` variable containing current row data
3. **Execute Kotlin script** with access to row data
4. **Capture result** from script execution
5. **Add result** to row as `script_result` column
6. **Write enriched row** to output
7. **Repeat** for all rows

**Important:** The script must be valid Kotlin code. Syntax errors or runtime exceptions will stop the entire workflow.

---

## Accessing Row Data

Within your script, use the `row` variable to access column values:

```kotlin
// Access a column value
row["columnName"]

// Convert to specific type
row["age"]?.toString()?.toInt()

// Safe access with default
row["message"]?.toString() ?: "default"

// Boolean check
row["isActive"] == true
```

**Row Structure:** The `row` variable is a `Map<String, Any?>` where keys are column names and values are column data.

---

## Example

Let's add a custom greeting message based on user data.

**Input:**

| id | name | age |
|----|------|-----|
| 1 | Alice | 25 |
| 2 | Bob | 35 |
| 3 | Charlie | 30 |

**Configuration:**
- **script**: `"Hello ${row["name"]}, you are ${row["age"]} years old"`

**Output:**

| id | name | age | script_result |
|----|------|-----|---------------|
| 1 | Alice | 25 | Hello Alice, you are 25 years old |
| 2 | Bob | 35 | Hello Bob, you are 35 years old |
| 3 | Charlie | 30 | Hello Charlie, you are 30 years old |

---

## Common Use Cases

- **Custom calculations**: Complex business logic or mathematical formulas
- **String manipulation**: Advanced text processing beyond standard nodes
- **Data validation**: Check data quality and add validation results
- **Conditional logic**: If/else logic based on multiple columns
- **Type conversion**: Convert data types with custom rules
- **Data enrichment**: Add computed columns based on existing data
- **Custom formatting**: Format data according to specific requirements

---

## Script Examples

### Example 1: Calculate Total Price
```kotlin
val quantity = row["quantity"]?.toString()?.toInt() ?: 0
val price = row["price"]?.toString()?.toDouble() ?: 0.0
quantity * price
```

**Input:**

| item | quantity | price |
|------|----------|-------|
| Apple | 5 | 1.50 |
| Banana | 3 | 0.75 |

**Output:**

| item | quantity | price | script_result |
|------|----------|-------|---------------|
| Apple | 5 | 1.50 | 7.5 |
| Banana | 3 | 0.75 | 2.25 |

---

### Example 2: Conditional Status
```kotlin
val score = row["score"]?.toString()?.toInt() ?: 0
when {
    score >= 90 -> "Excellent"
    score >= 70 -> "Good"
    score >= 50 -> "Pass"
    else -> "Fail"
}
```

**Input:**

| student | score |
|---------|-------|
| Alice | 95 |
| Bob | 72 |
| Charlie | 45 |

**Output:**

| student | score | script_result |
|---------|-------|---------------|
| Alice | 95 | Excellent |
| Bob | 72 | Good |
| Charlie | 45 | Fail |

---

### Example 3: String Manipulation
```kotlin
val firstName = row["firstName"]?.toString() ?: ""
val lastName = row["lastName"]?.toString() ?: ""
"${lastName.uppercase()}, ${firstName}"
```

**Input:**

| firstName | lastName |
|-----------|----------|
| John | Doe |
| Jane | Smith |

**Output:**

| firstName | lastName | script_result |
|-----------|----------|---------------|
| John | Doe | DOE, John |
| Jane | Smith | SMITH, Jane |

---

### Example 4: Boolean Logic
```kotlin
val age = row["age"]?.toString()?.toInt() ?: 0
val hasLicense = row["hasLicense"] == true
age >= 18 && hasLicense
```

---

## Tips & Warnings

⚠️ **Script Errors**
- Any syntax error or runtime exception will stop the entire workflow
- Test your script with sample data first
- Use safe navigation (`?.`) and elvis operators (`?:`) to handle null values

⚠️ **Type Conversion**
- Row values are of type `Any?`, so you need to convert them
- Always use `.toString()` before converting to Int, Double, etc.
- Provide default values using elvis operator: `?: 0`

⚠️ **Performance**
- The script is executed once per row
- Complex scripts on large datasets may be slow
- Consider using standard nodes for simple operations

⚠️ **Null Values**
- Missing columns return `null`
- Always handle null cases with safe operators
- Example: `row["col"]?.toString() ?: "default"`

💡 **Kotlin Language Features**
- Full Kotlin syntax supported: `when`, `if`, `let`, `run`, etc.
- String templates: `"Hello ${row["name"]}"`
- Safe calls: `row["value"]?.toString()?.toInt()`
- Elvis operator: `row["value"] ?: defaultValue`

💡 **Debugging**
- Start with simple scripts and test incrementally
- Use string interpolation to debug: `"Debug: ${row}"`
- Check for null values before type conversion

💡 **Return Value**
- The last expression in your script is the result
- Can return any type: String, Number, Boolean, etc.
- Result is added as `script_result` column

---

## Complex Example: Multi-Column Calculation

**Scenario:** Calculate BMI and add health category

**Script:**
```kotlin
val weight = row["weight"]?.toString()?.toDouble() ?: 0.0
val height = row["height"]?.toString()?.toDouble() ?: 0.0
val bmi = if (height > 0) weight / (height * height) else 0.0
val category = when {
    bmi < 18.5 -> "Underweight"
    bmi < 25 -> "Normal"
    bmi < 30 -> "Overweight"
    else -> "Obese"
}
"BMI: ${String.format("%.1f", bmi)} - $category"
```

**Input:**

| name | weight | height |
|------|--------|--------|
| Alice | 65 | 1.70 |
| Bob | 90 | 1.80 |

**Output:**

| name | weight | height | script_result |
|------|--------|--------|---------------|
| Alice | 65 | 1.70 | BMI: 22.5 - Normal |
| Bob | 90 | 1.80 | BMI: 27.8 - Overweight |

---

## Available Kotlin Features

**Data Types:**
- String, Int, Double, Boolean, Long, Float
- Collections: List, Map, Set
- Nullable types with `?`

**Operators:**
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical: `&&`, `||`, `!`
- Safe navigation: `?.`
- Elvis: `?:`

**Control Flow:**
- `if`/`else` expressions
- `when` expressions
- `for`, `while` loops
- `try`/`catch` blocks

**String Operations:**
- Templates: `"Hello $name"`
- Multiline: `"""..."""`
- Methods: `.uppercase()`, `.lowercase()`, `.trim()`, `.split()`

---

## Error Handling

**Script Validation:**
- Script property cannot be empty
- Kotlin script engine must be available

**Runtime Errors:**
```
Script execution error at row 5: Cannot cast to Int
```

**Common Errors:**
- Type conversion failures
- Null pointer exceptions
- Invalid syntax

**Best Practice:**
```kotlin
// Bad: May throw exception
row["age"].toString().toInt()

// Good: Safe with default
row["age"]?.toString()?.toIntOrNull() ?: 0
```

---

## Comparison with Other Nodes

| Feature | Kotlin Script | Standard Nodes |
|---------|---------------|----------------|
| **Flexibility** | Unlimited custom logic | Fixed operations |
| **Performance** | Slower (script evaluation) | Faster (compiled) |
| **Complexity** | Requires coding knowledge | User-friendly |
| **Debugging** | More difficult | Easier |
| **Use Case** | Custom/complex logic | Standard operations |

**When to use Kotlin Script:**
- Complex business logic
- Custom calculations
- No standard node available

**When to use Standard Nodes:**
- Simple operations
- Performance critical
- Team has non-developers

---

## Technical Details

- **Script Engine**: Java ScriptEngineManager with Kotlin engine
- **Execution**: Per-row sequential evaluation
- **Context**: Each row gets fresh bindings with `row` variable
- **Output Column**: Always named `script_result`
- **Error Handling**: First error stops execution
- **Memory**: One row in memory at a time
- **Thread Safety**: Single-threaded execution per workflow
