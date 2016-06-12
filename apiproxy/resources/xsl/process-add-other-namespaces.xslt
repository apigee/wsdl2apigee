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

    <xsl:template match="/ns:FoxEDFEvent//*/ns:TOList">
    <xsl:element name="email:{local-name()}">
      
      <xsl:namespace name="wsa" select="'http://schemas.xmlsoap.org/ws/2003/03/addressing'"/>
    <xsl:namespace name="email" select="'http://fox.email.ebo/V1.0'"/>

      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:TOList/ns:TO">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:CCList">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:CCList/ns:CC">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:DeliveryType">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:DeliveryType/@*">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:Subject">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:Message">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:AttachmentList">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:AttachmentList/ns:Attachment">
    <xsl:element name="email:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:Address">
    <xsl:element name="wsa:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:ReferenceProperties">
    <xsl:element name="wsa:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:PortType">
    <xsl:element name="wsa:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>


  <xsl:template match="/ns:FoxEDFEvent//*/ns:ServiceName">
    <xsl:element name="wsa:{local-name()}">
      
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>




  <!-- template to copy the rest of the nodes -->
  <xsl:template match="comment() | processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>
