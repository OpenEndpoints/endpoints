# Using Parameter Placeholders in Data Sources

Endpoint parameters can be used as placeholders in your data source. On executing the data source definition any parameter placeholder will be replaced by the respective value of the parameter.

You can only use parameters defined in the **endpoints.xml file** of your configuration.

```xml
<endpoint-folder>
    ...
    <parameter name="foo"/>
    <parameter name="long"/>
    ...
</endpoint-folder>
```

You can not use

* intermediate parameters (from a task)
* content submitted from a file-upload (or - more generally - any additional content submitted from a multi-part message)

## Basic Syntax

If my parameter is called "foo" ...

```xml
<parameter name="foo"/>
```

then I can use as a placeholder:

```
${foo}
```

## Placeholders in the data-source definition file

Placeholders can be used inside the data-source.xml file.

For example, you can select the specific piece of content loaded from CMS by evaluating a submitted parameter value. Or you could load specific database rows selected for a specific parameter value.

For security or technological reason this does not work in every case. For details please refer to the specific sections:

* [Load Data From a Local XML File](load-data-from-a-local-xml-file.md)
* [Load Data from any REST-API](load-data-from-any-rest-api.md)
* [Load Data From Databases](load-data-from-databases.md)
* [Additional Useful Data Source Types](additional-useful-data-source-types.md)

## Inline Placeholders directly in the content source

On loading content from any of these data source types placeholders will be automatically replaced by its parameter values:

* xml-from-application
* xml-from-url

For example, you can use ${foo} as a placeholder directly in your CMS. On loading data from your CMS, actual values will replace the placeholders.

{% hint style="danger" %}
#### Potential Source of Error!

Using a placeholder for parameters not decalared in your endpoints.xml will raise an error!

For example, if you use ${firstname} in your CMS, but a parameter "firstname" is not existing in your application, this will not work.
{% endhint %}
