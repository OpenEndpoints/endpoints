# Endpoint to Return Static File

This type of syntax specifies that the response content is fetched from the named file within the static directory. Subdirectories such as `filename="sub-dir/file.pdf"` are supported.

Variables are **not** allowed in the filename attribute. This is to prevent accidentally making more files accessible (if the client guesses the right filename) than intended. But you can use [Conditional Success Action](conditional-success-action.md) to deliver different files depending on different parameter values.

```xml
<endpoint name="foo">
    <success>
        <response-from-static filename="path-to-file"/>
    </success>
</endpoint>
```

You can optionally add an **attribute "download-filename"**.

If the `download-filename="foo.pdf"` attribute is present, then the header in the HTTP response is set indicating that the file should be downloaded as opposed to displayed in the browser window. Parameter Values like `${foo}` are replaced.

```xml
<endpoint name="foo">
    <success>
        <response-from-static filename="path-to-file" download-filename="my-document.pdf"/>
    </success>
</endpoint>
```

{% hint style="danger" %}
#### Potential Source of Error

Make sure that the filename does not contain empty characters, because this will raise an error.
{% endhint %}
