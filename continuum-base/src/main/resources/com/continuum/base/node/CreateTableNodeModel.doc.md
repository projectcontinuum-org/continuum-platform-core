# Create Table Node

Generates a structured table from a FreeMarker template. Supports dynamic row generation through template variables and loops.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| (none) | - | - | This is a trigger node - sources data from properties only |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Table with rows created from generated JSON array objects |

## Properties
- **jsonArrayString** (string format: code, language: freemarker, required): FreeMarker template that generates a JSON array

## Behavior
1. Evaluates the `jsonArrayString` property as a FreeMarker template
2. Template can use FreeMarker directives to generate JSON dynamically
3. Parses the generated JSON as an array
4. Converts each JSON object into a table row
5. Ensures all rows have the same columns (fills empty string for missing keys)
6. Outputs a structured table for downstream processing

**Error Handling:**
- Template rendering error: Throws non-retriable NodeRuntimeException
- Invalid JSON output: Throws non-retriable NodeRuntimeException
- Empty template result: Returns empty table

**Column Consistency:**
The node collects all unique keys across all objects and ensures every row has all columns.
Missing keys in any object are filled with empty string ("") values.

## FreeMarker Template Syntax

Access template variables and use FreeMarker directives:

**Loop to generate multiple rows:**
```freemarker
[<#list 1..5 as i>
  {"id": ${i}, "value": "item${i}"}<#if i?has_next>,</#if>
</#list>]
```

**Conditional logic:**
```freemarker
[<#list items as item>
  <#if item.active>
    {"id": ${item.id}, "name": "${item.name}"}
    <#if item?has_next>,</#if>
  </#if>
</#list>]
```

## Use Cases
- Generate test data dynamically using loops
- Create rows based on template logic
- Transform JSON configuration into processable rows
- Import raw JSON data into workflow
- Convert API responses to table format

## Example 1: Simple Static JSON

**Properties:**
```freemarker
[{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]
```

**Output:**
```json
[
  {"id": 1, "name": "Alice"},
  {"id": 2, "name": "Bob"}
]
```

## Example 2: Generate Rows with Loop

**Properties:**
```freemarker
[<#list 1..3 as i>
  {"id": ${i}, "name": "User${i}"}<#if i?has_next>,</#if>
</#list>]
```

**Output:**
```json
[
  {"id": 1, "name": "User1"},
  {"id": 2, "name": "User2"},
  {"id": 3, "name": "User3"}
]
```

## Example 3: Conditional Row Generation

**Properties:**
```freemarker
[<#assign items = [
  {"id": 1, "status": "active"},
  {"id": 2, "status": "inactive"},
  {"id": 3, "status": "active"}
]>
<#list items as item>
  <#if item.status == "active">
    {"id": ${item.id}, "name": "Item${item.id}"}<#if item?has_next>,</#if>
  </#if>
</#list>]
```

**Output:**
```json
[
  {"id": 1, "name": "Item1"},
  {"id": 3, "name": "Item3"}
]
```
