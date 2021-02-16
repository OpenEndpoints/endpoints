<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

    <xsl:template match="/">
        <parameter-transformation-output>
            <xsl:copy-of select="/parameter-transformation-input/input-from-request/parameter[@name != 'hash' and @name != 'environment']"/>
            <parameter name="user-agent">
                <xsl:attribute name="value" select="/parameter-transformation-input/input-from-request/http-header-user-agent/text()"/>
            </parameter>
            <parameter name="base-url">
                <xsl:attribute name="value" select="/parameter-transformation-input/input-from-application/base-url/text()"/>
            </parameter>
        </parameter-transformation-output>
    </xsl:template>

</xsl:stylesheet>
