package com.apigee.proxywriter;

import com.apigee.proxywriter.exception.ErrorParsingWsdlException;
import com.apigee.utils.WsdlDefinitions;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GenerateProxyTest {


    public static final String WEATHER_WSDL = "http://wsf.cdyne.com/WeatherWS/Weather.asmx?WSDL";
    public static final String oMap = "<proxywriter><get><operation><pattern>get</pattern><location>beginsWith</location></operation><operation><pattern>inq</pattern><location>beginsWith</location></operation><operation><pattern>search</pattern><location>beginsWith</location></operation><operation><pattern>list</pattern><location>beginsWith</location></operation><operation><pattern>retrieve</pattern><location>beginsWith</location></operation></get><post><operation><pattern>create</pattern><location>contains</location></operation><operation><pattern>add</pattern><location>beginsWith</location></operation><operation><pattern>process</pattern><location>beginsWith</location></operation></post><put><operation><pattern>update</pattern><location>beginsWith</location></operation><operation><pattern>change</pattern><location>beginsWith</location></operation><operation><pattern>modify</pattern><location>beginsWith</location></operation><operation><pattern>set</pattern><location>beginsWith</location></operation></put><delete><operation><pattern>delete</pattern><location>beginsWith</location></operation><operation><pattern>remove</pattern><location>beginsWith</location></operation><operation><pattern>del</pattern><location>beginsWith</location></operation></delete></proxywriter>";

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
                System.out.println(zipEntry.getName());
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
    };

    private int entryCount(InputStream inputStream) throws IOException {
        int count = 0;
        final ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            count++;
        }
        zipInputStream.close();
        return count;
    }

    @Test
    public void testGeneratePassthrough() throws Exception {
        final List<String> filenames = Arrays.asList(
                "apiproxy/policies/Extract-Operation-Name.xml",
                "apiproxy/policies/Invalid-SOAP.xml",
                "apiproxy/proxies/default.xml",
                "apiproxy/targets/default.xml",
                "apiproxy/Weather.xml");
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", true, "", "/foo", "default,secure", false, false, false, false, null));
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
    public void testGenerateRest() throws Exception {
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
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", false, false, false, false, null));
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

    @Test
    public void testVHosts() throws Exception {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", false, false, false, false, null));
        String entry = readZipFileEntry("apiproxy/proxies/default.xml", inputStream);
        Assert.assertTrue(entry.contains("<VirtualHost>default</VirtualHost"));
        Assert.assertTrue(entry.contains("<VirtualHost>secure</VirtualHost"));

        inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default", false, false, false, false, null));
        entry = readZipFileEntry("apiproxy/proxies/default.xml", inputStream);
        Assert.assertTrue(entry.contains("<VirtualHost>default</VirtualHost"));
        Assert.assertFalse(entry.contains("<VirtualHost>secure</VirtualHost"));
    }

    @Test
    public void testCors() throws Exception {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", true, false, false, false, null));
        final String entry = readZipFileEntry("apiproxy/policies/add-cors.xml", inputStream);
        Assert.assertTrue(entry.length() > 0);
    }

    @Test
    public void testSelectedOperations2() throws Exception {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", true, false, false, false, null));
        final int countWithNoSelectedJson = entryCount(inputStream);
        inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false,
                "Whatever", "/foo", "default,secure", true, false, false, false,
                "[{\"operationName\": \"GetWeatherInformation\", \"verb\": \"GET\", \"resourcePath\": \"\\\\whatever\"}]"));
        Assert.assertNotEquals("Selected operations not working as expected", countWithNoSelectedJson, entryCount(inputStream));
    }

    @Test
    public void testParseWsdl() throws ErrorParsingWsdlException {
        final WsdlDefinitions wsdlDefinitions = GenerateProxy.parseWsdl(WEATHER_WSDL);
        Assert.assertTrue(1 == wsdlDefinitions.getServices().size());
        final WsdlDefinitions.Service service = wsdlDefinitions.getServices().get(0);
        Assert.assertEquals("Weather", service.getName());
        final List<WsdlDefinitions.Port> portTypes = service.getPorts();
        Assert.assertEquals(portTypes.size(), 4);
        final WsdlDefinitions.Port weatherSoap = portTypes.get(0);
        Assert.assertEquals(weatherSoap.getName(), "WeatherSoap");
        final WsdlDefinitions.Operation getWeatherInformationOperation = weatherSoap.getOperations().get(0);
        Assert.assertEquals(getWeatherInformationOperation.getDescription(), "Gets Information for each WeatherID");
        Assert.assertEquals(getWeatherInformationOperation.getMethod(), "GET");
        Assert.assertEquals(getWeatherInformationOperation.getName(), "GetWeatherInformation");
        Assert.assertEquals(getWeatherInformationOperation.getPath(), "/weatherinformation");
        Assert.assertEquals(portTypes.get(1).getName(), "WeatherSoap12");
        Assert.assertEquals(portTypes.get(2).getName(), "WeatherHttpGet");
        Assert.assertEquals(portTypes.get(3).getName(), "WeatherHttpPost");
        Assert.assertEquals(weatherSoap.getOperations().size(), 3);
    }

    @Test
    public void testOpsMap1 () throws Exception {

        GenerateProxy genProxy = new GenerateProxy();
        genProxy.setOpsMap(oMap);
        genProxy.setPassThru(false);
        final InputStream inputStream = genProxy.begin("test case", WEATHER_WSDL);
        inputStream.reset();
    }

    @Test
    public void testOpsMap2 () throws Exception {

        GenerateProxy genProxy = new GenerateProxy();
        String jsonOMap = "{\r\n  \"proxywriter\": {\r\n    \"get\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"get\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"inq\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"search\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"list\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"retrieve\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"post\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"create\",\r\n          \"location\": \"contains\"\r\n        },\r\n        {\r\n          \"pattern\": \"add\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"process\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"put\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"update\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"change\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"modify\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"set\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"delete\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"delete\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"remove\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"del\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    }\r\n  }\r\n}";
        genProxy.setOpsMap(jsonOMap);
        genProxy.setPassThru(false);
        final InputStream inputStream = genProxy.begin("test case", WEATHER_WSDL);
        inputStream.reset();
    }

    @Test
    public void testSelectedOperations () throws Exception {
        final List<String> filenames = Arrays.asList(
                "apiproxy/policies/AddressVerify-add-namespace.xml",
                "apiproxy/policies/AddressVerify-add-other-namespaces.xml",
                "apiproxy/policies/AddressVerify-add-soapaction.xml",
                "apiproxy/policies/AddressVerify-json-to-xml.xml",
                "apiproxy/policies/AddressVerify-root-wrapper.xml",
                "apiproxy/policies/MassPay-add-namespace.xml",
                "apiproxy/policies/MassPay-add-other-namespaces.xml",
                "apiproxy/policies/MassPay-add-soapaction.xml",
                "apiproxy/policies/MassPay-json-to-xml.xml",
                "apiproxy/policies/MassPay-root-wrapper.xml",
                "apiproxy/policies/extract-format.xml",
                "apiproxy/policies/get-response-soap-body-xml.xml",
                "apiproxy/policies/get-response-soap-body.xml",
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
                "apiproxy/resources/jsc/root-wrapper.js",
                "apiproxy/resources/xsl/remove-empty-nodes.xslt",
                "apiproxy/resources/xsl/remove-namespaces.xslt",
                "apiproxy/resources/xsl/AddressVerify-add-namespace.xslt",
                "apiproxy/resources/xsl/AddressVerify-add-other-namespaces.xslt",
                "apiproxy/resources/xsl/MassPay-add-namespace.xslt",
                "apiproxy/resources/xsl/MassPay-add-other-namespaces.xslt",
                "apiproxy/targets/default.xml",
                "apiproxy/proxies/default.xml",
                "apiproxy/PayPalAPIInterfaceService.xml");
        final String PAYPAL_WSDL = "https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl";
        String jsonSelectedOp = "[{\"operationName\": \"AddressVerify\",\"verb\": \"post\",\"resourcePath\": \"/addressverify\"},{\"operationName\": \"MassPay\",\"verb\": \"put\",\"resourcePath\": \"/massivepay\"}]";
        GenerateProxy genProxy = new GenerateProxy();
        genProxy.setSelectedOperationsJson(jsonSelectedOp);
        genProxy.setOpsMap(oMap);
        genProxy.setPassThru(false);
        final InputStream inputStream = genProxy.begin("selected operations", PAYPAL_WSDL);
        checkForFilesInBundle(filenames, inputStream);
        inputStream.reset();
    }
}
