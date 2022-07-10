# Data Source Transformation

## Basic Principle

\<OpenEndpoints/> transforms content from CMS, REST-APIs, databases and static files into other content structure and other content types. The technical solution is **XML Transformation**.

This requires

1. XML as a data-source
2. XSLT to transform XML into other content structure and content type

XSLT (Extensible Stylesheet Language Transformations) is a language for transforming XML documents into many different formats: other XML documents, or other formats such as HTML, PDF (precisely: XSL-FO), or plain text which may subsequently be converted to other formats, such as JSON. XSLT is an open, stable and established technology. Read more here: [https://en.wikipedia.org/wiki/XSLT](https://en.wikipedia.org/wiki/XSLT).

While XSLT is incredibly versatile and powerful, XML is not a really common type of data-source in the web - which we sincerely regret, because XML is just perfect to describe all sort of semantic content **and** provide established tools to manipulate that content. This is where \<openEndpoints/> comes in:

{% hint style="info" %}
#### XML transformation for non-XML data-sources

\<OpenEndpoints/> automatically converts various data-source types into XML and applies XSLT to transform original content into something new.
{% endhint %}

Required components are:

1. The data-source
2. The XSLT
3. The "transformer" - which basically combines the data-source and the XSLT in order to generate new output.

## Data-Source

In the data-sources directory under the application, there are zero or more files, each describing a data-source.

A data source is a list of commands (e.g. fetch XML from URL) which produce XML. Each data source is executed, and the results are appended into an XML document (e.g. fetch XML from two URLs, then the result of the data source will be an XML document with two child elements, which are the XML fetched from the two URLs).

The data source file contains the `<data-source>` root element then any number of data-source command in any order:

```xml
<data-source>

    ... data-source 1 ...
    ... data-source 2 ...
    ... data-source n ...

</data-source>
```

The resulting XML document has the root tag `<transformation-input>`. The results of the command are appended, in order, directly underneath this tag.

```xml
<transformation-input>
    ... result of data-source 1
    ... result of data-source 2
    ... result of data-source n
</transformation-input>
```

## Data-Source XSLT

The XML Transformation is stored in a single file ("xslt-file"). In the `data-source-xslt` directory under the application, there are zero or more XSLT files than can be used for your transformations. You can use subdirectories to organize your xslt files.

The data-type of the generated output is determined by the XSLT file.

Native XSLT can produce XML, plain text or HTML. A special markup of XML is XSL-FO, which can be converted into PDF. The option to trigger conversion of XSL-FO into PDF is described [here](transformation-options.md#generate-pdf).

## Transformer

In the transformers directory under the application, there are zero or more files, each describing a transformation. The transformation determines which XSLT to apply on which data-source.

```xml
<transformer data-source="name-of-data-source">
    <xslt-file name="path-to-xslt"/>
</transformer>
```

[Transformation Options](transformation-options.md) for post-processing the result enable flexible application for various practical use cases.
