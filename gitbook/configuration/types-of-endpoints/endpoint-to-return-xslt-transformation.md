# Endpoint to Return XSLT Transformation

This type of syntax specifies that the transformation named in the attribute is performed, and its contents are returned to the client in the HTTP response. For example, the transformation might produce HTML to be displayed in the browser, or a PDF to be downloaded.

## Syntax

```xml
<endpoint name="foo">
    <success>
        <response-transformation name="a-transformer"/>
    </success>
</endpoint>
```

You can optionally add an **attribute "download-filename"**.

If the `download-filename="foo.pdf"` attribute is present, then the header in the HTTP response is set indicating that the file should be downloaded as opposed to displayed in the browser window. Parameter values like `${foo}` are replaced.

```xml
<endpoint name="foo">
    <success>
        <response-transformation name="a-transformer" download-filename="my-document.pdf"/>
    </success>
</endpoint>
```

{% hint style="danger" %}
#### Potential Source of Error

The `download-filename` attribute will raise an error if it contains a blank or a special character. This is because browsers are inconsistent in how they handle download filenames with special characters. In order to create a "write once, works everywhere" experience special characters are not supported.
{% endhint %}
