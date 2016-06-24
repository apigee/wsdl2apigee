<xsl:stylesheet version="1.0" 
                xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output indent="yes" method="xml" encoding="utf-8" omit-xml-declaration="yes"/>

  <!-- Stylesheet to inject namespaces into a document in specific places -->
 <xsl:template match="/">
    <soapenv:Envelope>
        <soapenv:Header/>
        <soapenv:Body>
          <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
          </xsl:copy>
        </soapenv:Body>
    </soapenv:Envelope>
 </xsl:template>

  <!-- template to copy attributes -->
  <xsl:template match="@*">
    <xsl:attribute name="{local-name()}">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>

  <!-- apply the 'event' namespace to the top node and its descendants -->
  <xsl:template match="//*">
    <xsl:element name="ns0:{local-name()}">
      <xsl:copy-of select="namespace::*"/>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>
  
  <!-- template to copy the rest of the nodes -->
  <xsl:template match="comment() | processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>