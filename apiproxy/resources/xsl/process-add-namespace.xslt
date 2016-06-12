<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:email="http://fox.email.ebo/V1.0" xmlns:ns="http://fox.event.ebo/V1.0" xmlns:wsa="http://schemas.xmlsoap.org/ws/2003/03/addressing" version="1.0">

  <xsl:output encoding="utf-8" indent="yes" method="xml" omit-xml-declaration="yes"/>

  <!-- Stylesheet to inject namespaces into a document in specific places -->
  <xsl:template match="node()">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>

  <!-- template to copy attributes -->
  <xsl:template match="@*">
    <xsl:attribute name="{local-name()}">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>

  <!-- apply the 'event' namespace to the top node and its descendants -->
  <xsl:template match="//*">
    <xsl:element name="ns:{local-name()}">
<xsl:namespace name="ns" select="'http://fox.event.ebo/V1.0'"/>
      <xsl:copy-of select="namespace::*"/>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>
  
  <!-- template to copy the rest of the nodes -->
  <xsl:template match="comment() | processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>
