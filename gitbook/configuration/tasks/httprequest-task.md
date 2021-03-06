# HttpRequest Task

OpenEndpoints supports any kind of HTTP request to other systems. You can call and fetch data from

* REST Api
* SOAP interface
* simple URL that returns content

Examples:

* fetch data from any CRM or ERP system (as long as it offers a REST or SOAP API that can be called from the internet)
* upload generated files to a CRM or an archive
* fetch the next available invoice-number from your accounting system, generate an invoice and send it as an email
* validate the existence of an address by calling an external validation service

{% hint style="info" %}
#### Task vs Data-Source

Interaction with external APIs is available for both, loading data for the purpose of a data-source transformation (see [Load Data from any REST-API](../data-source-transformation/load-data-from-any-rest-api.md)), or for the execution of a task - which is content of this section. Technically both types of application are very similar, but there are some differences:

* A task can have a "condition" based on a parameter value. While a request as part of a data source will always be triggered (on using the data source), a task may be triggered only if a certain condition applies.
* The response body of a task can be parsed to generate values that do not come from the user's request, instead they come from that task. Such "intermediate values" can be used like parameters in subsequent tasks.
{% endhint %}

## Basic Syntax

```xml
<task class="endpoints.task.HttpRequestTask">
    <!-- mandatory -->
    <url>http://www.google.com/foo/${variable}</url>
    <!-- optional, default GET -->
    <method name="POST"/>
    <!-- zero or more - provided method is GET -->
    <get-parameter name="foo">${variable}</get-parameter>
    <!-- zero or more - provided method is POST -->
    <post-parameter name="foo">${variable}</post-parameter>
    <!-- zero or more -->
    <request-header name="foo">${variable}</request-header>
    <!-- optional -->
    <basic-access-authentication username="${variable}" password="${variable}"/>
    <!-- either one <xml-body>, or <json-body>, or neither -->
    <json-body>...</json-body>
</task>
```

This task performs an HTTP request, checks the response is a 2xx OK, and ignores the response body.

Redirects are not followed.

The attribute ignore-if-error="true" may be present on the \<task> element to indicate that if an error occurs (e.g. server not found, non-2xx response, etc.) this error is ignored. By default, the error aborts the processing of the endpoint.

```xml
<xml-from-url ignore-if-error="true">...</xml-from-url>
```

## Request Body

:bulb:The beauty of \<OpenEndpoints/> shows in the solution of the optional request body, which can be json or xml. There are several different options how-to build the content for the request-body.

### XML-Request Body with Inline Contents

The request body is expressed as xml within the \<xml-body> tag. Endpoint parameters are expanded.

```xml
<task class="endpoints.task.HttpRequestTask">
     ...
    <xml-body>
        <your-tag>${parameter}</your-tag>
    </xml-body>
    ...
</task>
```

**Uploaded content** encoded in base64 can be filled into any tag of the request body. This requires 2 actions:

* Add **attribute upload-files="true"** to \<xml-from-url>
* Add to any element of your request body attributes **upload-field-name="foo" encoding="base64"**

The uploaded content will expand into that xml element.

{% hint style="warning" %}
#### base64 encoded content only

The expansion of uploaded content works for base 64 encoded content only!
{% endhint %}

It is also possible to send **generated content** within a request body:

* Add **attribute expand-transformations="true"** to \<xml-from-url>
* Add to any element of your request body attributes **xslt-transformation="foo" encoding="base64"**

iAdding that attribute to the element indicates that the transformation with that name should be executed (for example, generate a PDF file), and the contents of the resulting file should be placed in this tag. The encoding is always base64, no other encodings are supported.

### XML-Request Body from Transformation

The request body is generated by XSLT. This leaves maximum flexibility to build different content of the request body depending on endpoint parameter values!

```xml
<task class="endpoints.task.HttpRequestTask">
    ...
    <xml-body xslt-file="foo.xslt"/>
    ...
</task>
```

Note that this is a transformation within a transformation. The XSLT takes a \<parameters> as its input document; This XSLT does not have access to the results of any other data sources. The reason is, that data sources cannot use data produced by another data source.

* The XSLT file is taken from the http-xslt directory.
* The transformation-input to apply that XSLT has \<parameters> as its root tag.

```xml
<parameters>
    <parameter name="foo" value="abc"/>
    <parameter name="long" employees="def"/>
    ...
</parameters>
```

The optional attribute upload-files="true" and expand-transformations="true" may be present as above.

### JSON-Request Body with Inline Contents

The request body is expressed as json within the \<json-body> tag. Endpoint parameters are expanded.

```xml
<parameters>
    <parameter name="foo" value="abc"/>
    <parameter name="long" employees="def"/>
    ...
</parameters>
```

Endpoint parameters are expanded within the string context of JSON, that is to say that no concern about escaping is necessary.

Options for expanding base 64 content from file upload or generated content is not available for JSON.

### JSON-Request Body from Transformation

The request body is generated by XSLT. That requires that the result of the transformation is valid JSON.

```xml
<task class="endpoints.task.HttpRequestTask">
    ...
    <json-body xslt-file="foo.xslt"/>
    ...
</task>
```

Note that this is a transformation within a transformation. The XSLT takes a \<parameters> as its input document; This XSLT does not have access to the results of any other data sources. The reason is, that data sources cannot use data produced by another data source.

* The XSLT file is taken from the http-xslt directory.
* The transformation-input to apply that XSLT has \<parameters> as its root tag.

```xml
<parameters>
    <parameter name="foo" value="abc"/>
    <parameter name="long" employees="def"/>
    ...
</parameters>
```

Options for expanding base 64 content from file upload or generated content is not available for JSON.
