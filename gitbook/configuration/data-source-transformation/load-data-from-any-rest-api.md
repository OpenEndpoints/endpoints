# Load Data from any REST-API

You can fetch content from any REST API and use it as a data-source in \<OnestopEndpoints/>.

The data-source command to fetch xml or json data from any URL is `<xml-from-url>`. JSON or HTML returned to this command will automatically converted into XML.

For JSON to XML conversion, any characters which would be illegal in XML (for example element name starting with a digit) replaced by `_xxxx_` containing their hex unicode character code.

## Basic Syntax

```xml
<data-source>
    <xml-from-url>
        <!-- mandatory -->
        <url>http://www.google.com/foo/${variable}</url>
        <!-- optional, default GET -->
        <method name="POST"/>
        <!-- zero or more -->
        <get-parameter name="foo">${variable}</get-parameter>
        <!-- zero or more -->
        <request-header name="foo">${variable}</request-header>
        <!-- optional -->
        <basic-access-authentication username="${variable}" password="${variable}"/>
        <!-- either one <xml-body>, or <json-body>, or neither -->
        <json-body>...</json-body>
    </xml-from-url>
<data-source>
```

**\<url>** is mandatory. That should ne no surprise ;-)

**\<method>** can be "POST" or "GET". If omitted "GET" will be used as a default.

Zero or many **\<get-parameter>**, zero or many **\<request-header>** and zero or one **\<basic-access-authentication>** - all optional.

## Request Body

$inline\[badge,Highlight,success] The beauty of \<OpenEndpoints/> shows in the solution of the optional request body, which can be JSON or XML. There are several different options how-to build the content for the request-body.

### XML-Request Body with Inline Contents

The request body is expressed as XML within the \<xml-body> tag. Endpoint parameters are expanded.

```xml
<xml-from-url>
    ...
    <xml-body>
        <your-tag>${parameter}</your-tag>
    </xml-body>
    ...
</xml-from-url>
```

**Uploaded content** encoded in base64 can be filled into any tag of the request body. This requires 2 actions:

* Add `attribute upload-files="true"` to `<xml-from-url>`
* Add to any element of your request body attributes `upload-field-name="foo" encoding="base64"`

The uploaded content will expand into that XML element.

{% hint style="warning" %}
#### base64 encoded content only

The expansion of uploaded content works for base 64 encoded content only!
{% endhint %}

It is also possible to send **generated content** within a request body:

* Add attribute `expand-transformations="true"` to `<xml-from-url>`
* Add to any element of your request body attributes `xslt-transformation="foo" encoding="base64"`

Adding that attribute to the element indicates that the transformation with that name should be executed (for example, generate a PDF file), and the contents of the resulting file should be placed in this tag. The encoding is always base64, no other encodings are supported.

### XML Request Body from Transformation

The request body is generated by XSLT. This leaves maximum flexibility to build different content of the request body depending on endpoint parameter values!

```xml
<!-- Data source definition -->
<xml-from-url>
    ...
    <xml-body xslt-file="foo.xslt"/>
    ...
</xml-from-url>
```

Note that this is a transformation within a transformation. The XSLT takes a \<parameters> as its input document; This XSLT does not have access to the results of any other data sources. The reason is, that data sources cannot use data produced by another data source.

* The XSLT file is taken from the http-xslt directory.
* The transformation-input to apply that XSLT has \<parameters> as its root tag.

```xml
<!-- Input to XSLT processing to build HTTP request body -->
<parameters>
    <parameter name="foo" value="abc"/>
    <parameter name="long" employees="def"/>
    ...
</parameters>
```

The optional attribute `upload-files="true"` and `expand-transformations="true"` may be present, as above.

### JSON Request Body with Inline Contents

The request body is expressed as JSON within the \<json-body> tag. Endpoint parameters are expanded.

```xml
<!-- Data source definition -->
<xml-from-url>
    ...
    <json-body>
        {"json": {
            "key": "${foo}"
                },
          ...
        }
    </json-body>
    ...
</xml-from-url>
```

Endpoint parameters are expanded within the string context of JSON, that is to say that no concern about escaping is necessary.

Options for expanding base64 content from file upload or generated content is not available for JSON.

### JSON Request Body from Transformation

The request body is generated by XSLT. That requires that the result of the transformation is valid JSON.

```xml
<!-- Data source definition -->
<xml-from-url>
    ...
    <json-body xslt-file="foo.xslt"/>
    ...
</xml-from-url>
```

Note that this is a transformation within a transformation. The XSLT takes a `<parameters>` as its input document, see above "XML Request Body from Transformation" for the format of that block.

## Root Tag

By default the root-tag of the generated output is \<xml-from-url>. Use the optional **tag attribute** to generate any different root-tag:

```xml
<!-- Data source definition -->
<data-source>
    <xml-from-url tag="my-new-root-tag">
        ...
    </xml-from-url>
<data-source>
```