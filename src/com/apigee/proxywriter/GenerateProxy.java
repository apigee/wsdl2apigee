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
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.lang3.StringEscapeUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.apigee.proxywriter.exception.BindingNotFoundException;
import com.apigee.proxywriter.exception.NoServicesFoundException;
import com.apigee.proxywriter.exception.TargetFolderException;
import com.apigee.utils.APIMap;
import com.apigee.utils.KeyValue;
import com.apigee.utils.OpsMap;
import com.apigee.utils.Options;
import com.apigee.utils.Options.Multiplicity;
import com.apigee.utils.Options.Separator;
import com.apigee.utils.StringUtils;
import com.apigee.utils.XMLUtils;
import com.predic8.wsdl.AbstractSOAPBinding;
import com.predic8.wsdl.Binding;
import com.predic8.wsdl.BindingOperation;
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

	private static final String SOAP11_CONTENT_TYPE = "text/xml; charset=utf-8";// "text&#x2F;xml;
																				// charset=utf-8";
	private static final String SOAP12_CONTENT_TYPE = "application/soap+xml";

	private static final String SOAP11_PAYLOAD_TYPE = "text/xml";// "text&#x2F;xml";
	private static final String SOAP12_PAYLOAD_TYPE = "application/soap+xml";

	private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
	private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

	// set this to true if SOAP passthru is needed
	private boolean PASSTHRU;

	private String targetEndpoint;

	private String soapVersion;

	private String serviceName;

	private String portName;

	private String basePath;

	private String proxyName;

	// default target folder is ./build
	private String targetFolder;

	// Each row in this Map has the key as the operation name. The operation
	// name has SOAP Request
	// and JSON Equivalent of SOAP (without the SOAP Envelope) as values.
	// private Map<String, KeyValue<String, String>> messageTemplates;
	private Map<String, APIMap> messageTemplates;

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
		messageTemplates = new HashMap<String, APIMap>();
		targetFolder = "." + File.separator + "build";
		soapVersion = "SOAP12";
	}

	public void setTargetFolder(String folder) {
		targetFolder = folder;
	}

	public void setPassThru(boolean pass) {
		PASSTHRU = pass;
	}

	public void setService(String serv) {
		serviceName = serv;
	}

	public void setPort(String prt) {
		portName = prt;
	}

	private void writeAPIProxy(String proxyDescription) throws Exception {

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

	private void writeSOAP2APIProxyEndpoint() throws Exception {

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

		boolean addJsonToXMLPolicy = false;

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1, step2;
		Node name1, name2;

		for (Map.Entry<String, APIMap> entry : messageTemplates.entrySet()) {
			String operationName = entry.getKey();
			APIMap apiMap = entry.getValue();
			String buildSOAPPolicy = operationName + "-build-soap";
			String extractPolicyName = operationName + "-extract-query-param";
			String jsonToXML = "json-to-xml";

			String httpVerb = apiMap.getVerb();
			String resourcePath = apiMap.getResourcePath();
			String Condition = "(proxy.pathsuffix MatchesPath \"" + resourcePath + "\") and (request.verb = \""
					+ httpVerb + "\")";

			flow = proxyDefault.createElement("Flow");
			((Element) flow).setAttribute("name", operationName);

			flowDescription = proxyDefault.createElement("Description");
			flowDescription.setTextContent(operationName);
			flow.appendChild(flowDescription);

			request = proxyDefault.createElement("Request");
			response = proxyDefault.createElement("Response");
			condition = proxyDefault.createElement("Condition");

			step1 = proxyDefault.createElement("Step");
			name1 = proxyDefault.createElement("Name");

			if (httpVerb.equalsIgnoreCase("get")) {
				name1.setTextContent(extractPolicyName);
				step1.appendChild(name1);
				request.appendChild(step1);

				step2 = proxyDefault.createElement("Step");
				name2 = proxyDefault.createElement("Name");
				name2.setTextContent(buildSOAPPolicy);
				step2.appendChild(name2);
				request.appendChild(step2);

				LOGGER.fine("Assign Message: " + buildSOAPPolicy);
				LOGGER.fine("Extract Variable: " + extractPolicyName);

			} else {
				name1.setTextContent(jsonToXML);
				step1.appendChild(name1);
				request.appendChild(step1);
			}

			LOGGER.fine("Condition: " + Condition);
			condition.setTextContent(Condition);

			flow.appendChild(request);
			flow.appendChild(response);
			flow.appendChild(condition);

			flows.appendChild(flow);

			if (httpVerb.equalsIgnoreCase("get")) {
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
				writeSOAP2APIExtractPolicy(extractTemplate, operationName, extractPolicyName);
			} else {
				if (!addJsonToXMLPolicy) {
					Node policy1 = apiTemplateDocument.createElement("Policy");
					policy1.setTextContent(jsonToXML);
					// this will ensure the file is only copied once for all
					// non-get operations
					addJsonToXMLPolicy = true;
					writeJsonToXMLPolicy();
				}
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

	private void writeSOAP2APIExtractPolicy(Document extractTemplate, String operationName, String policyName)
			throws Exception {
		XMLUtils xmlUtils = new XMLUtils();
		Element queryParam;
		Element pattern;

		Document extractPolicyXML = xmlUtils.cloneDocument(extractTemplate);

		Node rootElement = extractPolicyXML.getFirstChild();
		NamedNodeMap attr = rootElement.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(policyName);

		Node displayName = extractPolicyXML.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(operationName + " Extract Query Param");

		APIMap apiMap = messageTemplates.get(operationName);

		List<String> elementList = xmlUtils.getElementList(apiMap.getSoapBody());
		for (String elementName : elementList) {
			queryParam = extractPolicyXML.createElement("QueryParam");
			queryParam.setAttribute("name", elementName);

			pattern = extractPolicyXML.createElement("Pattern");
			pattern.setAttribute("ignoreCase", "true");
			pattern.setTextContent("{" + elementName + "}");

			queryParam.appendChild(pattern);
			rootElement.appendChild(queryParam);
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

		if (soapVersion.equalsIgnoreCase("SOAP12")) {
			payloadAttr.setNodeValue(StringEscapeUtils.escapeXml10(SOAP12_PAYLOAD_TYPE));

			assignPolicyXML.getElementsByTagName("Header").item(1)
					.setTextContent(StringEscapeUtils.escapeXml10(SOAP12_CONTENT_TYPE));
		} else {
			payloadAttr.setNodeValue(StringEscapeUtils.escapeXml10(SOAP11_PAYLOAD_TYPE));

			assignPolicyXML.getElementsByTagName("Header").item(1)
					.setTextContent(StringEscapeUtils.escapeXml10(SOAP11_CONTENT_TYPE));
		}

		APIMap apiMap = messageTemplates.get(operationName);
		Document operationPayload = xmlUtils.getXMLFromString(apiMap.getSoapBody());
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

	private void writeJsonToXMLPolicy() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			String sourcePath = "." + File.separator + "templates" + File.separator + "soap2api" + File.separator;
			String targetPath = targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			Files.copy(Paths.get(sourcePath + "json-to-xml.xml"), Paths.get(targetPath + "json-to-xml.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.severe(e.getMessage());
			e.printStackTrace();
			throw e;
		}
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
				Files.copy(Paths.get(sourcePath + "remove-envelope.xslt"),
						Paths.get(xslResourcePath + "remove-envelope.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "remove-soap-envelope.xml"),
						Paths.get(targetPath + "remove-soap-envelope.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "return-generic-error.xml"),
						Paths.get(targetPath + "return-generic-error.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "xml-fault-to-json.xml"),
						Paths.get(targetPath + "xml-fault-to-json.xml"),
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

	private void writeSOAPPassThruProxyEndpointConditions() throws Exception {
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

		for (Map.Entry<String, APIMap> entry : messageTemplates.entrySet()) {

			flow = proxyDefault.createElement("Flow");
			((Element) flow).setAttribute("name", entry.getKey());

			flowDescription = proxyDefault.createElement("Description");
			flowDescription.setTextContent(entry.getKey());
			flow.appendChild(flowDescription);

			request = proxyDefault.createElement("Request");
			response = proxyDefault.createElement("Response");
			condition = proxyDefault.createElement("Condition");
			condition.setTextContent(conditionText + entry.getKey() + "\")");

			flow.appendChild(request);
			flow.appendChild(response);
			flow.appendChild(condition);

			flows.appendChild(flow);
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
		XMLUtils xmlUtils = new XMLUtils();
		StringWriter writer = new StringWriter();
		Definitions wsdl = null;
		SOARequestCreator creator = null;
		Service service = null;
		Port port = null;
		String bindingName;
		
		try {
			WSDLParser parser = new WSDLParser();
			wsdl = parser.parse(wsdlPath);
			if (wsdl.getServices().size() == 0) {
				LOGGER.severe("No services were found in the WSDL");
				throw new NoServicesFoundException("No services were found in the WSDL");
			}

			creator = new SOARequestCreator(wsdl, new RequestTemplateCreator(), new MarkupBuilder(writer));
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.severe(e.getLocalizedMessage());
			throw e;
		}

		if (serviceName != null) {
			for (Service svc : wsdl.getServices()) {
				if (svc.getName().equalsIgnoreCase(serviceName)) {
					service = svc;
					break;
				}
			}
			if (service == null) { //didn't find any service matching name
				LOGGER.severe("No matching services were found in the WSDL");
				throw new NoServicesFoundException("No matching services were found in the WSDL");
			}
		} else {			
			service = wsdl.getServices().get(0); // get the first service
		}
		
		LOGGER.fine("Found Service: " + service.getName());
		String serviceName = service.getName();

		if (serviceName != null) {
			proxyName = serviceName;
			basePath = "/" + serviceName.toLowerCase();
		} else {
			KeyValue<String, String> map = StringUtils.proxyNameAndBasePath(wsdlPath);
			proxyName = map.getKey();
			basePath = map.getValue();
		}
		
		if ( portName != null ) {
			for (Port prt : service.getPorts()) {
				if (prt.getName().equalsIgnoreCase(portName)) {
					port = prt;
				}
			}
			if (port == null) { //didn't find any port matching name
				LOGGER.severe("No matching port were found in the WSDL");
				throw new NoServicesFoundException("No matching were found in the WSDL");
			}
		} else  {
			port = service.getPorts().get(0); // get first port
		}
		LOGGER.fine("Found Port: " + port.getName());
		
		Binding binding = port.getBinding();
		bindingName = binding.getName();
		soapVersion = binding.getProtocol().toString();
		
		LOGGER.fine("Found Binding: " + bindingName + " Binding Protocol: " + soapVersion + " Prefix: "
				+ binding.getPrefix() + " NamespaceURI: " + binding.getNamespaceUri());
		
		targetEndpoint = port.getAddress().getLocation();
		LOGGER.info("Retrieved WSDL endpoint: " + targetEndpoint);

		PortType portType = binding.getPortType();
		for (Operation op : portType.getOperations()) {
			LOGGER.fine("Found Operation Name: " + op.getName() + " Prefix: " + op.getPrefix() + " NamespaceURI: "
					+ op.getNamespaceUri());
			try {
				if (!PASSTHRU) {
					APIMap apiMap = null;
					String resourcePath = OpsMap.getResourcePath(op.getName());
					String verb = OpsMap.getOpsMap(op.getName());
					if (verb.equalsIgnoreCase("GET")) {
						creator.setCreator(new RequestTemplateCreator());
						// use membrane SOAP to generate a SOAP Request
						creator.createRequest(port.getName(), op.getName(), binding.getName());
						// store the operation name, SOAP Request and the
						// expected JSON Body in the map
						KeyValue<String, String> kv = xmlUtils.replacePlaceHolders(writer.toString());
						apiMap = new APIMap(kv.getValue(), kv.getKey(), resourcePath, verb);
						writer.getBuffer().setLength(0);
					} else {
						apiMap = new APIMap("", "", resourcePath, verb);
					}
					messageTemplates.put(op.getName(), apiMap);
				} else {
					messageTemplates.put(op.getName(), null);
				}

			} catch (Exception e) {
				LOGGER.severe(e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}

		if (messageTemplates.size() == 0) {
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			throw new BindingNotFoundException("Soap version provided did not match any binding in WSDL");
		}

		if (!PASSTHRU) {
	        for (Binding bnd : wsdl.getBindings()) {
	        	if (bindingName.equalsIgnoreCase(bnd.getName())) {
	                for (BindingOperation bop : bnd.getOperations()) {
	                    if(bnd.getBinding() instanceof AbstractSOAPBinding) {
	            			LOGGER.fine("Found Operation Name: " + bop.getName() + " SOAPAction: "
	            					+ bop.getOperation().getSoapAction());
	                    	APIMap apiM = messageTemplates.get(bop.getName());
	                    	apiM.setSoapAction(bop.getOperation().getSoapAction());
	                    	messageTemplates.put(bop.getName(), apiM);
	                    }
	                }
	        	}
	        }
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

	public void begin(String proxyDescription, String wsdlPath) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		LOGGER.fine("Preparing target folder");
		String zipFolder = targetFolder + File.separator + "apiproxy";

		try {
			// prepare the target folder (create apiproxy folder and sub-folders
			if (prepareTargetFolder()) {
				
				// if not passthru read conf file to interpret soap operations
				// to resources
				if (!PASSTHRU) {
					OpsMap.readOperationsMap(OPSMAPPING_TEMPLATE);
					LOGGER.info("Read operations map");
				}
				
				// parse the wsdl
				parseWSDL(wsdlPath);
				LOGGER.info("Parsed WSDL Successfully.");

				LOGGER.info("Proxy Name: " + proxyName + "\nProxy Description: " + proxyDescription);
				LOGGER.info("Base Path: " + basePath + "\nWSDL Path: " + wsdlPath);
				LOGGER.info("Target Folder: " + targetFolder + "\nSOAP Version: " + soapVersion);

				// create the basic proxy structure from templates
				writeAPIProxy(proxyDescription);
				LOGGER.info("Generated Apigee proxy file.");

				if (!PASSTHRU) {
					LOGGER.info("Generated SOAP Message Templates.");
					writeSOAP2APIProxyEndpoint();
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
					writeSOAPPassThruProxyEndpointConditions();
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

		Options opt = new Options(args, 1);
		// the wsdl param contains the URL or FilePath to a WSDL document
		opt.getSet().addOption("wsdl", Separator.EQUALS, Multiplicity.ONCE);
		// if this flag it set, the generate a passthru proxy
		opt.getSet().addOption("passthru", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to specify target folder
		opt.getSet().addOption("target", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to pass proxy description
		opt.getSet().addOption("desc", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to specify service name
		opt.getSet().addOption("service", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to specify port name
		opt.getSet().addOption("port", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
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

		if (opt.getSet().isSet("service")) {
			// React to option -service
			genProxy.setService(opt.getSet().getOption("service").getResultValue(0));
			if (opt.getSet().isSet("port")) {
				// React to option -port
				genProxy.setPort(opt.getSet().getOption("port").getResultValue(0));
			}
		}

		if (opt.getSet().isSet("debug")) {
			// enable debug
			LOGGER.setLevel(Level.FINEST);
			handler.setLevel(Level.FINEST);
		} else {
			LOGGER.setLevel(Level.FINE);
			handler.setLevel(Level.FINE);
		}

		// wsdlPath =
		// "/Users/srinandansridhar/Downloads/FoxEDFEmailEBO/FoxEDFEventProducer_BPEL_Client_ep.wsdl";
		// wsdlPath =
		// "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";

		genProxy.PASSTHRU = false;
		// wsdlPath = "https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl";
		// wsdlPath =
		// "http://www.konakart.com/konakart/services/KKWebServiceEng?wsdl";

		genProxy.begin(proxyDescription, wsdlPath);

		// TODO: in the fault rules, if the response is not a soap fault, handle
		// it as xml...
		// TODO: deal with soap action
	}
}
