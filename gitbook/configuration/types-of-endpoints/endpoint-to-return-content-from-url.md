# Endpoint to Return Content From Url

This type of syntax specifies that an HTTP request is performed and the result of this request is streamed back to the client as the response of the call to Endpoints.

## Syntax

```xml
<endpoint name="foo">
    <success>
        <response-from-url>
            <!-- mandatory parameter: url -->
            <url>path-to-content</url>
        </response-from-url>
    </success>
</endpoint>
```

You can optionally add an **attribute "download-filename"**.

If the `download-filename="foo"` attribute is present, then the header in the HTTP response is set indicating that the file should be downloaded as opposed to displayed in the browser window. Parameter Values like `${foo}` are replaced.

```xml
<endpoint name="foo">
    <success>
        <response-from-url download-filename="my-document.doc">
            <!-- mandatory parameter: url -->
            <url>path-to-content</url>
        </response-from-url>
    </success>
</endpoint>
```

{% hint style="danger" %}
#### Potential Source of Error

Make sure that the filename does not contain empty characters, because this will raise an error.
{% endhint %}
