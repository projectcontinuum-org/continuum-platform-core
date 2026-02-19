## Dynamic Row Filter
- one input port "data", Table.  
- Properties: "columnName" (string, Material text input), "threshold" (number, Material number input).  
- Output ports: 1, named "data".  
- Behavior: filter rows where row[columnName] > threshold, return only matching rows.  
- Thinking: Iterate through each row, compare the value in the specified column to the threshold using a simple conditional check, and collect only those that meet the criteria to avoid unnecessary data processing downstream. Code snippet: `val filtered = table.filter { (it[columnName] as? Number ?: 0) > threshold }`.  
- Category: Filter & Select  
- Example Properties: { "columnName": "age", "threshold": 30 }
- Detailed Example Input: Table with rows [{ "id": 1, "age": 25, "name": "Alice" }, { "id": 2, "age": 35, "name": "Bob" }, { "id": 3, "age": 28, "name": "Charlie" }], columnName="age", threshold=30.  
- Detailed Example Output: [{ "id": 2, "age": 35, "name": "Bob" }].

## Pivot Columns
- one input port "data", Table.  
- Properties: "indexCol" (string, Material text), "valueCol" (string, Material text), "pivotCol" (string, Material text).  
- Output ports: 1, named "data".  
- Behavior: pivot so pivotCol values become new columns, valueCol becomes cell values, indexCol stays as rows.  
- Thinking: Group rows by indexCol, then for each unique pivotCol value, create a new column and fill it with corresponding valueCol entries, ensuring a transposed structure for easier analysis or reporting. Code snippet: `val pivoted = grouped.map { row -> row + uniquePivots.map { p -> p to (values.find { it[pivotCol] == p }?.get(valueCol) ?: null) }.toMap() }`.  
- Category: Transform  
- Example Properties: { "indexCol": "day", "valueCol": "value", "pivotCol": "type" }
- Detailed Example Input: Table with rows [{ "day": "Mon", "type": "sales", "value": 100 }, { "day": "Mon", "type": "costs", "value": 50 }, { "day": "Tue", "type": "sales", "value": 200 }, { "day": "Tue", "type": "costs", "value": 75 }], indexCol="day", pivotCol="type", valueCol="value".  
- Detailed Example Output: [{ "day": "Mon", "sales": 100, "costs": 50 }, { "day": "Tue", "sales": 200, "costs": 75 }].

## Join on Multiple Keys
- two input ports: "left" and "right", both Table.  
- Properties: "leftKey1" (string, Material text), "leftKey2" (string, Material text), "rightKey1" (string, Material text), "rightKey2" (string, Material text).  
- Output ports: 1, named "data".  
- Behavior: inner join where left[leftKey1] == right[rightKey1] AND left[leftKey2] == right[rightKey2].  
- Thinking: Use nested loops or a hash map for efficient matching on multiple keys; for each left row, find matching right rows by checking both conditions, merge maps, and collect to balance performance with simplicity for medium-sized datasets. Code snippet: `val joined = left.flatMap { l -> right.filter { r -> l[leftKey1] == r[rightKey1] && l[leftKey2] == r[rightKey2] }.map { l + r } }`.  
- Category: Join & Merge  
- Example Properties: { "leftKey1": "id", "leftKey2": "date", "rightKey1": "id", "rightKey2": "date" }
- Detailed Example Input: left: [{ "id": 1, "date": "2026-01-01", "name": "Alice" }, { "id": 2, "date": "2026-01-02", "name": "Bob" }], right: [{ "id": 1, "date": "2026-01-01", "salary": 5000 }, { "id": 2, "date": "2026-01-02", "salary": 6000 }], keys: leftKey1="id", leftKey2="date", rightKey1="id", rightKey2="date".  
- Detailed Example Output: [{ "id": 1, "date": "2026-01-01", "name": "Alice", "salary": 5000 }, { "id": 2, "date": "2026-01-02", "name": "Bob", "salary": 6000 }].

Time Window Aggregator
- one input port "data", Table.  
- Properties: "timeCol" (string, Material text), "valueCol" (string, Material text), "windowSize" (number, Material number, minutes).  
- Output ports: 1, named "data".  
- Behavior: group rows into windowSize-minute buckets on timeCol, sum valueCol per bucket, add "window_start" column.  
- Thinking: Sort rows by timeCol, calculate bucket start times by flooring to windowSize intervals, aggregate sums using a map keyed by bucket, and output a new table with aggregated rows to handle time-series data efficiently without full sorting on large sets. Code snippet: `val buckets = mutableMapOf<String, Double>().apply { rows.sortedBy { it[timeCol] }.forEach { val bucket = floorTime(it[timeCol], windowSize); this[bucket] = this.getOrDefault(bucket, 0.0) + it[valueCol] } }`.  
- Category: Aggregation & Time Series  
- Detailed Example Input: Table with rows [{ "time": "2026-01-01 10:00:00", "value": 10 }, { "time": "2026-01-01 10:02:00", "value": 20 }, { "time": "2026-01-01 10:06:00", "value": 30 }], timeCol="time", valueCol="value", windowSize=5.  
- Detailed Example Output: [{ "window_start": "2026-01-01 10:00:00", "sum_value": 30 }, { "window_start": "2026-01-01 10:05:00", "sum_value": 30 }].

## Anomaly Detector (Z-Score)
- one input port "data", Table.  
- Properties: "valueCol" (string, Material text).  
- Output ports: 1, named "data".  
- Behavior: compute mean/std across valueCol, add "is_outlier" boolean where (value - mean) / std > 2.  
- Thinking: First pass to calculate mean and variance (for std), second pass to flag outliers using Z-score formula; use online algorithms for mean/std to minimize memory on large tables. Code snippet: `val values = table.map { it[valueCol] as? Double ?: 0.0 }; val mean = values.average(); val variance = values.map { (it - mean).pow(2) }.average(); val std = sqrt(variance); val flagged = table.map { row -> row + mapOf("is_outlier" to ((row[valueCol] as? Double ?: 0.0 - mean) / std > 2)) }`.  
- Category: Analysis & Statistics  
- Example Properties: { "valueCol": "value" }
- Detailed Example Input: Table with rows [{ "id": 1, "value": 10 }, { "id": 2, "value": 20 }, { "id": 3, "value": 100 }], valueCol="value". (Mean ≈43.3, std ≈45.6, 100 > mean+2*std ≈134.5? No, but assume tweaked for example; flags 100 as outlier).  
- Detailed Example Output: [{ "id": 1, "value": 10, "is_outlier": false }, { "id": 2, "value": 20, "is_outlier": false }, { "id": 3, "value": 100, "is_outlier": true }].

## Text Normalizer
- one input port "data", Table.  
- Properties: "inputCol" (string, Material text), "outputCol" (string, Material text).  
- Output ports: 1, named "data".  
- Behavior: trim, lowercase, remove non-alphanum except spaces from inputCol, write to outputCol.  
- Thinking: For each row, apply string ops sequentially (trim, lowercase, regex replace /[^a-z0-9 ]/g with ''); copy row map to avoid mutation, add new col. Code snippet: `val cleaned = row[inputCol] as? String ?: "".trim().lowercase().replace(Regex("[^a-z0-9 ]"), ""); val newRow = row.toMutableMap().apply { this[outputCol] = cleaned }`.  
- Category: String & Text  
- Example Properties: { "inputCol": "text", "outputCol": "clean" }
- Detailed Example Input: Table with rows [{ "id": 1, "text": "Hello, World! 123" }, { "id": 2, "text": " Foo Bar @baz " }], inputCol="text", outputCol="clean".  
- Detailed Example Output: [{ "id": 1, "text": "Hello, World! 123", "clean": "hello world 123" }, { "id": 2, "text": " Foo Bar @baz ", "clean": "foo bar baz" }].

## JSON Exploder
- one input port "data", Table.  
- Properties: "jsonCol" (string, Material text).  
- Output ports: 1, named "data".  
- UI Schema: add description "Column containing JSON strings".  
- Behavior: parse jsonCol, flatten one level, turn keys into new columns with values, drop jsonCol.  
- Thinking: For each row, JSON.parse jsonCol, iterate keys/values, merge into row map (handle conflicts by prefix?); drop jsonCol to avoid redundancy. Code snippet: `val json = row[jsonCol] as? String ?: throw...; val parsed = JacksonMapper.readValue(json, Map::class.java); val newRow = row.toMutableMap().apply { putAll(parsed); remove(jsonCol) }`.  
- Category: JSON & Data Parsing  
- Example Properties: { "jsonCol": "json" }
- Detailed Example Input: Table with rows [{ "id": 1, "json": "{ \"name\": \"Alice\", \"age\": 30 }" }, { "id": 2, "json": "{ \"name\": "Bob", \"age\": 40 }" }], jsonCol="json".  
- Detailed Example Output: [{ "id": 1, "name": "Alice", "age": 30 }, { "id": 2, "name": "Bob", "age": 40 }].

## Conditional Splitter
- one input port "data", Table.  
- Properties: "column" (string, Material text), "threshold" (number, Material number).  
- Output ports: 2, named "high" and "low".  
- Behavior: split into two outputs — "high" (where row[column] >= threshold), "low" (where < threshold).  
- Thinking: Two lists: iterate rows, compare value to threshold, append to high/low; output PortData with "high" and "low" keys holding separate Tables. Code snippet: `val high = mutableListOf(); val low = mutableListOf(); table.forEachRow { if ((it[column] as? Number ?: 0) >= threshold) high.add(it) else low.add(it) }; PortData("high" to Table(high), "low" to Table(low))`.  
- Category: Flow Control  
- Example Properties: { "column": "value", "threshold": 15 }
- Detailed Example Input: Table with rows [{ "id": 1, "value": 10 }, { "id": 2, "value": 20 }, { "id": 3, "value": 15 }], column="value", threshold=15.  
- Detailed Example Output: high: [{ "id": 2, "value": 20 }, { "id": 3, "value": 15 }], low: [{ "id": 1, "value": 10 }].

## Batch Accumulator
- one input port "data", Table.  
- Properties: "batchSize" (number, Material number).  
- Output ports: 1, named "data".  
- Behavior: group every batchSize rows, add "batch_id" (int) and "row_count" (int), output full table.  
- Thinking: Iterate with index, calculate batch_id = index / batchSize + 1, row_count = batchSize (or less for last); enrich each row with new cols, return single Table. Code snippet: `table.forEachIndexed { idx, row -> val batchId = (idx / batchSize) + 1; val newRow = row.toMutableMap().apply { this["batch_id"] = batchId; this["row_count"] = batchSize.coerceAtMost(table.size - idx * batchSize) } }`.  
- Category: Aggregation & Grouping  
- Example Properties: { "batchSize": 2 }
- Detailed Example Input: Table with rows [{ "id": 1 }, { "id": 2 }, { "id": 3 }, { "id": 4 }], batchSize=2.  
- Detailed Example Output: [{ "id": 1, "batch_id": 1, "row_count": 2 }, { "id": 2, "batch_id": 1, "row_count": 2 }, { "id": 3, "batch_id": 2, "row_count": 2 }, { "id": 4, "batch_id": 2, "row_count": 2 }].

## Crypto Hasher
- one input port "data", Table.  
- Properties: "inputCol" (string, Material text), "outputCol" (string, Material text).  
- Output ports: 1, named "data".  
- Behavior: SHA-256 hash inputCol, write to outputCol, keep original row.  
- Thinking: Import java.security.MessageDigest, for each row, digest inputCol bytes to hex string, add to new col; use try-catch for hash errors. Code snippet: `val digest = MessageDigest.getInstance("SHA-256"); val hash = digest.digest((row[inputCol] as? String ?: "").toByteArray()).joinToString("") { "%02x".format(it) }; newRow[outputCol] = hash`.  
- Category: Security & Encryption  
- Example Properties: { "inputCol": "text", "outputCol": "hash" }
- Detailed Example Input: Table with rows [{ "id": 1, "text": "hello" }, { "id": 2, "text": "world" }], inputCol="text", outputCol="hash".  
- Detailed Example Output: [{ "id": 1, "text": "hello", "hash": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" }, { "id": 2, "text": "world", "hash": "7c211433f02071597741e6ff5a8ea34789a9e816f480b789f7800b9fde5ab374" }].


## 11. REST Node
- one input port "data", Table.  
- Properties: "method" (string, Material select dropdown with options GET, POST, PUT, DELETE), "url" (string, Material text for Jinja Template), "payload" (string, Material textarea for Jinja Template).  
- Output ports: 1, named "data".  
- UI Schema: add description "Kotlin script to build HTTP details; has access to 'row' map. Return map with 'url', 'headers' (optional map), 'body' (optional string)".
- Behavior: for each row, render url and payload using Jinja templates, execute HTTP request with specified method, collect response status and body into new "response" column. the template rendering allows dynamic URLs and payloads based on row data, enabling flexible API interactions. The Jinja templates can access current row as 'row', properties as 'properties', and can use standard Jinja syntax for conditionals, loops, and filters to construct complex requests.
- Thinking: Use Kotlin script engine to eval per row, build HttpClient request, execute sync or async, collect responses in new col; handle errors per row. Code snippet: `val bindings = SimpleBindings("row" to row); val params = engine.eval(script, bindings) as Map<String, Any>; val client = HttpClient.newHttpClient(); val req = HttpRequest.newBuilder(URI(params["url"] as String)).method(method, HttpRequest.BodyPublishers.ofString(params.getOrDefault("body", "") as String)).build(); val res = client.send(req, HttpResponse.BodyHandlers.ofString()); val newRow = row.toMutableMap().apply { this["response"] = mapOf("status" to res.statusCode(), "body" to res.body()) }`.  
- Category: Integration & API  
- Example Properties: { "method": "GET", "payload": "mapOf('url' to 'https://api.example.com/data?query=${row[\"data\"]}', 'headers' to mapOf('Authorization
- Detailed Example Input: Table with rows [{ "id": 1, "data": "query=hello" }, { "id": 2, "data": "query=world" }], method="GET", script: "mapOf('url' to 'https://api.example.com/search?${row['data']}', 'headers' to mapOf('Content-Type' to 'application/json'))".  
- Detailed Example Output: [{ "id": 1, "data": "query=hello", "response": { "status": 200, "body": "{results: [... ]}" } }, { "id": 2, "data": "query=world", "response": { "status": 200, "body": "{results: [... ]}" } }].

## JSON to Table Node
- no input ports.  
- Properties: "jsonArrayString" (string, format 'code' language 'json').
- Output ports: 1, named "data".  
- Behavior: parse jsonArrayString into a Table format, where each object in the JSON array becomes a row in the Table, and keys become column names. This allows users to easily convert raw JSON data into a structured format for further processing in the workflow. The node should handle parsing errors gracefully, returning an empty table or an error message if the input is not valid JSON. The node should also ensure that all rows have the same columns, filling in nulls for missing keys in any given object to maintain a consistent table structure. any kind of error happenig because of users input should be thrown as NodeRuntimeException with isRetriable as false.
- Thinking: Use a JSON parsing library to convert the input string into a list of maps, then create a Table object from that list. Code snippet: `val jsonArray = JacksonMapper.readValue(jsonArrayString, List::class.java) as List<Map<String, Any>>; Table(jsonArray)`.  
- Category: Table & Data Structures  
- Example Properties: { "jsonArrayString": "[{ \"id\": 1, \"name\": \"Alice\" }, { \"id\": 2, \"name\": \"Bob\" }]" }
- Detailed Example Input: N/A (no input ports).  
- Detailed Example Output: Table with rows [{ "id": 1, "name": "Alice" }, { "id": 2, "name": "Bob" }].


## Table Diff Checker
- two input ports: "tableA" and "tableB", both Table.