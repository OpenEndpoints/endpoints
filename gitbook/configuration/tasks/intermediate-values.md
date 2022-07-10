# Intermediate Values

In addition to parameters there are Intermediate Values . These are like parameters, but they do not come from the user’s request, instead they come from other tasks.

For example, imagine a CRM which requires 2 separate calls to

1. fetch the next available id to insert a new customerinsert a new customer, with that id (from irst request) as a mandatory parameter
2. insert a new customer, with that id (from irst request) as a mandatory parameter

In this case the first \<task> will fetch the id, and make it available as an input to the second task. This is an “intermediate value”.

* Intermediate values can be referenced as ${value} just like normal parameters.
* Intermediate values may not have the same name as a parameter declared in “endpoints.xml”. (If intermediate values could have the same names, then ${value} could be ambiguous.)
* Tasks must explicitly specify which intermediate values they output and which they input. Any task may accept any input intermediate value, however, the output of an intermediate value is task-specific. (For example, HTTP Tasks parse the response, but there is no useful way for an email task to output a variable.)
* A task which outputs intermediate values may not be optional (with if and equals attributes). That is because the output variables will be used by other tasks, therefore the task must always run.

For example:

```xml
<task class="endpoints.task.HttpRequestTask">
    ...
    <output-intermediate-value name="invoice-number"/>
</task>

<task class="endpoints.task.HttpRequestTask">
    <input-intermediate-value name="invoice-number"/>
    ...
</task>
```

To produce an **\<output-intermediate-value>** from a response-body, one of the following syntaxes must be used:

```xml
<task class="endpoints.task.HttpRequestTask">
    ...
    <output-intermediate-value
        name="invoice-number"
        xpath="/foo/bar"
        regex=" d+"/>
</task>

<task class="endpoints.task.HttpRequestTask">
    ...
    <output-intermediate-value
        name="invoice-number"
        jsonpath="$.invoiceNumbers[:1].value"
        regex=" d+"/>
</task>
```

The former requires that the result of the response be XML, the latter that it be JSON. No attempt is made to convert the response between XML and JSON. The regex attribute is optional.

## Using Intermediate Values in data-source-xslt

ssIn case you want to use the intermediate value within a regular data-source-xslt, then you need to declare it within the \<success> tags:

```xml
<success>
    <input-intermediate-value name="name-of-intermediate-variable"/>
        <response-transformation name="name-of-transformer"/>
</success>
```
