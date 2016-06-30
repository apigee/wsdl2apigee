package com.apigee.proxywriter;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GenerateProxyTest {


    private void checkForFilesInBundle(List<String> filenames, InputStream inputStream) throws IOException {
        final ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            Assert.assertTrue("Missing " + zipEntry.getName(), filenames.contains(zipEntry.getName()));
        }
        zipInputStream.close();
    }

    private String readZipFileEntry(String filename, InputStream inputStream) throws IOException {
        final ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (filename.equals(zipEntry.getName())) {
                    final byte[] bytes = new byte[1024];
                    int read = 0;
                    final StringBuilder sb = new StringBuilder();
                    while ( (read = zipInputStream.read(bytes, 0, 1024)) != -1) {
                        sb.append(new String(bytes, 0, read));
                    }
                    zipInputStream.closeEntry();
                    return sb.toString();
                }
            }
            return null;
        } finally {
            zipInputStream.close();
        }
    }

    @Test
    public void testGeneratePassthrough() throws IOException {
        final List<String> filenames = Arrays.asList(
                "apiproxy/policies/Extract-Operation-Name.xml",
                "apiproxy/policies/Invalid-SOAP.xml",
                "apiproxy/proxies/default.xml",
                "apiproxy/targets/default.xml",
                "apiproxy/Weather.xml");
        final InputStream inputStream = GenerateProxy.generateProxy("http://wsf.cdyne.com/WeatherWS/Weather.asmx?WSDL", true, "");
        checkForFilesInBundle(filenames, inputStream);
        inputStream.reset();
        final String extractVariablesPolicy = readZipFileEntry("apiproxy/policies/Extract-Operation-Name.xml", inputStream);
        Assert.assertEquals(extractVariablesPolicy, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<ExtractVariables async=\"false\" continueOnError=\"false\" enabled=\"true\" name=\"Extract-Operation-Name\">\n" +
                "    <DisplayName>Extract Operation Name</DisplayName>\n" +
                "    <Properties/>\n" +
                "    <IgnoreUnresolvedVariables>true</IgnoreUnresolvedVariables>\n" +
                "    <Source clearPayload=\"false\">request</Source>\n" +
                "    <XMLPayload stopPayloadProcessing=\"false\">\n" +
                "        <Variable name=\"envelope\" type=\"String\">\n" +
                "            <XPath>local-name(/*)</XPath>\n" +
                "        </Variable>\n" +
                "        <Variable name=\"body\" type=\"String\">\n" +
                "            <XPath>local-name(/*/*[local-name() = 'Body'])</XPath>\n" +
                "        </Variable>\n" +
                "        <Variable name=\"envelopeNamespace\" type=\"String\">\n" +
                "            <XPath>namespace-uri(/*)</XPath>\n" +
                "        </Variable>\n" +
                "        <Variable name=\"operation\" type=\"String\">\n" +
                "            <XPath>local-name(//*[local-name() = 'Body']/*[1])</XPath>\n" +
                "        </Variable>\n" +
                "    </XMLPayload>\n" +
                "</ExtractVariables>");
    }

    @Test
    public void testGenerateRest() throws IOException {
        final List<String> filenames = Arrays.asList(
                "apiproxy/policies/extract-format.xml",
                "apiproxy/policies/get-response-soap-body-xml.xml",
                "apiproxy/policies/get-response-soap-body.xml",
                "apiproxy/policies/GetCityForecastByZIP-build-soap.xml",
                "apiproxy/policies/GetCityForecastByZIP-extract-query-param.xml",
                "apiproxy/policies/GetCityWeatherByZIP-build-soap.xml",
                "apiproxy/policies/GetCityWeatherByZIP-extract-query-param.xml",
                "apiproxy/policies/GetWeatherInformation-build-soap.xml",
                "apiproxy/policies/GetWeatherInformation-extract-query-param.xml",
                "apiproxy/policies/remove-empty-nodes.xml",
                "apiproxy/policies/remove-namespaces.xml",
                "apiproxy/policies/return-generic-error-accept.xml",
                "apiproxy/policies/return-generic-error.xml",
                "apiproxy/policies/set-response-soap-body-accept.xml",
                "apiproxy/policies/set-response-soap-body.xml",
                "apiproxy/policies/set-target-url.xml",
                "apiproxy/policies/unknown-resource-xml.xml",
                "apiproxy/policies/unknown-resource.xml",
                "apiproxy/policies/xml-to-json.xml",
                "apiproxy/proxies/default.xml",
                "apiproxy/resources/jsc/root-wrapper.js",
                "apiproxy/resources/xsl/remove-empty-nodes.xslt",
                "apiproxy/resources/xsl/remove-namespaces.xslt",
                "apiproxy/targets/default.xml",
                "apiproxy/Weather.xml");
        final InputStream inputStream = GenerateProxy.generateProxy("http://wsf.cdyne.com/WeatherWS/Weather.asmx?WSDL", false, "");
        checkForFilesInBundle(filenames, inputStream);
        inputStream.reset();
        final String extractVariablesPolicy = readZipFileEntry("apiproxy/policies/extract-format.xml", inputStream);
        Assert.assertEquals(extractVariablesPolicy, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<ExtractVariables async=\"false\" continueOnError=\"false\" enabled=\"true\" name=\"extract-format\">\n" +
                "    <DisplayName>Extract Format</DisplayName>\n" +
                "    <Properties/>\n" +
                "    <Header name=\"Content-Type\">\n" +
                "        <Pattern ignoreCase=\"true\">{contentformat}</Pattern>\n" +
                "    </Header>\n" +
                "    <Header name=\"Accept\">\n" +
                "        <Pattern ignoreCase=\"true\">{acceptformat}</Pattern>\n" +
                "    </Header>\n" +
                "    <Variable name=\"request.verb\">\n" +
                "        <Pattern>{verb}</Pattern>\n" +
                "    </Variable>\n" +
                "</ExtractVariables>");
    }
}
