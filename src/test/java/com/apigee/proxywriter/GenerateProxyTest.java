package com.apigee.proxywriter;

import com.apigee.proxywriter.exception.ErrorParsingWsdlException;
import com.apigee.utils.WsdlDefinitions;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
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
                "apiproxy/policies/return-wsdl.xml",
                "apiproxy/proxies/default.xml",
                "apiproxy/targets/default.xml",
                "apiproxy/Weather.xml");
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", true, "", "/foo", "default,secure", false, false, false, false, null));
        checkForFilesInBundle(filenames, inputStream);
        inputStream.reset();
        final String extractVariablesPolicy = readZipFileEntry("apiproxy/policies/Extract-Operation-Name.xml", inputStream);
        Assert.assertEquals(extractVariablesPolicy, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + System.getProperty("line.separator") +
                "<ExtractVariables async=\"false\" continueOnError=\"false\" enabled=\"true\" name=\"Extract-Operation-Name\">" + System.getProperty("line.separator") +
                "    <DisplayName>Extract Operation Name</DisplayName>" + System.getProperty("line.separator") +
                "    <Properties/>" + System.getProperty("line.separator") +
                "    <IgnoreUnresolvedVariables>true</IgnoreUnresolvedVariables>" + System.getProperty("line.separator") +
                "    <Source clearPayload=\"false\">request</Source>" + System.getProperty("line.separator") +
                "    <XMLPayload stopPayloadProcessing=\"false\">" + System.getProperty("line.separator") +
                "        <Variable name=\"envelope\" type=\"String\">" + System.getProperty("line.separator") +
                "            <XPath>local-name(/*)</XPath>" + System.getProperty("line.separator") +
                "        </Variable>" + System.getProperty("line.separator") +
                "        <Variable name=\"body\" type=\"String\">" + System.getProperty("line.separator") +
                "            <XPath>local-name(/*/*[local-name() = 'Body'])</XPath>" + System.getProperty("line.separator") +
                "        </Variable>" + System.getProperty("line.separator") +
                "        <Variable name=\"envelopeNamespace\" type=\"String\">" + System.getProperty("line.separator") +
                "            <XPath>namespace-uri(/*)</XPath>" + System.getProperty("line.separator") +
                "        </Variable>" + System.getProperty("line.separator") + 
                "        <Variable name=\"operation\" type=\"String\">" + System.getProperty("line.separator") +
                "            <XPath>local-name(//*[local-name() = 'Body']/*[1])</XPath>" + System.getProperty("line.separator") +
                "        </Variable>" + System.getProperty("line.separator") + 
                "    </XMLPayload>" + System.getProperty("line.separator") + 
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
                "apiproxy/policies/return-open-api.xml",
                "apiproxy/Weather.xml");
        final InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", false, false, false, false, null));
        checkForFilesInBundle(filenames, inputStream);
        inputStream.reset();
        final String extractVariablesPolicy = readZipFileEntry("apiproxy/policies/extract-format.xml", inputStream);
        Assert.assertEquals(extractVariablesPolicy, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + System.getProperty("line.separator") + 
                "<ExtractVariables async=\"false\" continueOnError=\"false\" enabled=\"true\" name=\"extract-format\">" + System.getProperty("line.separator") + 
                "    <DisplayName>Extract Format</DisplayName>" + System.getProperty("line.separator") + 
                "    <Properties/>" + System.getProperty("line.separator") + 
                "    <Header name=\"Content-Type\">" + System.getProperty("line.separator") + 
                "        <Pattern ignoreCase=\"true\">{contentformat}</Pattern>" + System.getProperty("line.separator") + 
                "    </Header>" + System.getProperty("line.separator") + 
                "    <Header name=\"Accept\">" + System.getProperty("line.separator") + 
                "        <Pattern ignoreCase=\"true\">{acceptformat}</Pattern>" + System.getProperty("line.separator") + 
                "    </Header>" + System.getProperty("line.separator") + 
                "    <Variable name=\"request.verb\">" + System.getProperty("line.separator") + 
                "        <Pattern>{verb}</Pattern>" + System.getProperty("line.separator") + 
                "    </Variable>" + System.getProperty("line.separator") + 
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
    public void testSoapActionHeaderNotPresentForHttpBinding() throws Exception {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherHttpPost", false, "Whatever", "/foo", "default,secure", true, false, false, false, null));
        final String entry = readZipFileEntry("apiproxy/policies/GetWeatherInformation-build-soap.xml", inputStream);
        Assert.assertFalse(entry.contains("<Header name=\"SOAPAction\"/>"));
    }

    @Test
    public void testSoapActionHeaderPresentForSoapBinding() throws Exception {
        InputStream inputStream = GenerateProxy.generateProxy(new GenerateProxyOptions(WEATHER_WSDL, "WeatherSoap", false, "Whatever", "/foo", "default,secure", true, false, false, false, null));
        final String entry = readZipFileEntry("apiproxy/policies/GetWeatherInformation-build-soap.xml", inputStream);
        Assert.assertTrue(entry.contains("<Header name=\"SOAPAction\">http://ws.cdyne.com/WeatherWS/GetWeatherInformation</Header>"));
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
        Assert.assertEquals(portTypes.size(), 2);
        final WsdlDefinitions.Port weatherSoap = portTypes.get(0);
        Assert.assertEquals(weatherSoap.getName(), "WeatherSoap");
        final WsdlDefinitions.Operation getWeatherInformationOperation = weatherSoap.getOperations().get(0);
        Assert.assertEquals(getWeatherInformationOperation.getDescription(), "Gets Information for each WeatherID");
        Assert.assertEquals(getWeatherInformationOperation.getMethod(), "GET");
        Assert.assertEquals(getWeatherInformationOperation.getName(), "GetWeatherInformation");
        Assert.assertEquals(getWeatherInformationOperation.getPath(), "/weatherinformation");
        Assert.assertEquals(portTypes.get(1).getName(), "WeatherSoap12");
        Assert.assertEquals(weatherSoap.getOperations().size(), 3);
    }

    @Test
    public void testParseWsdlDoesNotShowPortsWithoutSOAPBindings() throws ErrorParsingWsdlException {
        final WsdlDefinitions wsdlDefinitions = GenerateProxy.parseWsdl(WEATHER_WSDL);
        final WsdlDefinitions.Service service = wsdlDefinitions.getServices().get(0);
        final List<WsdlDefinitions.Port> ports = service.getPorts();
        Assert.assertEquals(ports.size(), 2);
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
                "apiproxy/policies/return-open-api.xml",
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
    
    @Test
    public void testPassThruElement() throws Exception {
        final String CLIENT_SERVICE_WSDL = "http://sxcqa.webservice.sxc.com/WebService/services/MemberSearchV5/wsdl/MemberSearchV5.wsdl";
        final GenerateProxy generateProxy = new GenerateProxy();
        generateProxy.setOpsMap(oMap);
        generateProxy.setPassThru(true);
        final InputStream inputStream = generateProxy.begin("Test operation element", CLIENT_SERVICE_WSDL);
        inputStream.reset();
        final String proxiesDefault = readZipFileEntry("apiproxy/proxies/default.xml", inputStream);
        Assert.assertTrue(proxiesDefault.contains("MemberSearchV5Request"));
    }
    
    @Test
    public void testHttpBinding() throws Exception {
        final String CLIENT_SERVICE_WSDL = "http://wsf.cdyne.com/WeatherWS/Weather.asmx?WSDL";
        final GenerateProxy generateProxy = new GenerateProxy();
        generateProxy.setService("Weather");
        generateProxy.setPort("WeatherHttpGet");
        generateProxy.setOpsMap(oMap);
        generateProxy.setPassThru(false);
        
        final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
        final InputStream inputStream = generateProxy.begin("Test http binding", CLIENT_SERVICE_WSDL);
        inputStream.reset();

        final String assignMessage = readZipFileEntry("apiproxy/policies/GetCityWeatherByZIP-build-soap.xml", inputStream);
        Assert.assertTrue(assignMessage.contains(SOAP11));
    }
    
    @Test 
    public void testHttpPort() throws Exception {
        final String CLIENT_SERVICE_WSDL = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
        final GenerateProxy generateProxy = new GenerateProxy();
        generateProxy.setService("BLZService");
        generateProxy.setPort("BLZServiceHttpport");
        generateProxy.setOpsMap(oMap);
        generateProxy.setPassThru(false);
        
        generateProxy.begin("Test http port binding", CLIENT_SERVICE_WSDL);

    }

    @Test
    public void testOASpec() throws Exception {
        final String CLIENT_SERVICE_WSDL = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
        final GenerateProxy generateProxy = new GenerateProxy();
        final String OAS = "{\r\n     \"basePath\": \"/blzservice\",\r\n     \"paths\": {\"/bank\": {\"get\": {\r\n          \"description\": \"Implements WSDL operation getBank\",\r\n          \"responses\": {\"200\": {\r\n               \"schema\": {\"$ref\": \"#/definitions/undefined\"},\r\n               \"description\": \"Successful response\"\r\n          }},\r\n          \"parameters\": [{\r\n               \"in\": \"query\",\r\n               \"name\": \"blz\",\r\n               \"description\": \"\",\r\n               \"type\": \"string\",\r\n               \"required\": false\r\n          }]\r\n     }}},\r\n     \"host\": \"www.thomas-bayer.com\",\r\n     \"produces\": [\"application/json\"],\r\n     \"schemes\": [\"http\"],\r\n     \"definitions\": {\"undefined\": {\"properties\": {\"message\": {\"type\": \"string\"}}}},\r\n     \"swagger\": \"2.0\",\r\n     \"info\": {\r\n          \"license\": {\"name\": \"Apache 2.0\"},\r\n          \"contact\": {\"name\": \"API Team\"},\r\n          \"description\": \"A OAS document generated from WSDL\",\r\n          \"termsOfService\": \"\",\r\n          \"title\": \"BLZService\",\r\n          \"version\": \"1.0.0\"\r\n     },\r\n     \"consumes\": [\"application/json\"]\r\n}";
    	generateProxy.setOpsMap(oMap);
    	final InputStream inputStream = generateProxy.begin("Test OAS generation", CLIENT_SERVICE_WSDL);
    }
    
    @Test
    public void testReservedVariables() throws Exception {
    	URL url = this.getClass().getResource("/reservedVariables.wsdl");
        final String CLIENT_SERVICE_WSDL = url.toString();
        final GenerateProxy generateProxy = new GenerateProxy();
    	
        generateProxy.setOpsMap(oMap);
        generateProxy.setPassThru(false);
    	
        final InputStream inputStream = generateProxy.begin("Test Reserved Variables", CLIENT_SERVICE_WSDL);
        final String extractPolicy = readZipFileEntry("apiproxy/policies/getOrganization-extract-query-param.xml", inputStream);
        Assert.assertTrue(extractPolicy.contains("name=\"org\""));
    }
    
    @Test
    public void testRecursiveWSDL() throws Exception {
    	final String CLIENT_SERVICE_WSDL = "https://webservice.s7.exacttarget.com/etframework.wsdl";
    	final GenerateProxy generateProxy = new GenerateProxy();
    
    	generateProxy.setOpsMap(oMap);
    	generateProxy.setPassThru(false);
    	
        final InputStream inputStream = generateProxy.begin("Test WSDL with recursive schema", CLIENT_SERVICE_WSDL);
    	
    }

}
