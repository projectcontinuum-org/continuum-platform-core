# REST Node

Makes HTTP requests for each row using FreeMarker templates to dynamically construct URLs and payloads from row data.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table where each row triggers an HTTP request |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table plus response column with {status, body} |

## Properties
- **method** (string, required): HTTP method - GET, POST, PUT, or DELETE
- **url** (string, required): FreeMarker template for URL (e.g., `https://api.example.com/user/${row.id}`)
- **payload** (string, optional): FreeMarker template for request body

## Behavior
For each row:
1. Renders `url` template with row data using FreeMarker
2. Renders `payload` template (if provided)
3. Builds HTTP request with:
   - Method: GET, POST, PUT, or DELETE
   - URL: Rendered from template
   - Body: Rendered payload (POST/PUT only)
   - Header: Content-Type set to application/json
4. Executes synchronous HTTP request
5. Adds `response` object to row:
   - `status` (integer): HTTP status code
   - `body` (string): Response body

**Error Handling:**
- On failure: Returns status=-1 with error message in body
- Template errors throw NodeRuntimeException

## FreeMarker Template Syntax

Access row fields using `${row.fieldName}`:

**Simple field access:**
```
https://api.example.com/user/${row.userId}
```

**Multiple fields:**
```
https://api.example.com/search?q=${row.query}&page=${row.page}
```

**Conditionals:**
```
<#if row.premium>https://api.premium.com<#else>https://api.free.com</#if>
```

**JSON payload:**
```json
{
  "name": "${row.name}",
  "age": ${row.age},
  "active": <#if row.active>true<#else>false</#if>
}
```

## Example 1: Simple GET Request

**Input:**
```json
[
  {"userId": 1, "query": "hello"},
  {"userId": 2, "query": "world"}
]
```

**Properties:**
```json
{
  "method": "GET",
  "url": "https://api.example.com/search?user=${row.userId}&q=${row.query}",
  "payload": ""
}
```

**Output:**
```json
[
  {
    "userId": 1,
    "query": "hello",
    "response": {
      "status": 200,
      "body": "{\"results\": [...]}"
    }
  },
  {
    "userId": 2,
    "query": "world",
    "response": {
      "status": 200,
      "body": "{\"results\": [...]}"
    }
  }
]
```

## Example 2: POST with Payload

**Properties:**
```json
{
  "method": "POST",
  "url": "https://api.example.com/users",
  "payload": "{\"name\": \"${row.name}\", \"email\": \"${row.email}\"}"
}
```

This creates a new user via POST for each row.
