# Transformation Options

## Transformer file

In the transformers directory under the application, there are zero or more files, each describing a transformation. Subdirectories are not supported.

![transformation options](https://cdn.openendpoints.io/images/gitbook/transformation-options-transformer.png)

The root element of each transformer file is `<transformer>` .

The root `<transformer>` element has with a **mandatory** attribute `data-source`. The is the name of the data-source without file-extension. For example, if you have a file `my-data-source.xml` in the data-sources directory, then the correct attribute value is `data-source="my-data-source"`.

The `<xslt-file>` element is optional. If omitted, the data-source will be returned without XSLT transformation. The `name` attribute in XSLT file element is mandatory. It is the file-name of an XSLT file including the file-extension. Possible file extensions are ".xslt" or ".xsl".

The optional `content-type` element sets the mime-type of the generated output. The `type` attribute is mandatory. The value of this attribute is the mime-type that shall be set. If no content-type were set, heuristics are used by Endpoints to guess an appropriate content type.

{% hint style="warning" %}
#### Potential Source of Error!

Using a placeholder for parameters not decalared in your endpoints.xml will raise an error!

For example, if you use ${firstname} in your CMS, but a parameter "firstname" is not existing in your application, this will not work.
{% endhint %}

{% hint style="info" %}
#### Generating a REST API Request-Body

REST APIs often require a specific MIME type for the request body. Use the `content-type` element to set the required value.
{% endhint %}

## Possible applications

### Omit transformation

```xml
<transformer data-source="[name-of-data-source]"/>
```

The data-source will be wrapped into a root-tag `<transformation-input>`.

Note that this is useful for developing and debugging a data-source transformation. Omitting the xslt-file element returns exactly the input, which your xslt will be applied to.

### Generate XML

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-xml]"/>
    <content-type type="text/xml"/>
</transformer>
```

### Generate HTML

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-html]"/>
    <content-type type="text/html"/>
</transformer>
```

### Generate PDF

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xsl-fo-file]"/>
    <convert-output-xsl-fo-to-pdf/>
</transformer>
```

The correct content-type is set automatically. It is possible to deliberately set a different content-type, but we do not recommend to do so.

### Generate JSON

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-xml]"/>
    <convert-output-xml-to-json/>
</transformer>
```

The correct content-type is set automatically. It is possible to deliberately set a different content-type, but we do not recommend to do so.

Note that in the example above the XSLT produces XML, which is then converted to JSON. An alternative option to generate JSON is to have XSLT with output type "text". In that case the correct syntax is different:

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-text]"/>
    <content-type type="application/json"/>
</transformer>
```

{% hint style="info" %}
#### JSON Syntax

Conversion of XML to JSON can be done in different ways. If you need a specific JSON syntax, XSLT generating JSON might be the better option compared to generating XML with option .
{% endhint %}

### Generate Plain Text

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-text]"/>
</transformer>
```

{% hint style="info" %}
#### UTF-8

XSLT output by default is UTF-8. Use `<content-type type="text/plain; charset=xxx"/>` to set a specific charset if required. In such case the generated output needs to match that specific charset, of course.
{% endhint %}

### Generate Excel Sheet

XSLT can **not** generate output of type Excel. \<OpenEndpoints/> offers a workaround which converts a simple HTML table into Excel binary format.

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-html]"/>
    <convert-output-xml-to-excel/>
</transformer>
```

The format is chosen to be as similar to XHTML as possible. The syntax is as follows:

* HTML should contain `<table>` elements.
* These should contain `<tr>` elements and within them `<td>` (or `<th>`) elements.
* Excel files differentiate between "text cells" and "number cells". The contents of the `<td>` are inspected to see if they look like a number, in which case an Excel "number cell" is produced, otherwise an Excel "text cell" is produced.
* The attribute `<convert-output-xml-to-excel input-decimal-separator="xxx">` affects how numbers in the input HTML document are parsed.
  * "dot" (default). Decimal separator is ".", thousand separator is ",".
  * "comma". Decimal separator is ",", thousand separator is ".".
  * "magic". Numbers may use either dot or comma as thousand or decimal separator, or the Swiss format 1'234.45. Heuristics are used to determine which system is in use. (This is useful in very broken input documents that use dot for some numbers and comma for others, within the same document.) The numbers must either have zero decimal (e.g. "1,024") or two decimal places (e.g. "12,34"). Any other number of decimal places in the input data will lead to wrong results.
* The number of decimal places in the `<td>` data are taken over the to Excel cell formatting. That is to say, `<td>12.20</td>` will produce an Excel number cell containing the value 12.2 with the Excel number format showing two decimal places, so will appear as 12.20 in the Excel file.
* To force the cell to be an Excel text cell, even if the above algorithm would normally classify it as an Excel number cell, make the table cell with `<td excel-type="text">`.
* The `colspan` attribute, e.g. `<td colspan="2">`, is respected.
* The following style elements of `<td>` are respected:
  * `style="text-align: center"` (Right align etc. is not supported)
  * `style="font-weight: bold"`
  * `style="border-top:"` (Bottom borders etc. are not supported)
  * `style="color: green"`, `style="color: red"`, `style="color: orange"` (Other colors are not supported.)
* `<thead>`, `<tfoot>` and `<tbody>` are respected. (Elements in `<tfoot>` sections will appear at the bottom of the Excel file, no matter what order the tags come in in the HTML.)
* Column widths are determined by the lengths of text within each column.
* Any `<table>` which appears inside a `<td>` is ignored (i.e. tables may be nested in the HTML, only the outermost table is present in the resulting Excel file.)
* The contents of any `<script>` elements are ignored
* The contents of any other tags such as `<span>` and `<div>` are included.
* Table rows which contain only table cells which contain no text are ignored. (Often such rows contain sub-tables, which themselves are ignored. Having empty rows doesn't look nice.)

### Generate RTF

RTF can be generated with XSLT using output type text. Set the correct content-type to open a downloaded (generated) RTF with WORD.

```xml
<transformer data-source="[name-of-data-source]">
    <xslt-file name="[path-to-xslt-with-output-type-text]"/>
    <content-type type="application/doc"/>
</transformer>
```

## XSLT Parameter

XSLT parameters can be useful to re-use the same XSLT for different transformations.

```xml
<xsl:stylesheet>
    <xsl:param name="foo"/>
    ...
</xsl:stylesheet>
```

The parameter value can be set in the transformer file:

```xml
<transformer data-source="...">
    <xslt-file name=".."/>
    ...
    <!-- zero or many placeholder-values -->
    <placeholder-value placeholder-name="foo" value="some value"/>
</transformer>
```

Note that variables ${foo} are not supported with this feature.
