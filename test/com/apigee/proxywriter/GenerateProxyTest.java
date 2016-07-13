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
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", true, "", "/foo", "default,secure", false, false, false, false));
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
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", false, false, false, false));
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
    public void testVHosts() throws IOException {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", false, false, false, false));
        String entry = readZipFileEntry("apiproxy/proxies/default.xml", inputStream);
        Assert.assertTrue(entry.contains("<VirtualHost>default</VirtualHost"));
        Assert.assertTrue(entry.contains("<VirtualHost>secure</VirtualHost"));

        inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default", false, false, false, false));
        entry = readZipFileEntry("apiproxy/proxies/default.xml", inputStream);
        Assert.assertTrue(entry.contains("<VirtualHost>default</VirtualHost"));
        Assert.assertFalse(entry.contains("<VirtualHost>secure</VirtualHost"));
    }

    @Test
    public void testCors() throws IOException {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", true, false, false, false));
        final String entry = readZipFileEntry("apiproxy/policies/add-cors.xml", inputStream);
        Assert.assertTrue(entry.length() > 0);
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
    	String oMap = "<proxywriter><get><operation><name>get</name><location>beginsWith</location></operation><operation><name>inq</name><location>beginsWith</location></operation><operation><name>search</name><location>beginsWith</location></operation><operation><name>list</name><location>beginsWith</location></operation><operation><name>retrieve</name><location>beginsWith</location></operation></get><post><operation><name>create</name><location>contains</location></operation><operation><name>add</name><location>beginsWith</location></operation><operation><name>process</name><location>beginsWith</location></operation></post><put><operation><name>update</name><location>beginsWith</location></operation><operation><name>change</name><location>beginsWith</location></operation><operation><name>modify</name><location>beginsWith</location></operation><operation><name>set</name><location>beginsWith</location></operation></put><delete><operation><name>delete</name><location>beginsWith</location></operation><operation><name>remove</name><location>beginsWith</location></operation><operation><name>del</name><location>beginsWith</location></operation></delete></proxywriter>";
    	genProxy.setOpsMap(oMap);
    	genProxy.setPassThru(false);
    	final InputStream inputStream = genProxy.begin("test case", WEATHER_WSDL);
        inputStream.reset();
    }
    
    @Test 
    public void testOpsMap2 () throws Exception {
     	
    	GenerateProxy genProxy = new GenerateProxy();
    	String oMap = "{\r\n  \"proxywriter\": {\r\n    \"get\": {\r\n      \"operation\": [\r\n        {\r\n          \"name\": \"get\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"inq\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"search\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"list\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"retrieve\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"post\": {\r\n      \"operation\": [\r\n        {\r\n          \"name\": \"create\",\r\n          \"location\": \"contains\"\r\n        },\r\n        {\r\n          \"name\": \"add\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"process\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"put\": {\r\n      \"operation\": [\r\n        {\r\n          \"name\": \"update\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"change\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"modify\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"set\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"delete\": {\r\n      \"operation\": [\r\n        {\r\n          \"name\": \"delete\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"remove\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"name\": \"del\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    }\r\n  }\r\n}";
    	genProxy.setOpsMap(oMap);
    	genProxy.setPassThru(false);
    	final InputStream inputStream = genProxy.begin("test case", WEATHER_WSDL);
        inputStream.reset();   	
    }
    
}
