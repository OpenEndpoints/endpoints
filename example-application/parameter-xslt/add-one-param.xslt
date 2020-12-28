<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:template match="/">
        <parameter-transformation-output>
            <xsl:copy-of select="/parameter-transformation-input/input-from-request/parameter[@name != 'hash' and @name != 'environment']"/>
            <parameter name="written-to-by-parameter-transformation-output" value="from-xslt"/>
            <parameter name="value-of-param-via-xml-from-url-identity">
                <xsl:attribute name="value">
                    <xsl:value-of select="/parameter-transformation-input/from-xml-from-url/response/args/param/text()"/>
                </xsl:attribute>
            </parameter>
            <parameter name="value-of-unknown-param-via-xml-from-url-identity">
                <xsl:attribute name="value">
                    <xsl:value-of select="/parameter-transformation-input/from-xml-from-url/response/args/unknown-param/text()"/>
                </xsl:attribute>
            </parameter>
            <!--<error>Test param error</error>-->
        </parameter-transformation-output>
    </xsl:template>

</xsl:stylesheet>
