<xsl:stylesheet version="1.0" 
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
<xsl:output method="xml" version="1.0" encoding="utf-8" indent="yes"/>
<xsl:strip-space elements="*"/>

<xsl:template match="/">
    <soapenv:Envelope>
        <soapenv:Header/>
        <soapenv:Body>
                <xsl:copy-of select="/"/>
        </soapenv:Body>
    </soapenv:Envelope>
</xsl:template>
</xsl:stylesheet>
