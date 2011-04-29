<?xml version="1.0"?>

<xsl:stylesheet xmlns="https://www.w3.org/1999/xhtml/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:template match="/">

    <p>
    Param in hash: <input type="text" name="param-in-hash"/>
    </p>
        <p>
            email-address: <input type="text" name="email-address"/>
        </p>
        <p>
            upload: <input type="file" name="uploads" multiple="true"/>
        </p>
    </xsl:template>
</xsl:stylesheet>
