<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:output indent="yes" />

    <xsl:template match="/">
        <xsl:copy-of select="/"/>
        <from-java-functions-during-xslt>
            <uuid>
                <xsl:value-of 
                        select="uuid:randomUUID()"
                        xmlns:uuid="java:java.util.UUID"/>
            </uuid>
            <random-between-0-and-1>
                <xsl:value-of 
                        select="math:random()" 
                        xmlns:math="java:java.lang.Math"/>
            </random-between-0-and-1>
            <random-lowercase-letter>
                <xsl:value-of
                        select="strings:randomLowercaseLetter()"
                        xmlns:strings="java:com.offerready.xslt.xsltfunction.Strings"/>
            </random-lowercase-letter>
            <base64-encode>
                <xsl:value-of
                        select="base64:encode('wt')"
                        xmlns:base64="java:com.offerready.xslt.xsltfunction.Base64"/>
            </base64-encode>
            <base64-decode>
                <xsl:value-of
                        select="base64:decode('d3Q=')"
                        xmlns:base64="java:com.offerready.xslt.xsltfunction.Base64"/>
            </base64-decode>
            <sha256>
                <xsl:value-of 
                        select="digest:sha256Hex('foo')" 
                        xmlns:digest="java:org.apache.commons.codec.digest.DigestUtils"/>
            </sha256>
            <reCaptcha>
                <xsl:value-of 
                        select="reCaptchaV3:check('foo', 'bar')" 
                        xmlns:reCaptchaV3="java:com.offerready.xslt.xsltfunction.ReCaptchaV3Client"/>
            </reCaptcha>
        </from-java-functions-during-xslt>
    </xsl:template>

</xsl:stylesheet>
