[
<#-- Math Q&A examples -->
<#list 1..50 as i>
  <#assign a = (i * 7) % 100 + 1>
  <#assign b = (i * 13) % 100 + 1>
  {"instruction": "What is ${a} + ${b}?", "response": "The sum of ${a} and ${b} is ${a + b}."},
</#list>

<#-- Multiplication examples -->
<#list 1..50 as i>
  <#assign a = (i % 12) + 1>
  <#assign b = (i % 10) + 1>
  {"instruction": "Calculate ${a} × ${b}", "response": "${a} × ${b} = ${a * b}"},
</#list>

<#-- Temperature conversion examples -->
<#list 0..100 as celsius>
  <#if celsius % 10 == 0>
  <#assign fahrenheit = (celsius * 9 / 5) + 32>
  {"instruction": "Convert ${celsius}°C to Fahrenheit", "response": "${celsius}°C equals ${fahrenheit}°F. Formula: F = (C × 9/5) + 32"},
  </#if>
</#list>

<#-- Programming concepts -->
<#assign concepts = [
  {"topic": "variable", "definition": "A named storage location in memory that holds a value which can be changed during program execution"},
  {"topic": "function", "definition": "A reusable block of code that performs a specific task and can accept inputs (parameters) and return outputs"},
  {"topic": "loop", "definition": "A control structure that repeats a block of code multiple times until a condition is met"},
  {"topic": "array", "definition": "A data structure that stores a collection of elements, typically of the same type, in contiguous memory locations"},
  {"topic": "class", "definition": "A blueprint for creating objects that defines properties (attributes) and behaviors (methods)"},
  {"topic": "inheritance", "definition": "An OOP mechanism where a class can inherit properties and methods from a parent class"},
  {"topic": "API", "definition": "Application Programming Interface - a set of protocols and tools that allows different software applications to communicate"},
  {"topic": "recursion", "definition": "A programming technique where a function calls itself to solve smaller instances of the same problem"}
]>
<#list concepts as concept>
  {"instruction": "What is a ${concept.topic} in programming?", "response": "${concept.definition}"}<#if concept?has_next>,</#if>
</#list>
]