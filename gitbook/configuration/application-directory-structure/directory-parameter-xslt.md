# Directory: parameter-xslt

This **optional** directory contains custom XSLT for [Parameter Transformation](../parameter-transformation/).

## Parameter Transformation Input

If an endpoint uses the parameter transformation, OpenEndpoints automatically generates the temporary XML `parameter-transformation-input.xml`, which is the input for the transformation. This is (unless debug is enabled) only stored in memory, and never written to disk. Depending on how data are submitted to the endpoint, the input XML might look slightly different.

* In case input was submitted as GET request or POST request containing parameters, the generated `parameter-transformation-input.xml` will include submitted parameters.
* In case data are submitted as a POST Request with application type `application/xml`, the `input-from-request` tag contains the XML request body instead of parameters.
* In case data are submitted as a POST Request with application type `application/json`, the `input-from-request` tag contains the json request body **converted to xml**:

![directory parameter](https://cdn.openendpoints.io/images/gitbook/directory-parameter-xslt.png)

Parameter Transformation Output

The expected output (generated by your custom xslt) requires values for any parameter not having a default value. Not doing so will raise an error. In addition the output might have an `error` tag. If present, an error will be raised an the text within the error tag will be available with the system parameter `${parameter-transformation-error-text}`.

![directory parameter output](https://cdn.openendpoints.io/images/gitbook/directory-parameter-output.png)