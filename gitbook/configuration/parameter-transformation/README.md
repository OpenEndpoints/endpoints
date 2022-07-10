# Parameter Transformation

Parameter Transformation is an option for the advanced processing of inputs from a request. It allows to

1. have different names for parameters submitted and parameters declared in endpoints.xml
2. produce parameter values different from submitted values
3. validate input data and create custom error messages, implementing custom validation rules
4. process XML body as input (content type "application/xml")

To use parameter-transformation add a tag \<parameter-transformation> to the endpoint-definition in the endpoint-folder. The xslt (mandatory) is from the directory "parameter-transformation-xslt" under application. You may use subdirectories to organize XSLT in this directory.

Optionally zero or many data sources may be added. Syntax for adding data sources is the same as for [Data Source Transformation](../data-source-transformation/).

```xml
<endpoint name="foo">
  <parameter-transformation xslt="xslt-from-directory-parameter-transformation-xslt">
    <!-- optional: zero or many data sources -->
    <xml-from-application file="books.xml"/>
    </parameter-transformation>
  ...
</endpoint>
```

## Default behaviour without parameter transformation

In the absence of "parameter transformation" the default behaviour for processing data inputs is:

* Data can be sent as GET or POST parameters only. It is not possible to supply request type "application/xml" as an input.
* The name of the parameter sent must be existing as a parameter-name in endpoints.xml.
* Sending parameters not existing in endpoints.xml will cause an error.
* For every parameter existing in endpoints.xml a value must be submitted, unless a default-value exists. Otherwise this will cause an error.
* The value of each parameter equals the value of the respective parameter submitted.

## Alternative behaviour with parameter transformation

1. If the request is a GET/POST with parameters then all parameters are taken and \<parameter name="x" value="y"/> elements are created - no matter if declared in \<endpoints.xml> or not. If the request is a POST with an XML body then the XML is taken as is.
2. Any optionally specified data sources are executed. Any GET and POST parameters may be accessed with the ${x} syntax (see the data source descriptions for where parameters may be used). Any parameter which is referenced but not supplied with the request is left empty (an error is not produced) as the point of the parameter transformation is to determine errors. An error being produced by a missing parameter would then not allow the parameter transformation to produce a custom \<error> output.
3. Input from \[1] and \[2] are placed into an XML called "parameter-transformation-input.xml". See [Parameter Transformation Input](parameter-transformation-input.md) for details.
4. The XSLT (from directory parameter-transformation-xslt under application) is applied on "parameter-transformation-input.xml". The generated output is called "parameter-transformation-output.xml". The generated output requires a specific schema. See [Parameter Transformation Output](parameter-transformation-output.md) for details.
5. If the result of the transformation includes \<error>Param 'x' must be an integer\</error>, this error message is returned to the user, and no further processing is performed. The absence of an error tag is considered a success (i.e. there is no “success” tag or similar).
6. Parameter values are extracted from the result of the transformation.
7. Normal parameter processing steps are taken (default values are applied, an error if values are missing, etc.).
