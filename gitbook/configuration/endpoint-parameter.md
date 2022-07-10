# Endpoint Parameter

A request contains any number of GET or POST parameters.

The values supplied by the user for these parameters are used in various places. For example, if you are fetching XML from a URL, parts of the whole of the URL may be the value of a parameter. The syntax for this is `${param}`. The braces are, in contrast to many other systems, mandatory.

Note that for security or technical reasons replacement is not always possible. See the specific sections for the various configuration elements where parameter replacement occurs.

{% hint style="info" %}
#### Case Sensitivity

Parameter names are case-sensitive. That means, the "firstname" is not the same as "Firstname". Case sensitivity is default behaviour in XML.
{% endhint %}

## Parameters must be defined in the endpoints.xml file

Which parameters the user may supply must be defined in the endpoints.xml file; any parameter sent to the server which is not defined there is an error.

The simplest form of a parameter definition in endpoints.xml is `<parameter name="foo">`. This means the user must supply a value that parameter as a GET or POST parameter like `?foo=value` in the request; not doing so is an error. (A request parameter transformation allows to submit parameters not defined in the request. For details please refer to section [Parameter Transformation](parameter-transformation/).)

```xml
<endpoint-folder>
    <parameter name="foo"/>
    ...
</endpoint-folder>
```

Adding a **default value** makes the parameter optional. If the user doesn't supply anything, the default value will be used.

```xml
<endpoint-folder>
    <parameter name="foo" default-value="some-value"/>
    ...
</endpoint-folder>
```

Parameters are defined, in the simplest case, directly under the root `<endpoint-folder>` node of endpoints.xml. Parameters under the root `<endpoint-folder>` node are available for all endpoints defined in the file.

{% hint style="info" %}
#### An endpoint-folder can contain zero or many other endpoint-folders

It is possible to use "cascading" endpoint-folders. In this case, endpoint-folders and endpoints are arranged in a hierarchy so that certain aspects may be defined once and inherited to all children. The structure is thus a mandatory root \<endpoint-folder> which may contain any number children \<endpoint-folder> and \<endpoint> elements.
{% endhint %}

## Multiple parameters supplied with the same name

The HTTP standard allows multiple GET parameters to be supplied with the same name, like

```url
...?param=foo&param=bar
```

In this case, all those values are concatenated into the same parameter. The default separator is two pipe characters `||`.

In the example above, the parameter value would be `foo||bar`.

The default separator be overridden with the **multiple-value-separator attribute**:

```xml
<endpoint-folder multiple-value-separator=",">
    ...
    <endpoint name="foo" multiple-value-separator=";">
        ...
    </endpoint>
</endpoint-folder>
```

Note that this property will be inherited in the hierarchy of endpoint-folders. That means it can be overwritten by subordinate folders or endpoints.

## System Parameters

There are certain parameters which are always available, and are not provided by the client. These can also be used with the same ${param} syntax.

* `${request-id}` - this system parameter returns the globally unique id of the request, assigned by OpenEndpoints during the request.
