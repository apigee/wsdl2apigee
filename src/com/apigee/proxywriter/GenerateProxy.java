package com.apigee.proxywriter;
/**
 * 
 * The GenerateProxy program generates a Apigee API Proxy from a WSDL Document. The generated proxy can be
 * passthru or converted to an API (REST/JSON over HTTP).
 * 
 * How does it work?
 * At a high level, here is the logic implemented for SOAP-to-API:
 * Step 1: Parse the WSDL
 * Step 2: Build a HashMap with
 * 	Step 2a: Generate SOAP Request template for each operation
 * 	Step 2b: Convert SOAP Request to JSON Request template without the SOAP Envelope
 * Step 3: Create the API Proxy folder structure
 * Step 4: Copy policies from the standard template
 * Step 5: Create the Extract Variables and Assign Message Policies
 * 	Step 5a: If the operation is interpreted as a POST (create), then obtain JSON Paths from JSON request template 
 * 	Step 5b: Use JSONPaths in the Extract Variables
 * 
 * At a high level, here is the logic implemented for SOAP-passthru:
 * Step 1: Parse the WSDL
 * Step 2: Copy policies from the standard template
 *  
 * 
 * @author  Nandan Sridhar
 * @version 0.1
 * @since   2016-05-20 
*/

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.apigee.proxywriter.exception.BindingNotFoundException;
import com.apigee.proxywriter.exception.NoServicesFoundException;
import com.apigee.proxywriter.exception.TargetFolderException;

import com.apigee.utils.JSONPathGenerator;
import com.apigee.utils.KeyValue;
import com.apigee.utils.OpsMap;
import com.apigee.utils.Options;
import com.apigee.utils.Options.Multiplicity;
import com.apigee.utils.Options.Separator;
import com.apigee.utils.StringUtils;
import com.apigee.utils.XMLUtils;

import com.predic8.wsdl.Binding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Operation;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.PortType;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wstool.creator.RequestTemplateCreator;
import com.predic8.wstool.creator.SOARequestCreator;

import groovy.xml.MarkupBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;

public class GenerateProxy {

	private static final Logger LOGGER = Logger.getLogger(GenerateProxy.class.getName());
	private static final ConsoleHandler handler = new ConsoleHandler();

	private static final String OPSMAPPING_TEMPLATE = "." + File.separator + "opsmapping.xml";

	private static final String SOAP2API_APIPROXY_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "apiProxyTemplate.xml";
	private static final String SOAP2API_PROXY_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "proxyDefault.xml";
	private static final String SOAP2API_TARGET_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "targetDefault.xml";
	private static final String SOAP2API_EXTRACT_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "ExtractPolicy.xml";
	private static final String SOAP2API_ASSIGN_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "AssignMessagePolicy.xml";

	private static final String SOAPPASSTHRU_APIPROXY_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soappassthru" + File.separator + "apiProxyTemplate.xml";
	private static final String SOAPPASSTHRU_PROXY_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soappassthru" + File.separator + "proxyDefault.xml";
	private static final String SOAPPASSTHRU_TARGET_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soappassthru" + File.separator + "targetDefault.xml";

	private static final String CONTENT_TYPE = "text/xml; charset=utf-8";// "text&#x2F;xml;
																			// charset=utf-8";
	private static final String PAYLOAD_TYPE = "text/xml";// "text&#x2F;xml";
	private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
	private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

	// set this to true if SOAP passthru is needed
	private boolean PASSTHRU;

	private String targetEndpoint;

	// default target folder is ./build
	private String targetFolder;

	private Definitions wsdl;

	// Each row in this Map has the key as the operation name. The operation
	// name has SOAP Request
	// and JSON Equivalent of SOAP (without the SOAP Envelope) as values.
	private Map<String, KeyValue<String, String>> messageTemplates;

	// initialize the logger
	static {
		LOGGER.setUseParentHandlers(false);

		Handler[] handlers = LOGGER.getHandlers();
		for (Handler handler : handlers) {
			if (handler.getClass() == ConsoleHandler.class)
				LOGGER.removeHandler(handler);
		}
		handler.setFormatter(new SimpleFormatter());
		LOGGER.addHandler(handler);
	}

	// initialize hashmap
	public GenerateProxy() {
		messageTemplates = new HashMap<String, KeyValue<String, String>>();
		targetFolder = "." + File.separator + "build";
	}

	public void setTargetFolder(String folder) {
		targetFolder = folder;
	}

	public void setPassThru(boolean pass) {
		PASSTHRU = pass;
	}

	private void writeAPIProxy(String proxyName, String proxyDescription) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();
		Document apiTemplateDocument;

		if (PASSTHRU) {
			apiTemplateDocument = xmlUtils.readXML(SOAPPASSTHRU_APIPROXY_TEMPLATE);
		} else {
			apiTemplateDocument = xmlUtils.readXML(SOAP2API_APIPROXY_TEMPLATE);
		}
		LOGGER.finest("Read API Proxy template file");

		Node rootElement = apiTemplateDocument.getFirstChild();
		NamedNodeMap attr = rootElement.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(proxyName);
		LOGGER.fine("Set proxy name: " + proxyName);

		Node displayName = apiTemplateDocument.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(proxyName);
		LOGGER.fine("Set proxy display name: " + proxyName);

		Node description = apiTemplateDocument.getElementsByTagName("Description").item(0);
		description.setTextContent(proxyDescription);
		LOGGER.fine("Set proxy description: " + proxyDescription);

		Node createdAt = apiTemplateDocument.getElementsByTagName("CreatedAt").item(0);
		createdAt.setTextContent(Long.toString(java.lang.System.currentTimeMillis()));

		Node LastModifiedAt = apiTemplateDocument.getElementsByTagName("LastModifiedAt").item(0);
		LastModifiedAt.setTextContent(Long.toString(java.lang.System.currentTimeMillis()));

		xmlUtils.writeXML(apiTemplateDocument,
				targetFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");
		LOGGER.fine(
				"Generated file: " + targetFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAP2APIProxyEndpoint(String proxyName, String basePath) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();

		Document proxyDefault = xmlUtils.readXML(SOAP2API_PROXY_TEMPLATE);
		Node basePathNode = proxyDefault.getElementsByTagName("BasePath").item(0);

		if (basePath != null && basePath.equalsIgnoreCase("") != true) {
			basePathNode.setTextContent(basePath);
		}

		Document apiTemplateDocument = xmlUtils
				.readXML(targetFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");

		Document extractTemplate = xmlUtils.readXML(SOAP2API_EXTRACT_TEMPLATE);

		Document assignTemplate = xmlUtils.readXML(SOAP2API_ASSIGN_TEMPLATE);

		Node policies = apiTemplateDocument.getElementsByTagName("Policies").item(0);

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1, step2;
		Node name1, name2;

		for (PortType pt : wsdl.getPortTypes()) {
			for (Operation op : pt.getOperations()) {
				String operationName = op.getName();
				String extractPolicyName = "";
				String buildSOAPPolicy = op.getName() + "-build-soap";
				String httpVerb = OpsMap.getOpsMap(operationName);
				String resourcePath = OpsMap.getResourcePath(operationName);

				if (httpVerb.equalsIgnoreCase("get")) {
					extractPolicyName = op.getName() + "-extract-query-param";
				} else {
					extractPolicyName = op.getName() + "-extract-json-payload";
				}

				flow = proxyDefault.createElement("Flow");
				((Element) flow).setAttribute("name", op.getName());

				flowDescription = proxyDefault.createElement("Description");
				flowDescription.setTextContent(op.getName());
				flow.appendChild(flowDescription);

				request = proxyDefault.createElement("Request");
				response = proxyDefault.createElement("Response");
				condition = proxyDefault.createElement("Condition");

				step1 = proxyDefault.createElement("Step");
				name1 = proxyDefault.createElement("Name");
				name1.setTextContent(extractPolicyName);
				step1.appendChild(name1);
				request.appendChild(step1);

				step2 = proxyDefault.createElement("Step");
				name2 = proxyDefault.createElement("Name");
				name2.setTextContent(buildSOAPPolicy);
				step2.appendChild(name2);
				request.appendChild(step2);

				String Condition = "(proxy.pathsuffix MatchesPath \"" + resourcePath + "\") and (request.verb = \""
						+ httpVerb + "\")";
				LOGGER.fine("Assign Message: " + buildSOAPPolicy);
				LOGGER.fine("Extract Variable: " + extractPolicyName);
				LOGGER.fine("Condition: " + condition);
				condition.setTextContent(Condition);

				flow.appendChild(request);
				flow.appendChild(response);
				flow.appendChild(condition);

				flows.appendChild(flow);
				// Add policy to proxy.xml
				Node policy1 = apiTemplateDocument.createElement("Policy");
				policy1.setTextContent(extractPolicyName);
				Node policy2 = apiTemplateDocument.createElement("Policy");
				policy2.setTextContent(buildSOAPPolicy);
				policies.appendChild(policy1);
				policies.appendChild(policy2);
				// write Assign Message Policy
				writeSOAP2APIAssignMessagePolicies(assignTemplate, operationName, buildSOAPPolicy);
				// write Extract Variable Policy
				writeSOAP2APIExtractPolicy(extractTemplate, operationName, extractPolicyName, httpVerb);
			}
		}

		// Add unknown resource
		flow = proxyDefault.createElement("Flow");
		((Element) flow).setAttribute("name", "unknown-resource");

		flowDescription = proxyDefault.createElement("Description");
		flowDescription.setTextContent("Unknown Resource");
		flow.appendChild(flowDescription);

		request = proxyDefault.createElement("Request");
		response = proxyDefault.createElement("Response");
		condition = proxyDefault.createElement("Condition");

		step1 = proxyDefault.createElement("Step");
		name1 = proxyDefault.createElement("Name");
		name1.setTextContent("unknown-resource");
		step1.appendChild(name1);
		request.appendChild(step1);

		flow.appendChild(request);
		flow.appendChild(response);
		flow.appendChild(condition);

		flows.appendChild(flow);

		LOGGER.fine("Edited proxy xml: " + targetFolder + File.separator + "apiproxy" + File.separator + proxyName
				+ ".xml");
		xmlUtils.writeXML(apiTemplateDocument,
				targetFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");

		xmlUtils.writeXML(proxyDefault, targetFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + targetFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAP2APIExtractPolicy(Document extractTemplate, String operationName, String policyName,
			String httpVerb) throws Exception {
		XMLUtils xmlUtils = new XMLUtils();
		KeyValue<String, String> keyValue;
		Element queryParam;
		Element pattern;
		Element variable;
		Element JSONPath;

		Document extractPolicyXML = xmlUtils.cloneDocument(extractTemplate);

		Node rootElement = extractPolicyXML.getFirstChild();
		NamedNodeMap attr = rootElement.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(policyName);

		Node displayName = extractPolicyXML.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(operationName + " Extract Query Param");

		keyValue = messageTemplates.get(operationName);

		if (httpVerb.equalsIgnoreCase("GET")) {

			List<String> elementList = xmlUtils.getElementList(keyValue.getKey());
			for (String elementName : elementList) {
				queryParam = extractPolicyXML.createElement("QueryParam");
				queryParam.setAttribute("name", elementName);

				pattern = extractPolicyXML.createElement("Pattern");
				pattern.setAttribute("ignoreCase", "true");
				pattern.setTextContent("{" + elementName + "}");

				queryParam.appendChild(pattern);
				rootElement.appendChild(queryParam);
			}
		} else if (httpVerb.equalsIgnoreCase("POST") || httpVerb.equalsIgnoreCase("PUT")
				|| httpVerb.equalsIgnoreCase("DELETE")) {
			String jsonBody = keyValue.getValue();
			JSONPathGenerator jsonPathGen = new JSONPathGenerator();
			JSONObject json = new JSONObject(jsonBody);
			Map<String, String> out = new HashMap<String, String>();

			try {
				jsonPathGen.parse(json, out);
				Set<String> jsonPaths = jsonPathGen.getJsonPath();

				Node jsonPayload = extractPolicyXML.createElement("JSONPayload");

				for (String jsonPath : jsonPaths) {
					variable = extractPolicyXML.createElement("Variable");
					variable.setAttribute("name", StringUtils.lastWordAfterDot(jsonPath));
					variable.setAttribute("type", "string");

					JSONPath = extractPolicyXML.createElement("JSONPath");
					JSONPath.setTextContent(jsonPath);

					variable.appendChild(JSONPath);
					jsonPayload.appendChild(variable);
				}

				rootElement.appendChild(jsonPayload);
			} catch (Exception e) {
				LOGGER.severe(e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}

		xmlUtils.writeXML(extractPolicyXML, targetFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator + policyName + ".xml");

	}

	private void writeSOAP2APIAssignMessagePolicies(Document assignTemplate, String operationName, String policyName)
			throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();

		Document assignPolicyXML = xmlUtils.cloneDocument(assignTemplate);

		Node rootElement = assignPolicyXML.getFirstChild();
		NamedNodeMap attr = rootElement.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(policyName);

		Node displayName = assignPolicyXML.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(operationName + " Build SOAP");

		Node payload = assignPolicyXML.getElementsByTagName("Payload").item(0);
		NamedNodeMap payloadNodeMap = payload.getAttributes();
		Node payloadAttr = payloadNodeMap.getNamedItem("contentType");
		payloadAttr.setNodeValue(StringEscapeUtils.escapeXml10(PAYLOAD_TYPE));

		assignPolicyXML.getElementsByTagName("Header").item(1)
				.setTextContent(StringEscapeUtils.escapeXml10(CONTENT_TYPE));

		KeyValue<String, String> keyValue = messageTemplates.get(operationName);
		Document operationPayload = xmlUtils.getXMLFromString(keyValue.getKey());
		Node importedNode = assignPolicyXML.importNode(operationPayload.getDocumentElement(), true);
		payload.appendChild(importedNode);

		Node value = assignPolicyXML.getElementsByTagName("Value").item(0);
		value.setTextContent(targetEndpoint);

		LOGGER.fine("Generated resource xml: " + targetFolder + File.separator + "apiproxy" + File.separator
				+ "policies" + File.separator + policyName + ".xml");

		xmlUtils.writeXML(assignPolicyXML, targetFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator + policyName + ".xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeTargetEndpoint() throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();
		Document targetDefault;

		if (PASSTHRU) {
			targetDefault = xmlUtils.readXML(SOAPPASSTHRU_TARGET_TEMPLATE);
		} else {
			targetDefault = xmlUtils.readXML(SOAP2API_TARGET_TEMPLATE);
		}

		Node urlNode = targetDefault.getElementsByTagName("URL").item(0);

		if (targetEndpoint != null && targetEndpoint.equalsIgnoreCase("") != true) {
			urlNode.setTextContent(targetEndpoint);
		} else {
			LOGGER.warning("No target URL set");
		}

		xmlUtils.writeXML(targetDefault, targetFolder + File.separator + "apiproxy" + File.separator + "targets"
				+ File.separator + "default.xml");
		LOGGER.info("Generated Target xml: " + targetFolder + File.separator + "apiproxy" + File.separator + "targets"
				+ File.separator + "default.xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeStdPolicies() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			String sourcePath = "." + File.separator + "templates" + File.separator;
			String targetPath = targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			String xslResourcePath = targetFolder + File.separator + "apiproxy" + File.separator + "resources"
					+ File.separator + "xsl" + File.separator;
			LOGGER.fine("Source Path: " + sourcePath);
			LOGGER.fine("Target Path: " + targetPath);
			if (PASSTHRU) {
				sourcePath += "soappassthru" + File.separator;
				Files.copy(Paths.get(sourcePath + "Extract-Operation-Name.xml"),
						Paths.get(targetPath + "Extract-Operation-Name.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "Invalid-SOAP.xml"), Paths.get(targetPath + "Invalid-SOAP.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} else {
				sourcePath += "soap2api" + File.separator;
				Files.copy(Paths.get(sourcePath + "xml-to-json.xml"), Paths.get(targetPath + "xml-to-json.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "set-response-soap-body.xml"),
						Paths.get(targetPath + "set-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "get-response-soap-body.xml"),
						Paths.get(targetPath + "get-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "set-target-url.xml"), Paths.get(targetPath + "set-target-url.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "restore-message.xml"), Paths.get(targetPath + "restore-message.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "save-message.xml"), Paths.get(targetPath + "save-message.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "unknown-resource.xml"),
						Paths.get(targetPath + "unknown-resource.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "remove-empty-nodes.xml"),
						Paths.get(targetPath + "remove-empty-nodes.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "remove-empty-nodes.xslt"),
						Paths.get(xslResourcePath + "remove-empty-nodes.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.severe(e.getMessage());
			e.printStackTrace();
			throw e;
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAPPassThruProxyEndpointConditions(String basePath, String soapVersion) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String soapConditionText = "(envelope != \"Envelope\") or (body != \"Body\") or (envelopeNamespace !=\"";

		XMLUtils xmlUtils = new XMLUtils();
		Document proxyDefault = xmlUtils.readXML(SOAPPASSTHRU_PROXY_TEMPLATE);
		Node basePathNode = proxyDefault.getElementsByTagName("BasePath").item(0);

		if (basePath != null && basePath.equalsIgnoreCase("") != true) {
			basePathNode.setTextContent(basePath);
		}

		Node soapCondition = proxyDefault.getElementsByTagName("Condition").item(1);
		if (soapVersion.equalsIgnoreCase("SOAP11")) {
			soapCondition.setTextContent(soapConditionText + SOAP11 + "\")");
		} else {
			soapCondition.setTextContent(soapConditionText + SOAP12 + "\")");
		}

		String conditionText = "(proxy.pathsuffix MatchesPath \"/\") and (request.verb = \"POST\") and (operation = \"";
		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1;
		Node name1;

		for (PortType pt : wsdl.getPortTypes()) {
			for (Operation op : pt.getOperations()) {
				flow = proxyDefault.createElement("Flow");
				((Element) flow).setAttribute("name", op.getName());

				flowDescription = proxyDefault.createElement("Description");
				flowDescription.setTextContent(op.getName());
				flow.appendChild(flowDescription);

				request = proxyDefault.createElement("Request");
				response = proxyDefault.createElement("Response");
				condition = proxyDefault.createElement("Condition");
				condition.setTextContent(conditionText + op.getName() + "\")");

				flow.appendChild(request);
				flow.appendChild(response);
				flow.appendChild(condition);

				flows.appendChild(flow);
			}
		}

		// Add unknown resource
		flow = proxyDefault.createElement("Flow");
		((Element) flow).setAttribute("name", "unknown-resource");

		flowDescription = proxyDefault.createElement("Description");
		flowDescription.setTextContent("Unknown Resource");
		flow.appendChild(flowDescription);

		request = proxyDefault.createElement("Request");
		response = proxyDefault.createElement("Response");
		condition = proxyDefault.createElement("Condition");

		step1 = proxyDefault.createElement("Step");
		name1 = proxyDefault.createElement("Name");
		name1.setTextContent("Invalid-SOAP");
		step1.appendChild(name1);
		request.appendChild(step1);

		flow.appendChild(request);
		flow.appendChild(response);
		flow.appendChild(condition);

		flows.appendChild(flow);

		xmlUtils.writeXML(proxyDefault, targetFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + targetFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	private void parseWSDL(String wsdlPath) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			WSDLParser parser = new WSDLParser();
			wsdl = parser.parse(wsdlPath);
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.severe(e.getLocalizedMessage());
			throw e;
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void getTargetEndpoint() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		try {
			Service service = wsdl.getServices().get(0);
			Port port = service.getPorts().get(0);
			targetEndpoint = port.getAddress().getLocation();
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.severe(e.getLocalizedMessage());
			throw e;
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private KeyValue<String, String> getProxyNameAndBasePath(String wsdlPath) throws Exception {

		if (wsdl.getServices().size() == 0) {
			LOGGER.severe("No services were found in the WSDL");
			throw new NoServicesFoundException("No services were found in the WSDL");
		}

		String serviceName = wsdl.getServices().get(0).getName();
		KeyValue<String, String> map;

		if (serviceName != null) {
			map = new KeyValue<String, String>(serviceName, "/" + serviceName.toLowerCase());
		} else {
			map = StringUtils.proxyNameAndBasePath(wsdlPath);
		}
		return map;
	}

	private void storeMessages(String soapVersion) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();
		StringWriter writer = new StringWriter();
		SOARequestCreator creator = new SOARequestCreator(wsdl, new RequestTemplateCreator(),
				new MarkupBuilder(writer));
		LOGGER.fine("PASSED SOAP Version: " + soapVersion);

		for (Service service : wsdl.getServices()) {
			LOGGER.fine("FOUND Service: " + service.getName());
			for (Port port : service.getPorts()) {
				LOGGER.fine("Found Port: " + port.getName());
				Binding binding = port.getBinding();
				LOGGER.fine("Found Binding: " + binding.getName() + " Binding Protocol: " + binding.getProtocol()
						+ " Prefix: " + binding.getPrefix() + " NamespaceURI: " + binding.getNamespaceUri());
				if (binding.getProtocol().toString().equalsIgnoreCase(soapVersion)) {
					PortType portType = binding.getPortType();
					for (Operation op : portType.getOperations()) {
						LOGGER.fine("Found Operation Name: " + op.getName() + " Prefix: " + op.getPrefix()
								+ " NamespaceURI: " + op.getNamespaceUri());
						creator.setCreator(new RequestTemplateCreator());
						try {
							// use membrane SOAP to generate a SOAP Request
							creator.createRequest(port.getName(), op.getName(), binding.getName());
							// store the operation name, SOAP Request and the
							// expected JSON Body in the map
							messageTemplates.put(op.getName(), xmlUtils.replacePlaceHolders(writer.toString()));
							writer.getBuffer().setLength(0);
						} catch (Exception e) {
							LOGGER.severe(e.getMessage());
							e.printStackTrace();
							throw e;
						}
					}
				}
			}
		}

		if (messageTemplates.size() == 0) {
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			throw new BindingNotFoundException("Soap version provided did not match any binding in WSDL");
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private boolean prepareTargetFolder() {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		File f = new File(targetFolder);

		if (f.isDirectory()) { // ensure target is a folder
			LOGGER.fine("Target is a folder");
			File apiproxy = new File(f.getAbsolutePath() + File.separator + "apiproxy");
			if (apiproxy.exists()) {
				LOGGER.severe("Folder called apiproxy already exists");
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				return false;
			} else {
				apiproxy.mkdir();
				LOGGER.fine("created apiproxy folder");
				new File(apiproxy.getAbsolutePath() + File.separator + "policies").mkdirs();
				LOGGER.fine("created policies folder");
				new File(apiproxy.getAbsolutePath() + File.separator + "proxies").mkdirs();
				LOGGER.fine("created proxies folder");
				new File(apiproxy.getAbsolutePath() + File.separator + "targets").mkdirs();
				LOGGER.fine("created targets folder");
				if (!PASSTHRU) {
					new File(apiproxy.getAbsolutePath() + File.separator + "resources" + File.separator + "xsl")
							.mkdirs();
					LOGGER.fine("created resources folder");
				}
				LOGGER.info("Target proxy folder setup complete");
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				return true;
			}
		} else {
			LOGGER.severe("Target folder is not a directory");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return false;
		}
	}

	private void removeBuildFolder(File targetFolder) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		File[] files = targetFolder.listFiles();
		if (files != null) { // some JVMs return null for empty dirs
			for (File f : files) {
				if (f.isDirectory()) {
					removeBuildFolder(f);
				} else {
					f.delete();
				}
			}
		}
		targetFolder.delete();
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	public void begin(String proxyDescription, String wsdlPath, String soapVersion) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		LOGGER.fine("Preparing target folder");
		String zipFolder = targetFolder + File.separator + "apiproxy";

		try {
			// prepare the target folder (create apiproxy folder and sub-folders
			if (prepareTargetFolder()) {
				// parse the wsdl
				parseWSDL(wsdlPath);
				LOGGER.info("Parsed WSDL Successfully.");

				// infer proxyname and basepath from wsdl
				KeyValue<String, String> map = getProxyNameAndBasePath(wsdlPath);
				String proxyName = map.getKey();
				String basePath = map.getValue();

				LOGGER.info("Proxy Name: " + proxyName + "\nProxy Description: " + proxyDescription);
				LOGGER.info("Base Path: " + basePath + "\nWSDL Path: " + wsdlPath);
				LOGGER.info("Target Folder: " + targetFolder + "\nSOAP Version: " + soapVersion);

				// if not passthru read conf file to interpret soap operations
				// to resources
				if (!PASSTHRU) {
					OpsMap.readOperationsMap(OPSMAPPING_TEMPLATE);
					LOGGER.info("Read operations map");
				}

				// create the basic proxy structure from templates
				writeAPIProxy(proxyName, proxyDescription);
				LOGGER.info("Generated Apigee proxy file.");

				// get the endpoint
				getTargetEndpoint();
				LOGGER.info("Retrieved WSDL endpoint: " + targetEndpoint);

				if (!PASSTHRU) {
					// produce SOAP Requests for each operation in the WSDL
					storeMessages(soapVersion);
					LOGGER.info("Generated SOAP Message Templates.");
					writeSOAP2APIProxyEndpoint(proxyName, basePath);
					LOGGER.info("Generated proxies XML.");
					writeStdPolicies();
					LOGGER.info("Copied standard policies.");
					writeTargetEndpoint();
					LOGGER.info("Generated target XML.");
				} else {
					writeStdPolicies();
					LOGGER.info("Copied standard policies.");
					writeTargetEndpoint();
					LOGGER.info("Generated target XML.");
					writeSOAPPassThruProxyEndpointConditions(basePath, soapVersion);
				}

				GenerateBundle.build(zipFolder, proxyName);
				LOGGER.info("Generated Apigee Edge API Bundle file: " + proxyName + ".zip");
				removeBuildFolder(new File(zipFolder));
				LOGGER.info("Cleaned up temp folder");
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
			} else {
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				throw new TargetFolderException("Erorr is preparing target folder");
			}
		} catch (SecurityException e) {
			removeBuildFolder(new File(zipFolder));
			LOGGER.severe(e.getMessage());
		} catch (TargetFolderException e) {
			removeBuildFolder(new File(zipFolder));
			LOGGER.severe(e.getMessage());
		} catch (Exception e) {
			removeBuildFolder(new File(zipFolder));
			LOGGER.severe(e.getMessage());
		}
	}

	public static void main(String[] args) throws Exception {

		GenerateProxy genProxy = new GenerateProxy();

		String wsdlPath = "";
		String proxyDescription = "";
		String soapVersion = "";

		Options opt = new Options(args, 1);
		// the wsdl param contains the URL or FilePath to a WSDL document
		opt.getSet().addOption("wsdl", Separator.EQUALS, Multiplicity.ONCE);
		// if this flag it set, the generate a passthru proxy
		opt.getSet().addOption("passthru", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to specify target folder
		opt.getSet().addOption("target", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to pass proxy description
		opt.getSet().addOption("desc", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to specify soap version
		opt.getSet().addOption("soap", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to enable debug
		opt.getSet().addOption("debug", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);

		opt.check();

		if (opt.getSet().isSet("wsdl")) {
			// React to option -wsdl
			wsdlPath = opt.getSet().getOption("wsdl").getResultValue(0);
		} else {
			System.out.println("-wsdl is a madatory parameter");
			System.exit(1);
		}

		if (opt.getSet().isSet("target")) {
			// React to option -target
			genProxy.setTargetFolder(opt.getSet().getOption("target").getResultValue(0));
		}

		if (opt.getSet().isSet("passthru")) {
			// React to option -passthru
			genProxy.setPassThru(Boolean.parseBoolean(opt.getSet().getOption("passthru").getResultValue(0)));
		} else {
			genProxy.setPassThru(false);
		}

		if (opt.getSet().isSet("desc")) {
			// React to option -des
			proxyDescription = opt.getSet().getOption("desc").getResultValue(0);
		} else {
			proxyDescription = "Generated SOAP to API proxy";
		}

		if (opt.getSet().isSet("soap")) {
			// React to option -soap
			soapVersion = opt.getSet().getOption("soap").getResultValue(0);
		} else {
			soapVersion = "SOAP12";
		}

		if (opt.getSet().isSet("debug")) {
			// enable debug
			LOGGER.setLevel(Level.FINEST);
			handler.setLevel(Level.FINEST);
		} else {
			LOGGER.setLevel(Level.INFO);
			handler.setLevel(Level.INFO);
		}

		// wsdlPath =
		// "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";

		// genProxy.PASSTHRU = true;
		// wsdlPath = "https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl";
		// wsdlPath =
		// "http://www.konakart.com/konakart/services/KKWebServiceEng?wsdl";

		genProxy.begin(proxyDescription, wsdlPath, soapVersion);

	}
}
