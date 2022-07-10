# OpenEndpoints XSLT Specifics

## Saxon XSLT Processor

By default the Saxon-HE product from [Saxonica](https://www.saxonica.com/) is used, which is open-source and requires no license fees.

Basically, the software also supports the use of commercial versions of Saxon - see [(Link Removed)](broken-reference). When Saxon-PE is available, the following additional functions for XSLT will be available in OpenEndpoints:

* `<xsl:value-of select="uuid:randomUUID()" xmlns:uuid="java:java.util.UUID"/>`
* `<xsl:value-of select="math:random()" xmlns:math="java:java.lang.Math"/>` - Generate random number between 0 and 1, e.g. 0.37575608763635215.
* `<xsl:value-of select="base64:encode('foo')" xmlns:base64="java:com.offerready.xslt.xsltfunction.Base64"/>`
* `<xsl:value-of select="base64:decode('Zm9v')" xmlns:base64="java:com.offerready.xslt.xsltfunction.Base64"/>` - assumption is that the encoded text is UTF-8 text.
* `<xsl:value-of select="digest:sha256Hex('foo')" xmlns:digest="java:org.apache.commons.codec.digest.DigestUtils"/>`
* `<xsl:value-of select="reCaptchaV3:check('server side key', 'token-from-request')" xmlns:reCaptchaV3="java:com.offerready.xslt.xsltfunction.ReCaptchaV3Client"/>` yields a number from 0.0 to 1.0, or -1.0 in the case a communication error has occurred (see log for more details of the error)

## XSLT Processor Unaware Of XSD

In most cases, the XML that you are transforming will have a schema (xsd) that can be used when developing the XSLT. The XML standard also provides for reference to the xsd directly from the XML.

Our implementation of the XSLT processor **does not expect any reference to an XSD**, and ignores this reference if it were present.

{% hint style="info" %}
#### Do not rely on data-type definition from your xsd

For the development of the XSLT, we recommend always specifying data types in a dedicated manner ("cast as").
{% endhint %}

## Global XSLT Parameters

The global section of XSLT can contain parameters which are intended for the transfer of values for controlling the XSLT.

```
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
    ...
    <xsl:param name="foo"/>
    ...
</xsl:stylesheet>
```

The root element of a `transformer` (see: [Data Source Transformation](data-source-transformation/)) can have elements like `<placeholder-value placeholder-name="x" value="y"/>` which will be passed to the XSLT processing as `<xsl:param>`.

```xml
<transformer data-source="name-of-data-source">
    <xslt-file name="path-to-xslt"/>

    <placeholder-value placeholder-name="foo" value="xyz"/>
</transformer>
```

Note that it is not supported to extract an [Endpoint Parameter](endpoint-parameter.md) in the value attribute - `${foo}` etc. will not work.

## Data-Source Transformation Input XML

In the process of developing the data-source-transformation XSLT, it might be useful to get the transformation input xml the XSLT will be applied on.

In order to get the transformation input xml, simply omit the xslt (and all other options) from the transformer to get the raw transformation-input-xml.

```xml
<transformer data-source="xyz">
    <!-- omit xslt -->
    <!-- omit transformation options -->
</transformer>
```
