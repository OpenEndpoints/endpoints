# PDF Created With XSLT

Originally XSLT specifically was designed to support a 2-step process of

1. **Transformation:** In the first step an XML document transforms into another XML document containing information about how to display the document: what font to use, the size of a page, etc. This markup is called `Formatting Objects`. Note that the resulting XML document not only contains formatting information, but is also stores all of the document's data within itself.
2. **Formatting:** Some software (called “FO-Processor”) transforms the result of the first step (transformation) into the intended output format. For example, Apache™ FOP (Formatting Objects Processor) is an output independent formatter, which can generate PDF.

OpenEndpoints uses Apache™ FOP to generate PDF.

The data-source transformer refers to a XSLT that creates XSL-FO (=an xml containing content + formatting options).

```xml
<transformer data-source="[data-source]">
    <xslt-file name="[xslt-which-creates-xsl-fo]"/>
    <convert-output-xsl-fo-to-pdf/>
</transformer>
```

In the example above the 2-step process is:

1. `[xslt-which-creates-xsl-fo]` transforms `[data-source]` into the `xsl-fo-output` = an xml containing content + formatting options.
2. The option `<convert-output-xsl-fo-to-pdf/>` triggers the function to send `xsl-fo-output` to Apache FOP, which will generate PDF.

The Apache FOP integration with OpenEndpoints includes easy to use options to

* [Embedding Images](embedding-images.md) into the generated PDF
* Embedding Fonts such as Google Fonts into your generated PDF

## Advantages of XSL-FO

XSL-FO is a very mature standard for page composition, which was designed for paged media. It is capable of comprehensive layout functionality, which makes it possible to create error-free but also beautiful layouts. For example: Pagination controls to avoid “widows” and “orphans”, the support of multiple columns, indexing, etc. It is the perfect technology to produce “content-driven” design.

For more advantages and disadvantages of XSL-FO see: [https://en.wikipedia.org/wiki/XSL\_Formatting\_Objects](https://en.wikipedia.org/wiki/XSL\_Formatting\_Objects)
