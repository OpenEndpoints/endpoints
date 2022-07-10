# Data Source Post-Processing

A data source is a list of zero or many commands which fetch content from different content sources and produce XML. The resulting xml output is the input ("transformation-input") for XSLT to generate output documents.

Sometimes it makes sense to apply one or many intermediate steps to modify the loaded content before it becomes a "transformation-input". Possible reasons for this include

* You might want to reuse the same XSLT to generate an output document for different content sources, but these content-sources do not produce the exactly same structure of input. An intermediate transformation step can be used to "normalize" the input among different content sources.
* A complex transformation might be implement more elegant by splitting it into several subsequent steps.

A **data-source-post-processinig.xlt** can apply xml-transformations within the data-source object.

![data source post processing](https://cdn.openendpoints.io/images/gitbook/data-source-post-processing.png)

## How does it work?

In the **data-source-post-processing-xslt** directory under the application, there are zero or more files, each describing a post-processing transformation step. Each file is a XSLT which expects input-data with a root tag \<data-source-post-processing-input> and which shall produce any output xml with a root-tag \<data-source-post-processing-output>.

Content loaded from source A:

```xml
<data-source-post-processing-input>
    <any-xml/>
</data-source-post-processing-input>
```

Expected output:

```xml
<data-source-post-processing-output>
    <any-xml/>
</data-source-post-processing-output>
```

You can add zero or many data-source-post-processing.xslt to each content-source of your data-source object. For each content-source post-processing will be executed separately. Multiple steps for the same content-source will be executed subsequently in the order of post-processing-xslt files.

In addition you can apply the same logic for the entire data-source object. In this case all content-sources are loaded as a first stept, and post-processing applies for the collection of all content-sources.

```xml
<data-source>
    <xml-from-database>
        ...
        <post-process xslt="file-a.xslt"/>
    </xml-from-database>

    <xml-from-url ignore-if-error="true">
        ...
        <post-process xslt="file-b.xslt"/>
    </xml-from-url>

    <application-introspection>
        <post-process xslt="file-c.xslt"/>
    </application-introspection>

      <!--post-processing applied on the complete set of all content-sources-->
    <post-process xslt="file-X.xslt"/>
    <post-process xslt="file-Y.xslt"/>
    <post-process xslt="file-Z.xslt"/>
</data-source>
```
