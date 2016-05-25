<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet 
  version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <xsl:output indent="yes" />
  <xsl:template match="/">
    <xsl:apply-templates select="//*[local-name() = 'Body']/*[1]" />
  </xsl:template>
  <xsl:template match="*">
    <xsl:element name="{local-name()}">
        <xsl:apply-templates select="@* | node()"/>
    </xsl:element>
  </xsl:template>
</xsl:stylesheet>