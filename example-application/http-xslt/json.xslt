<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:output method="text"/>

    <xsl:template match="/">
        {"title":"<xsl:value-of select="//parameter[@name='invoice-number']/@value"/>"}
    </xsl:template>

</xsl:stylesheet>

