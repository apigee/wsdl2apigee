<xsl:stylesheet version="1.0"
                xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns0="http://www.apigee.com"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output indent="yes" method="xml" encoding="utf-8"
              omit-xml-declaration="yes" />

  <!-- Stylesheet to inject namespaces into a document in specific places -->
  <xsl:template match="/">
    <soapenv:Envelope>
      <soapenv:Header />
      <soapenv:Body>
        <xsl:choose>
          <!-- Handle 'Root' wrapper added by JSON to XML policy -->
          <xsl:when test="normalize-space(/Root)">
            <ns0:wrapper>
              <xsl:apply-templates select="node()|@*"/>
            </ns0:wrapper>
          </xsl:when>
          <!-- Handle 'Array' wrapper added by JSON to XML policy -->
          <xsl:when test="normalize-space(/Array)">
            <ns0:wrapper>
              <xsl:apply-templates select="node()|@*"/>
            </ns0:wrapper>
          </xsl:when>
          <!-- If the root element is not what was in the schema, add it -->
          <xsl:when test="not(normalize-space(/wrapper))">
            <ns0:wrapper>
              <xsl:apply-templates select="node()|@*"/>
            </ns0:wrapper>
          </xsl:when>
          <!-- everything checks out,  just copy the xml-->
          <xsl:otherwise>
            <xsl:apply-templates select="node()|@*"/>
          </xsl:otherwise>
        </xsl:choose>
      </soapenv:Body>
    </soapenv:Envelope>
  </xsl:template>

  <xsl:template match="/Root/*" name="copy-root">
    <xsl:element name="ns0:{local-name()}">
      <xsl:copy-of select="namespace::*"/>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="/Array/*" name="copy-array">
    <xsl:element name="ns0:{local-name()}">
      <xsl:copy-of select="namespace::*"/>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="*[not(local-name()='Root') and
                       not(local-name()='Array')]" name="copy-all">


    <xsl:element name="ns0:{local-name()}">
      <xsl:copy-of select="namespace::*"/>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:element>
  </xsl:template>

  <!-- template to copy the rest of the nodes -->
  <xsl:template match="comment() | processing-instruction()">
    <xsl:copy />
  </xsl:template>
</xsl:stylesheet>
