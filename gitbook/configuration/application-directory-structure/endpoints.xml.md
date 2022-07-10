# endpoints.xml

The **mandatory** file `endpoints.xml` under the application directory comprises of one or many endpoint folders, each of which contains of

* one or many endpoint definitions
* zero or many [Endpoint Parameter](../endpoint-parameter.md)

![endpoints main image](https://cdn.openendpoints.io/images/gitbook/endpoints-xml-main-image.png)

## Endpoint Folders

The root tag of the file is `<endpoint-folder>`. A simple `endpoints.xml` might have a single `<endpoint-folder>` only (in this case it is the root tag itself). But it is also possible to create a **hierarchy of endpoint-folders**:

Parameters may be defined at any level. If they are defined in an Endpoint they only apply to that Endpoint. If they are are defined in a folder they apply to all Endpoints under that folder.

Settings in the child folder will override the parent settings.

```xml
<endpoint-folder multiple-value-separator=";">
    <parameter name="foo"/>
    <parameter name="xyz" default-value="hello world"/>

    <endpoint name="my-action-1">
    ...
    </endpoint>

    <endpoint-folder>
        <endpoint name="my-action-2">
        ...
        </endpoint>
    <endpoint-folder>
</endpoint-folder>
```

## Parameters

Parameters require to have a `name` attribute, which is unique within the endpoint-folder. The default value is optional, but using will change the behaviour of the application. See Endpoint Parameter.

Note that a GET or POST request allows multiple parameters with the same name. For example, a web form might have 2 or more form fields with the same name attribute. In contrast to this, the name attributes of the endpoint parameter must be unique. As a consequence - if parameter names are used multiple times in GET or POST requests - several values would be transferred for the same endpoint parameter. The solution is to separate multiple parameter values with a separator. The default separator is a double pipe `||`. The attribute `multiple-value-separator` allows to define an alternative separator.

## Endpoint

The basic syntax of any endpoint is:

```xml
<endpoint name="some-name-unique-within-same-application">
    <! -- optional: parameter-transformation -->
    <parameter-transformation xslt="path-to-xslt">
        <! -- optional: zero or many data-sources -->
    </parameter-transformation>

    <! -- optional: additional authentication requirements -->
    <include-in-hash> ... </include-in-hash>

    <! -- type of action unless an error was raised  -->
    <success> ... </success>

    <! -- type of action if an error was raised  -->
    <error> ... </error>

    <! -- optional: zero or many tasks to be executed after success-action has been executed -->
    <task class="xyz">...</task>
</endpoint>
```

Details for each property can be found in the related sections of this documentation:

* Parameter Transformation allows to optionally transform submitted parameter values, for example to validate, normalize or correct submitted values or to create new parameters by calculation or by loading additional data from any data source.
* Include-In-Hash Use Cases allows for advanced authentication requirements based on parameter values.
* Section Types of Endpoints describes all action types for \<success> and \<error>.
* Tasks include actions such as sending an email, sending a request to any kind of REST-API or logging data into a database.
