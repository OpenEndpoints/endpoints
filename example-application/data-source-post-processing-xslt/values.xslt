<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:output indent="yes" />

    <xsl:template match="/data-source-post-processing-input">
        <data-source-post-processing-output>
            <post-process>
                <xsl:copy-of select="./*"/>
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
                    <url-encode>
                        <xsl:value-of
                                select="url:encode('foo bar', 'UTF-8')"
                                xmlns:url="java:java.net.URLEncoder"/>
                    </url-encode>
                    <url-decode>
                        <xsl:value-of
                                select="url:decode('foo+bar', 'UTF-8')"
                                xmlns:url="java:java.net.URLDecoder"/>
                    </url-decode>
                </from-java-functions-during-xslt>
            </post-process>
        </data-source-post-processing-output>
    </xsl:template>

</xsl:stylesheet>
