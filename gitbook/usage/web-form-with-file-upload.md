# Web Form With File Upload

HTML web forms allow to send files along with the form data:

```html
<input type="file" name="foo" />

<!-- It is possible to upload multiple files using the multiple attribute: -->
<input type="file" name="foo" multiple="true" />
```

OpenEndpoints automatically understands the correct `enctype` to separate uploaded content from form `<input>` data:

| File Upload                            | enctype                           |
| -------------------------------------- | --------------------------------- |
| Web form with no file upload           | application/x-www-form-urlencoded |
| Web form with one or many file uploads | multipart/form-data               |

Once the file(s) have been uploaded by the user, the following options exist to do things with these files:

## Option 1 - Email attachment

The [Email Task](../configuration/tasks/email-task.md) allows to add **all** uploaded files as attachments:

```xml
<attachments-from-request-file-uploads/>
```

That means: It is not possible to select which file should be attached as an attachment. All files are always attached.

## Option 2 - Insert file content into HTTP request body

When sending an HTTP request from OpenEndpoints using the [HttpRequest Task](../configuration/tasks/httprequest-task.md) , and when specifying that HTTP request should contain an XML body, and when that XML is specified inline, the optional attribute `upload-files="true"` may be set.

For example:

```xml
<xml-body upload-files="true">
    <foo upload-field-name="xyz" encoding="base64"/>
</xml-body>
```

This syntax indicates that the element `<foo>` will be filled with the contents of the uploaded file with the filename "xyz". The encoding is always **base64**, **no other encodings are supported**.
