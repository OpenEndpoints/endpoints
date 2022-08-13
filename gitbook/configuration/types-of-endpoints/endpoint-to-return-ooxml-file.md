# Endpoint to return OOXML File

This feature can be used to download e.g. Word or Excel files.

Only e.g. DOCX and XLSX etc. are supported; old-style DOC and XLS files are not supported.

The body of the document may contain parameter references such as `${foo}`, these are expanded to the parameters available through the endpoint processing.

```xml
<endpoint name="foo">
    <success>
        <ooxml-parameter-expansion
                source="foo.docx"
                download-filename="bar-${id}.docx"
        />
    </success>
</endpoint>
```

The endpoint application directory may contain an `ooxml-responses` directory and within that any referenced files must be present, for example `foo.docx` in the example above.