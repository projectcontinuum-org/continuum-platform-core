# KNIME Base Nodes Translation to Continuum

Based on KNIME Analytics Platform core nodes from org.knime.features.base, here are comprehensive translations:

## 1. CSV Reader
One input port: "file" (File/Path reference).
Properties: "filePath" (String, Material.Input.TextField), "columnDelimiter" (String, Material.Input.TextField, default ","), "hasColumnHeader" (Boolean, Material.Input.Checkbox), "hasRowIDs" (Boolean, Material.Input.Checkbox), "skipEmptyRows" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Reads CSV file from filesystem, parses according to delimiter and header settings, outputs Table with typed columns.
Thinking: Continuum uses Parquet/Arrow backend, so parse CSV to Arrow table with schema inference. Handle encoding, quote chars, escape sequences. Material UI for file picker and delimiter config.
Code snippet: `class CSVReaderNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val path = props.getString("filePath"); val table = ArrowCSVReader.read(path, delimiter = props.getString("columnDelimiter"), hasHeader = props.getBoolean("hasColumnHeader")); return listOf(TablePortData(table)) } }`
Detailed Example Input: File path "/data/sales.csv" with content: "Region,Sales,Year\nNorth,100,2023\nSouth,150,2023".
Detailed Example Output: Table with 3 columns (Region: String, Sales: Int64, Year: Int64), 2 rows.

## 2. Row Filter
One input port: "data", Table.
Properties: "filterColumn" (String, Material.Input.ColumnSelector), "filterOperator" (Enum, Material.Input.Dropdown: "equals", "greater than", "less than", "contains", "matches regex"), "filterValue" (String, Material.Input.TextField), "includeMatches" (Boolean, Material.Input.Checkbox, default true).
Output ports: 1, named "data".
Behavior: Filters rows based on condition applied to selected column, either including or excluding matches.
Thinking: Use Arrow compute functions for efficient filtering. Support various operators with type-aware comparisons. Build filter expression and apply to Arrow table.
Code snippet: `class RowFilterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val filtered = table.filter { row -> evaluateCondition(row[props.getString("filterColumn")], props.getString("filterOperator"), props.getString("filterValue")) }; return listOf(TablePortData(filtered)) } }`
Detailed Example Input: Table with columns [Name: String, Age: Int], rows [("Alice", 30), ("Bob", 25), ("Carol", 35)]. Filter: Age > 28.
Detailed Example Output: Table with rows [("Alice", 30), ("Carol", 35)].

## 3. Column Filter
One input port: "data", Table.
Properties: "includeColumns" (List<String>, Material.Input.MultiColumnSelector), "filterMode" (Enum, Material.Input.Dropdown: "include", "exclude").
Output ports: 1, named "data".
Behavior: Selects or excludes specified columns from input table.
Thinking: Arrow table projection - select column indices to keep. Efficient since no data copying, just metadata changes.
Code snippet: `class ColumnFilterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val cols = props.getStringList("includeColumns"); val filtered = if (props.getString("filterMode") == "include") table.selectColumns(cols) else table.dropColumns(cols); return listOf(TablePortData(filtered)) } }`
Detailed Example Input: Table with columns [Name, Age, City, Country], 10 rows. Include: [Name, Age].
Detailed Example Output: Table with columns [Name, Age], 10 rows.

## 4. String Manipulation
One input port: "data", Table.
Properties: "inputColumn" (String, Material.Input.ColumnSelector), "expression" (String, Material.Input.CodeEditor with syntax highlighting), "outputColumn" (String, Material.Input.TextField), "replaceColumn" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Applies string transformation expression to column, supporting functions like replace, substring, uppercase, lowercase, trim, concat, regex.
Thinking: Implement expression DSL or use Arrow string compute functions. Parse expression, validate, apply row-by-row or vectorized.
Code snippet: `class StringManipulationNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val expr = props.getString("expression"); val result = table.addColumn(props.getString("outputColumn"), evaluateStringExpression(table[props.getString("inputColumn")], expr)); return listOf(TablePortData(result)) } }`
Detailed Example Input: Table with column [Email: String] = ["alice@example.com", "bob@test.org"]. Expression: "substring(Email, 0, indexOf(Email, '@'))".
Detailed Example Output: Table with added column [Username: String] = ["alice", "bob"].

## 5. Math Formula
One input port: "data", Table.
Properties: "formula" (String, Material.Input.CodeEditor with math syntax), "outputColumn" (String, Material.Input.TextField), "replaceColumn" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Evaluates mathematical expression referencing column names, supports operators (+, -, *, /, ^), functions (sin, cos, log, sqrt, abs, round, ceil, floor).
Thinking: Parse math expression to AST, validate column references and types, evaluate using Arrow compute or JVM math libraries.
Code snippet: `class MathFormulaNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val formula = props.getString("formula"); val computed = table.addColumn(props.getString("outputColumn"), evaluateMathFormula(table, formula)); return listOf(TablePortData(computed)) } }`
Detailed Example Input: Table with columns [Price: Double, Tax: Double] = [(100.0, 0.08), (200.0, 0.08)]. Formula: "Price * (1 + Tax)".
Detailed Example Output: Table with added column [Total: Double] = [108.0, 216.0].

## 6. Joiner
Two input ports: "left" Table, "right" Table.
Properties: "joinType" (Enum, Material.Input.Dropdown: "inner", "left outer", "right outer", "full outer"), "leftJoinColumn" (String, Material.Input.ColumnSelector from left), "rightJoinColumn" (String, Material.Input.ColumnSelector from right), "columnSuffix" (String, Material.Input.TextField, default "_right").
Output ports: 1, named "data".
Behavior: Performs SQL-style join on specified columns, merging tables based on join type.
Thinking: Use Arrow hash join implementation or implement efficient join algorithm. Handle duplicate column names with suffix.
Code snippet: `class JoinerNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val left = inputs[0] as TablePortData; val right = inputs[1] as TablePortData; val joined = ArrowJoin.join(left, right, leftKey = props.getString("leftJoinColumn"), rightKey = props.getString("rightJoinColumn"), joinType = props.getString("joinType")); return listOf(TablePortData(joined)) } }`
Detailed Example Input: Left table [ID: Int, Name: String] = [(1, "Alice"), (2, "Bob")]. Right table [ID: Int, City: String] = [(1, "NYC"), (3, "LA")]. Inner join on ID.
Detailed Example Output: Table [ID: Int, Name: String, City: String] = [(1, "Alice", "NYC")].

## 7. GroupBy
One input port: "data", Table.
Properties: "groupColumns" (List<String>, Material.Input.MultiColumnSelector), "aggregations" (List<AggConfig>, Material.Input.AggregationBuilder: each has targetColumn, function: "sum", "count", "mean", "min", "max", "median", "stddev", outputName).
Output ports: 1, named "data".
Behavior: Groups rows by specified columns, applies aggregation functions to other columns.
Thinking: Implement efficient hash-based grouping with Arrow. Support multiple aggregations per group. Output schema has group columns plus aggregated columns.
Code snippet: `class GroupByNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val grouped = ArrowGroupBy.groupBy(table, groupCols = props.getStringList("groupColumns"), aggs = props.getAggregations("aggregations")); return listOf(TablePortData(grouped)) } }`
Detailed Example Input: Table [Region: String, Product: String, Sales: Int] = [("North", "A", 100), ("North", "B", 150), ("South", "A", 200)]. Group by Region, sum Sales.
Detailed Example Output: Table [Region: String, Sales_sum: Int] = [("North", 250), ("South", 200)].

## 8. Sorter
One input port: "data", Table.
Properties: "sortColumns" (List<SortConfig>, Material.Input.SortBuilder: each has column name, order: "ascending"/"descending").
Output ports: 1, named "data".
Behavior: Sorts table rows by specified columns in order of priority.
Thinking: Use Arrow sort functions with multi-column sort keys. Efficient in-place or copy-on-write sorting.
Code snippet: `class SorterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val sorted = ArrowSort.sort(table, sortKeys = props.getSortConfigs("sortColumns")); return listOf(TablePortData(sorted)) } }`
Detailed Example Input: Table [Name: String, Age: Int] = [("Carol", 35), ("Alice", 30), ("Bob", 25)]. Sort by Age ascending.
Detailed Example Output: Table [Name: String, Age: Int] = [("Bob", 25), ("Alice", 30), ("Carol", 35)].

## 9. Concatenate
Two input ports: "top" Table, "bottom" Table.
Properties: "handleDifferentColumns" (Enum, Material.Input.Dropdown: "fail", "union", "intersection"), "appendRowIDs" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Stacks tables vertically (row-wise concatenation), handling schema mismatches based on mode.
Thinking: Arrow record batch concatenation. Union mode fills missing columns with nulls. Intersection mode keeps only common columns.
Code snippet: `class ConcatenateNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val top = inputs[0] as TablePortData; val bottom = inputs[1] as TablePortData; val concatenated = ArrowConcat.concat(top, bottom, mode = props.getString("handleDifferentColumns")); return listOf(TablePortData(concatenated)) } }`
Detailed Example Input: Top table [Name: String, Age: Int] = [("Alice", 30)]. Bottom table [Name: String, Age: Int] = [("Bob", 25)].
Detailed Example Output: Table [Name: String, Age: Int] = [("Alice", 30), ("Bob", 25)].

## 10. Statistics
One input port: "data", Table.
Properties: "selectedColumns" (List<String>, Material.Input.MultiColumnSelector), "statistics" (List<String>, Material.Input.MultiCheckbox: "count", "sum", "mean", "min", "max", "variance", "stddev", "median").
Output ports: 1, named "data".
Behavior: Computes descriptive statistics for selected numeric columns, outputs table with statistic name and values per column.
Thinking: Use Arrow compute functions for aggregations. Output pivoted format with rows for each statistic type.
Code snippet: `class StatisticsNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val stats = ArrowStats.compute(table, columns = props.getStringList("selectedColumns"), metrics = props.getStringList("statistics")); return listOf(TablePortData(stats)) } }`
Detailed Example Input: Table [Value: Double] = [10.0, 20.0, 30.0, 40.0, 50.0]. Compute: mean, min, max.
Detailed Example Output: Table [Statistic: String, Value: Double] = [("mean", 30.0), ("min", 10.0), ("max", 50.0)].

## 11. Rule Engine
One input port: "data", Table.
Properties: "rules" (List<RuleConfig>, Material.Input.RuleBuilder: each has condition expression, outcome value), "defaultOutcome" (String, Material.Input.TextField), "outputColumn" (String, Material.Input.TextField).
Output ports: 1, named "data".
Behavior: Evaluates rules sequentially per row, assigns first matching outcome to new column, uses default if no match.
Thinking: Parse rule conditions to boolean expressions, evaluate against row values. Support operators: =, !=, <, >, <=, >=, AND, OR, IN.
Code snippet: `class RuleEngineNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val result = table.addColumn(props.getString("outputColumn")) { row -> evaluateRules(row, props.getRules("rules"), props.getString("defaultOutcome")) }; return listOf(TablePortData(result)) } }`
Detailed Example Input: Table [Age: Int] = [15, 25, 65]. Rules: "Age < 18" → "Minor", "Age >= 65" → "Senior", default "Adult".
Detailed Example Output: Table with added column [Category: String] = ["Minor", "Adult", "Senior"].

## 12. Missing Value
One input port: "data", Table.
Properties: "columnSettings" (List<ColumnConfig>, Material.Input.ColumnMissingValueBuilder: each has column name, strategy: "remove row", "forward fill", "backward fill", "fixed value", "mean", "median"), "fixedValue" (String, Material.Input.TextField).
Output ports: 1, named "data".
Behavior: Handles null/missing values per column using specified strategy.
Thinking: Iterate columns, apply strategy. Remove row affects all columns. Fill strategies use Arrow compute or manual iteration.
Code snippet: `class MissingValueNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val cleaned = ArrowMissingValue.handle(table, configs = props.getColumnConfigs("columnSettings")); return listOf(TablePortData(cleaned)) } }`
Detailed Example Input: Table [Value: Double?] = [10.0, null, 30.0, null]. Strategy: forward fill.
Detailed Example Output: Table [Value: Double] = [10.0, 10.0, 30.0, 30.0].

## 13. Duplicate Row Filter
One input port: "data", Table.
Properties: "compareColumns" (List<String>, Material.Input.MultiColumnSelector, default all), "keepFirst" (Boolean, Material.Input.RadioButton: true="first occurrence", false="last occurrence"), "unique" (Boolean, Material.Input.Checkbox, default false for keeping one).
Output ports: 1, named "data".
Behavior: Removes duplicate rows based on selected columns, keeping first or last occurrence.
Thinking: Use hash-based deduplication. Track seen row signatures (hash of selected column values). Efficient with Arrow.
Code snippet: `class DuplicateRowFilterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val unique = ArrowDeduplicate.removeDuplicates(table, columns = props.getStringList("compareColumns"), keepFirst = props.getBoolean("keepFirst")); return listOf(TablePortData(unique)) } }`
Detailed Example Input: Table [ID: Int, Name: String] = [(1, "Alice"), (2, "Bob"), (1, "Alice"), (3, "Carol")]. Compare all columns, keep first.
Detailed Example Output: Table [ID: Int, Name: String] = [(1, "Alice"), (2, "Bob"), (3, "Carol")].

## 14. Pivot
One input port: "data", Table.
Properties: "groupColumns" (List<String>, Material.Input.MultiColumnSelector), "pivotColumn" (String, Material.Input.ColumnSelector), "valueColumn" (String, Material.Input.ColumnSelector), "aggregation" (Enum, Material.Input.Dropdown: "sum", "mean", "count", "min", "max").
Output ports: 1, named "data".
Behavior: Transforms rows into columns based on pivot column values, aggregating value column grouped by other columns.
Thinking: Complex operation: group by (groupCols + pivotCol), aggregate valueCol, then reshape so pivotCol values become column names.
Code snippet: `class PivotNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val pivoted = ArrowPivot.pivot(table, groupCols = props.getStringList("groupColumns"), pivotCol = props.getString("pivotColumn"), valueCol = props.getString("valueColumn"), agg = props.getString("aggregation")); return listOf(TablePortData(pivoted)) } }`
Detailed Example Input: Table [Region: String, Product: String, Sales: Int] = [("North", "A", 100), ("North", "B", 150), ("South", "A", 200)]. Group: Region, Pivot: Product, Value: Sales (sum).
Detailed Example Output: Table [Region: String, A: Int, B: Int?] = [("North", 100, 150), ("South", 200, null)].

## 15. Column Resorter
One input port: "data", Table.
Properties: "columnOrder" (List<String>, Material.Input.ColumnOrderEditor with drag-and-drop).
Output ports: 1, named "data".
Behavior: Reorders columns according to specified order, unlisted columns appended at end.
Thinking: Arrow table projection with custom column order. Metadata operation, no data copy.
Code snippet: `class ColumnResorterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val reordered = table.reorderColumns(props.getStringList("columnOrder")); return listOf(TablePortData(reordered)) } }`
Detailed Example Input: Table with columns [City, Name, Age, Country]. New order: [Name, Age, City, Country].
Detailed Example Output: Table with columns [Name, Age, City, Country], same data.

## 16. Column Rename
One input port: "data", Table.
Properties: "renameConfig" (List<RenameConfig>, Material.Input.RenameBuilder: each has oldName, newName), "allowDuplicates" (Boolean, Material.Input.Checkbox, default false).
Output ports: 1, named "data".
Behavior: Renames specified columns, validates no duplicate names unless allowed.
Thinking: Arrow schema modification. Create new schema with updated column names, wrap existing data.
Code snippet: `class ColumnRenameNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val renamed = table.renameColumns(props.getRenameConfigs("renameConfig")); return listOf(TablePortData(renamed)) } }`
Detailed Example Input: Table columns [first_name, last_name, age_years]. Rename: first_name→firstName, last_name→lastName, age_years→age.
Detailed Example Output: Table columns [firstName, lastName, age], same data.

## 17. Binner
One input port: "data", Table.
Properties: "binColumn" (String, Material.Input.ColumnSelector), "binningMethod" (Enum, Material.Input.Dropdown: "fixed bins", "quantile", "custom edges"), "numBins" (Int, Material.Input.NumberField), "binEdges" (List<Double>, Material.Input.ArrayEditor), "outputColumn" (String, Material.Input.TextField), "outputLabels" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Discretizes continuous numeric column into categorical bins based on method.
Thinking: Compute bin edges from method, then assign each value to bin. Output bin indices or labels like "[0-10)", "[10-20)".
Code snippet: `class BinnerNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val binned = table.addColumn(props.getString("outputColumn")) { row -> assignBin(row[props.getString("binColumn")], computeBinEdges(props)) }; return listOf(TablePortData(binned)) } }`
Detailed Example Input: Table [Score: Double] = [15.0, 35.0, 55.0, 75.0, 95.0]. Fixed 5 bins [0-100].
Detailed Example Output: Table with added column [ScoreBin: String] = ["[0-20)", "[20-40)", "[40-60)", "[60-80)", "[80-100)"].

## 18. Row Splitter
One input port: "data", Table.
Properties: "splitMode" (Enum, Material.Input.Dropdown: "first N rows", "percentage", "row pattern"), "numRows" (Int, Material.Input.NumberField), "percentage" (Double, Material.Input.NumberField), "pattern" (Int, Material.Input.NumberField for "every Nth row").
Output ports: 2, named "top" and "bottom".
Behavior: Splits input table into two outputs based on criteria.
Thinking: Calculate split index, use Arrow slicing. Output two table references without data duplication.
Code snippet: `class RowSplitterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val splitIdx = calculateSplitIndex(table.numRows, props); return listOf(TablePortData(table.slice(0, splitIdx)), TablePortData(table.slice(splitIdx, table.numRows))) } }`
Detailed Example Input: Table with 100 rows. Split: first 30 rows.
Detailed Example Output: Port 0 (top): Table with rows 0-29. Port 1 (bottom): Table with rows 30-99.

## 19. Column Appender
Two input ports: "left" Table, "right" Table.
Properties: "handleDifferentRowCounts" (Enum, Material.Input.Dropdown: "fail", "skip extra rows", "fill with missing").
Output ports: 1, named "data".
Behavior: Appends columns from right table to left table horizontally, matching by row index.
Thinking: Combine schemas, concatenate columns. Requires aligned row counts or handling mismatches.
Code snippet: `class ColumnAppenderNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val left = inputs[0] as TablePortData; val right = inputs[1] as TablePortData; val appended = ArrowColumnAppend.append(left, right, mode = props.getString("handleDifferentRowCounts")); return listOf(TablePortData(appended)) } }`
Detailed Example Input: Left [Name: String] = ["Alice", "Bob"]. Right [Age: Int] = [30, 25].
Detailed Example Output: Table [Name: String, Age: Int] = [("Alice", 30), ("Bob", 25)].

## 20. Value Counter
One input port: "data", Table.
Properties: "targetColumn" (String, Material.Input.ColumnSelector), "outputMode" (Enum, Material.Input.Dropdown: "frequency table", "percentage").
Output ports: 1, named "data".
Behavior: Counts occurrences of unique values in column, outputs frequency or percentage table.
Thinking: Group by target column with count aggregation. Optional percentage calculation.
Code snippet: `class ValueCounterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val counts = ArrowGroupBy.groupBy(table, groupCols = listOf(props.getString("targetColumn")), aggs = listOf(AggConfig("*", "count", "Count"))); return listOf(TablePortData(counts)) } }`
Detailed Example Input: Table [Product: String] = ["A", "B", "A", "A", "C", "B"]. Count Product.
Detailed Example Output: Table [Product: String, Count: Int] = [("A", 3), ("B", 2), ("C", 1)].

## 21. File Reader
One input port: optional "file" (Path reference).
Properties: "filePath" (String, Material.Input.FilePicker), "fileType" (Enum, Material.Input.Dropdown: auto-detect or specific: "csv", "excel", "parquet", "json"), "readOptions" (JSON, Material.Input.OptionsPanel for type-specific settings).
Output ports: 1, named "data".
Behavior: Generic file reader supporting multiple formats with auto-detection.
Thinking: Detect format from extension or content sniffing. Delegate to appropriate parser (CSV, Parquet, JSON, Excel). Unified interface.
Code snippet: `class FileReaderNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val path = props.getString("filePath"); val type = detectOrUseFileType(path, props.getString("fileType")); val table = when(type) { "csv" -> ArrowCSVReader.read(path); "parquet" -> ArrowParquetReader.read(path); else -> throw Exception("Unsupported") }; return listOf(TablePortData(table)) } }`
Detailed Example Input: File path "/data/sales.parquet".
Detailed Example Output: Table loaded from Parquet file with native schema and data.

## 22. Excel Reader
One input port: "file" (File reference).
Properties: "filePath" (String, Material.Input.FilePicker), "sheetName" (String, Material.Input.TextField or dropdown), "hasColumnHeader" (Boolean, Material.Input.Checkbox), "skipRows" (Int, Material.Input.NumberField), "readRange" (String, Material.Input.TextField, e.g., "A1:D100").
Output ports: 1, named "data".
Behavior: Reads Excel (.xlsx, .xls) files, specific sheet and range.
Thinking: Use Apache POI or similar for Excel parsing. Convert to Arrow table. Handle formulas vs values, date formats.
Code snippet: `class ExcelReaderNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val path = props.getString("filePath"); val table = ExcelParser.read(path, sheet = props.getString("sheetName"), hasHeader = props.getBoolean("hasColumnHeader"), skipRows = props.getInt("skipRows")); return listOf(TablePortData(table)) } }`
Detailed Example Input: File "/reports/Q4.xlsx", sheet "Sales", has header, skip 2 rows.
Detailed Example Output: Table with columns from Excel header row, data rows converted to appropriate types.

## 23. CSV Writer
One input port: "data", Table.
Properties: "filePath" (String, Material.Input.FilePicker with save mode), "columnDelimiter" (String, Material.Input.TextField, default ","), "writeColumnHeader" (Boolean, Material.Input.Checkbox, default true), "appendMode" (Boolean, Material.Input.Checkbox), "compression" (Enum, Material.Input.Dropdown: "none", "gzip", "snappy").
Output ports: 0 (writer node).
Behavior: Writes table to CSV file with specified settings.
Thinking: Arrow to CSV conversion. Stream writing for large tables. Handle quoting, escaping. Compression wrapper.
Code snippet: `class CSVWriterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; ArrowCSVWriter.write(table, path = props.getString("filePath"), delimiter = props.getString("columnDelimiter"), writeHeader = props.getBoolean("writeColumnHeader")); return emptyList() } }`
Detailed Example Input: Table [Name: String, Score: Int] = [("Alice", 95), ("Bob", 87)]. Write to "/output/scores.csv".
Detailed Example Output: File created with content: "Name,Score\nAlice,95\nBob,87\n".

## 24. ARFF Reader
One input port: "file" (File reference).
Properties: "filePath" (String, Material.Input.FilePicker).
Output ports: 1, named "data".
Behavior: Reads ARFF (Attribute-Relation File Format) files used in Weka/machine learning.
Thinking: Parse ARFF header for attribute definitions (numeric, nominal, string, date), then parse data section. Convert to Arrow schema.
Code snippet: `class ARFFReaderNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val path = props.getString("filePath"); val table = ARFFParser.read(path); return listOf(TablePortData(table)) } }`
Detailed Example Input: File "/ml/iris.arff" with @RELATION, @ATTRIBUTE, @DATA sections.
Detailed Example Output: Table with columns per attributes (sepal_length: Double, sepal_width: Double, class: String), data rows.

## 25. Table Creator
No input ports.
Properties: "columnDefinitions" (List<ColDef>, Material.Input.SchemaBuilder: each has name, type, defaultValue), "numRows" (Int, Material.Input.NumberField), "data" (2D Array, Material.Input.DataGrid for manual entry).
Output ports: 1, named "data".
Behavior: Creates table from scratch with specified schema and data.
Thinking: Build Arrow schema from definitions, populate with data. Useful for testing or small manual datasets.
Code snippet: `class TableCreatorNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val schema = buildSchema(props.getColDefs("columnDefinitions")); val table = ArrowTableBuilder.create(schema, props.getData("data")); return listOf(TablePortData(table)) } }`
Detailed Example Input: Columns: [Name: String, Age: Int]. Data: [["Alice", 30], ["Bob", 25]].
Detailed Example Output: Table [Name: String, Age: Int] = [("Alice", 30), ("Bob", 25)].

## 26. Column Aggregator
One input port: "data", Table.
Properties: "aggregationMode" (Enum, Material.Input.Dropdown: "across rows", "across columns"), "targetColumns" (List<String>, Material.Input.MultiColumnSelector), "aggregationFunction" (Enum, Material.Input.Dropdown: "sum", "mean", "min", "max", "count"), "outputColumn" (String, Material.Input.TextField).
Output ports: 1, named "data".
Behavior: Aggregates multiple columns per row into single value using specified function.
Thinking: Row-wise operation if "across columns", computing aggregation horizontally. Different from GroupBy which is vertical.
Code snippet: `class ColumnAggregatorNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val result = table.addColumn(props.getString("outputColumn")) { row -> aggregateColumns(row, props.getStringList("targetColumns"), props.getString("aggregationFunction")) }; return listOf(TablePortData(result)) } }`
Detailed Example Input: Table [Q1: Int, Q2: Int, Q3: Int, Q4: Int] = [(100, 150, 120, 180)]. Aggregate: sum across [Q1, Q2, Q3, Q4].
Detailed Example Output: Table with added column [Total: Int] = [550].

## 27. Row Sampling
One input port: "data", Table.
Properties: "samplingMode" (Enum, Material.Input.Dropdown: "first N", "random absolute", "random fraction", "stratified"), "sampleSize" (Int/Double, Material.Input.NumberField), "stratifyColumn" (String, Material.Input.ColumnSelector), "randomSeed" (Int, Material.Input.NumberField), "withReplacement" (Boolean, Material.Input.Checkbox).
Output ports: 1, named "data".
Behavior: Samples rows from input table according to method.
Thinking: Random sampling uses Arrow random indices generation. Stratified requires grouping and per-group sampling.
Code snippet: `class RowSamplingNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val sampled = ArrowSampling.sample(table, mode = props.getString("samplingMode"), size = props.getNumber("sampleSize"), seed = props.getInt("randomSeed")); return listOf(TablePortData(sampled)) } }`
Detailed Example Input: Table with 1000 rows. Random fraction: 0.1 (10%), seed 42.
Detailed Example Output: Table with ~100 rows randomly selected.

## 28. Transpose
One input port: "data", Table.
Properties: "useRowIDs" (Boolean, Material.Input.Checkbox, use first column as new column names), "useColumnNames" (Boolean, Material.Input.Checkbox, create new column from original column names).
Output ports: 1, named "data".
Behavior: Transposes table (rows become columns, columns become rows).
Thinking: Complex operation - pivot schema and data. Limited by memory for wide tables. Row ID becomes column names, column names become first column.
Code snippet: `class TransposeNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val transposed = ArrowTranspose.transpose(table, useRowIDs = props.getBoolean("useRowIDs")); return listOf(TablePortData(transposed)) } }`
Detailed Example Input: Table [Name: String, Q1: Int, Q2: Int] = [("Alice", 10, 20), ("Bob", 15, 25)]. Transpose with Name as headers.
Detailed Example Output: Table [Column: String, Alice: Int, Bob: Int] = [("Q1", 10, 15), ("Q2", 20, 25)].

## 29. Column Combiner
One input port: "data", Table.
Properties: "columns" (List<String>, Material.Input.MultiColumnSelector), "separator" (String, Material.Input.TextField, default " "), "outputColumn" (String, Material.Input.TextField), "quoteChar" (String, Material.Input.TextField, optional).
Output ports: 1, named "data".
Behavior: Combines multiple columns into single string column with separator.
Thinking: String concatenation with separator. Handle nulls (skip or use placeholder). Quote individual values if specified.
Code snippet: `class ColumnCombinerNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val result = table.addColumn(props.getString("outputColumn")) { row -> combineColumns(row, props.getStringList("columns"), props.getString("separator")) }; return listOf(TablePortData(result)) } }`
Detailed Example Input: Table [FirstName: String, LastName: String] = [("Alice", "Smith"), ("Bob", "Jones")]. Combine with separator " ".
Detailed Example Output: Table with added column [FullName: String] = ["Alice Smith", "Bob Jones"].

## 30. Column Splitter
One input port: "data", Table.
Properties: "inputColumn" (String, Material.Input.ColumnSelector), "splitMode" (Enum, Material.Input.Dropdown: "by delimiter", "by regex", "by position"), "delimiter" (String, Material.Input.TextField), "pattern" (String, Material.Input.TextField), "positions" (List<Int>, Material.Input.ArrayEditor), "outputColumns" (List<String>, Material.Input.ArrayEditor).
Output ports: 1, named "data".
Behavior: Splits single string column into multiple columns based on method.
Thinking: String split operation. Generate N new columns from split result. Handle variable split counts (fill with null or trim).
Code snippet: `class ColumnSplitterNode : ContinuumNodeModel() { override fun execute(inputs: List<PortData>, props: Properties): List<PortData> { val table = inputs[0] as TablePortData; val split = splitColumn(table[props.getString("inputColumn")], props); val result = table.addColumns(props.getStringList("outputColumns"), split); return listOf(TablePortData(result)) } }`
Detailed Example Input: Table [FullName: String] = ["Alice Smith", "Bob Jones"]. Split by delimiter " ", output [FirstName, LastName].
Detailed Example Output: Table with added columns [FirstName: String, LastName: String] = [("Alice", "Smith"), ("Bob", "Jones")].

---

## Notes

This comprehensive list is based on standard KNIME base node knowledge from org.knime.features.base. These 30 nodes represent core data manipulation, I/O, transformation, and analysis capabilities commonly used in KNIME workflows, all translated to Continuum's Kotlin/Arrow/Parquet architecture with Material UI properties.

### Key Translation Principles

1. **Port Data**: All table data uses `TablePortData` wrapping Apache Arrow tables stored as Parquet
2. **Properties**: Material UI components for all node configuration (TextField, Dropdown, Checkbox, ColumnSelector, etc.)
3. **Execution Model**: Kotlin `ContinuumNodeModel` with `execute()` method taking inputs and properties, returning outputs
4. **Efficiency**: Leverage Arrow's columnar format for efficient operations (filtering, projection, joins, aggregations)
5. **Type Safety**: Strong typing throughout with compile-time validation
6. **Security**: Validate file paths, sanitize inputs, prevent injection attacks

### Common Material UI Input Types

- `Material.Input.TextField`: Single-line text input
- `Material.Input.NumberField`: Numeric input with validation
- `Material.Input.Checkbox`: Boolean toggle
- `Material.Input.Dropdown`: Single selection from list
- `Material.Input.ColumnSelector`: Dropdown of available columns from input table
- `Material.Input.MultiColumnSelector`: Multiple column selection
- `Material.Input.FilePicker`: File browser with path selection
- `Material.Input.CodeEditor`: Multi-line editor with syntax highlighting
- `Material.Input.AggregationBuilder`: Complex UI for defining aggregations
- `Material.Input.SortBuilder`: UI for defining multi-column sorts
- `Material.Input.RuleBuilder`: UI for defining conditional rules

### Arrow Operations Reference

- `ArrowCSVReader.read()`: Parse CSV to Arrow table
- `ArrowParquetReader.read()`: Read Parquet files
- `table.filter()`: Row filtering with predicate
- `table.selectColumns()`: Column projection
- `table.addColumn()`: Add computed column
- `ArrowJoin.join()`: Hash join operation
- `ArrowGroupBy.groupBy()`: Aggregation with grouping
- `ArrowSort.sort()`: Multi-key sorting
- `ArrowConcat.concat()`: Vertical concatenation
- `ArrowStats.compute()`: Statistical computations
