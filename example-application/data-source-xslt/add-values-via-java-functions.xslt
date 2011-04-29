<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:output indent="yes" />

    <xsl:template match="/">
        <xsl:copy-of select="/"/>
        <from-java-functions-during-xslt>
            <uuid><xsl:value-of select="uuid:randomUUID()" xmlns:uuid="java:java.util.UUID"/></uuid>
            <sha256><xsl:value-of select="digest:sha256Hex('foo')" xmlns:digest="java:org.apache.commons.codec.digest.DigestUtils"/></sha256>
        </from-java-functions-during-xslt>
    </xsl:template>

</xsl:stylesheet>

