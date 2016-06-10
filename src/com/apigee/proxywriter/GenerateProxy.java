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

import java.util.ArrayList;

import java.nio.file.Path;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.apigee.proxywriter.exception.UnSupportedWSDLException;
import com.apigee.utils.APIMap;
import com.apigee.utils.KeyValue;
import com.apigee.utils.OpsMap;
import com.apigee.utils.Options;
import com.apigee.utils.Options.Multiplicity;
import com.apigee.utils.Options.Separator;
import com.apigee.utils.StringUtils;
import com.apigee.utils.XMLUtils;
import com.apigee.xsltgen.Rule;
import com.apigee.xsltgen.RuleSet;
import com.predic8.schema.Choice;
import com.predic8.schema.ComplexContent;
import com.predic8.schema.ComplexType;
import com.predic8.schema.Derivation;
import com.predic8.schema.ModelGroup;
import com.predic8.schema.Schema;
import com.predic8.schema.SchemaComponent;
import com.predic8.schema.Sequence;
import com.predic8.schema.SimpleContent;
import com.predic8.schema.TypeDefinition;
import com.predic8.soamodel.XMLElement;
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
import groovy.xml.QName;

import java.nio.file.Files;
import java.nio.file.Paths;

public class GenerateProxy {

	private static final Logger LOGGER = Logger.getLogger(GenerateProxy.class.getName());
	private static final ConsoleHandler handler = new ConsoleHandler();

	private static final String OPSMAPPING_TEMPLATE = "." + File.separator + "opsmapping.xml";

	private static String SOAP2API_XSL = "";

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
	private static final String SOAP2API_XSLTPOLICY_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "add-namespace.xml";
	private static final String SOAP2API_XSLT_TEMPLATE = "." + File.separator + "templates" + File.separator
			+ "soap2api" + File.separator + "add-namespace.xslt";

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
	//
	private ArrayList<String> xpaths = new ArrayList<String>();
	//
	private ArrayList<Rule> ruleList = new ArrayList<Rule>();

	public static Map<String, String> namespace = new LinkedHashMap<String, String>();

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

		Document addNamespaceTemplate = xmlUtils.readXML(SOAP2API_XSLTPOLICY_TEMPLATE);

		Node policies = apiTemplateDocument.getElementsByTagName("Policies").item(0);

		boolean addJsonToXMLPolicy = false;

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1, step2, step3;
		Node name1, name2, name3;

		for (Map.Entry<String, APIMap> entry : messageTemplates.entrySet()) {
			String operationName = entry.getKey();
			APIMap apiMap = entry.getValue();
			String buildSOAPPolicy = operationName + "-build-soap";
			String extractPolicyName = operationName + "-extract-query-param";
			String jsonToXML = "json-to-xml";
			String soap12 = "add-soap12";
			String soap11 = "add-soap11";

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

			step2 = proxyDefault.createElement("Step");
			name2 = proxyDefault.createElement("Name");

			step3 = proxyDefault.createElement("Step");
			name3 = proxyDefault.createElement("Name");

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

				name2.setTextContent(operationName + "-add-namespace");
				step2.appendChild(name2);
				request.appendChild(step2);

				if (soapVersion.equalsIgnoreCase("SOAP12")) {
					name3.setTextContent(soap12);
					step3.appendChild(name3);
					request.appendChild(step3);
				} else {
					name3.setTextContent(soap11);
					step3.appendChild(name3);
					request.appendChild(step3);
				}
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

					Node policy2 = apiTemplateDocument.createElement("Policy");
					if (soapVersion.equalsIgnoreCase("SOAP12")) {
						policy2.setTextContent(soap12);
					} else {
						policy2.setTextContent(soap11);
					}
					// this will ensure the file is only copied once for all
					// non-get operations
					addJsonToXMLPolicy = true;
					writeJsonToXMLPolicy();
				}
				Node policy3 = apiTemplateDocument.createElement("Policy");
				policy3.setTextContent(operationName + "add-namespace");
				writeAddNamespace(addNamespaceTemplate, operationName);
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

	private void writeAddNamespace(Document namespaceTemplate, String operationName) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			XMLUtils xmlUtils = new XMLUtils();
			String policyName = operationName + "-add-namespace";
			Document xslPolicyXML = xmlUtils.cloneDocument(namespaceTemplate);

			Node rootElement = xslPolicyXML.getFirstChild();
			NamedNodeMap attr = rootElement.getAttributes();
			Node nodeAttr = attr.getNamedItem("name");
			nodeAttr.setNodeValue(policyName);

			Node displayName = xslPolicyXML.getElementsByTagName("DisplayName").item(0);
			displayName.setTextContent(operationName + " Add Namespace");

			Node resourceURL = xslPolicyXML.getElementsByTagName("ResourceURL").item(0);
			resourceURL.setTextContent("xsl://" + policyName + ".xslt");

			xmlUtils.writeXML(xslPolicyXML, targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator + policyName + ".xml");
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAP2APIExtractPolicy(Document extractTemplate, String operationName, String policyName)
			throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
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
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
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
			String xslResourcePath = targetFolder + File.separator + "apiproxy" + File.separator + "resources"
					+ File.separator + "xsl" + File.separator;
			String targetPath = targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			Files.copy(Paths.get(sourcePath + "json-to-xml.xml"), Paths.get(targetPath + "json-to-xml.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			if (soapVersion.equalsIgnoreCase("SOAP12")) {
				Files.copy(Paths.get(sourcePath + "add-soap12.xml"), Paths.get(targetPath + "add-soap12.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "add-soap12.xslt"), Paths.get(xslResourcePath + "add-soap12.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.copy(Paths.get(sourcePath + "add-soap11.xml"), Paths.get(targetPath + "add-soap11.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "add-soap11.xslt"), Paths.get(xslResourcePath + "add-soap11.xslt"),
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
				Files.copy(Paths.get(sourcePath + "unknown-resource.xml"),
						Paths.get(targetPath + "unknown-resource.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "remove-empty-nodes.xml"),
						Paths.get(targetPath + "remove-empty-nodes.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "remove-empty-nodes.xslt"),
						Paths.get(xslResourcePath + "remove-empty-nodes.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(Paths.get(sourcePath + "return-generic-error.xml"),
						Paths.get(targetPath + "return-generic-error.xml"),
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

	public static String getPrefix(String namespaceUri) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(namespaceUri)) {
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				return entry.getKey();
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return "ns";
	}

	public static String getNamespaceUri(String prefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(prefix)) {
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				return entry.getValue();
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return null;
	}
	
    private void parseElement(com.predic8.schema.Element e, List<Schema> schemas, String rootElement, String rootNamespace) {
        if (e.getName() == null) {
            if (e.getRef() != null) {
                final String localPart = e.getRef().getLocalPart();
                final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
                //out(element.getName());
                parseSchema(element, schemas, rootElement, rootNamespace);
            }
            else {
                //out("Trouble");
            	LOGGER.warning("unhandle conditions getRef() = null");
            }
        }
        else {
            if (!e.getName().equalsIgnoreCase(rootElement)) {
                if (e.getEmbeddedType() instanceof ComplexType) {
                    ComplexType ct = (ComplexType)e.getEmbeddedType();
                    parseSchema(ct.getModel(), schemas, rootElement, rootNamespace);
                } else {
                    if (e.getType() == null) {
                    	//TODO: handle this
                    	LOGGER.warning("unhandle conditions - getRef() = null");
                    }
                    else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
							&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
						buildXPath(e.getParent(), e.getName(), false, rootElement);
                    }
                }
            }
        }
    }	
    
    private com.predic8.schema.Element elementFromSchema(String name, List<Schema> schemas) {
        if (name != null) {
            for (Schema schema: schemas) {
                try {
                    final com.predic8.schema.Element element = schema.getElement(name);
                    if (element != null) {
                        return element;
                    }
                } catch (Exception e) {
                	LOGGER.warning("unhandle conditions: " + e.getMessage());
                }
            }
        }
        return null;
    }
    

	private void parseSchema(SchemaComponent sc, List<Schema> schemas, String rootElement, String rootNamespace) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			for (com.predic8.schema.Element e : seq.getElements()) {
				parseElement(e, schemas, rootElement, rootNamespace);
			}
		} else if (sc instanceof Choice) {
			Choice ch = (Choice) sc;
			for (com.predic8.schema.Element e : ch.getElements()) {
				if (!e.getName().equalsIgnoreCase(rootElement)) {
					if (e.getEmbeddedType() instanceof ComplexType) {
						ComplexType ct = (ComplexType) e.getEmbeddedType();
						parseSchema(ct.getModel(), schemas, rootElement, rootNamespace);
					} else {
						final TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
						if (typeDefinition instanceof ComplexType) {
							parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace);
						}
						if (e.getType() == null) {
							// TODO: handle this
							LOGGER.warning("unhandle conditions getRef() = null");
						} else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
								&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e.getParent(), e.getName(), false, rootElement);
						}
					}
				}
			}
		} else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent) sc;
			Derivation derivation = complexContent.getDerivation();
			if (derivation != null) {
				if (derivation.getModel() instanceof Sequence) {
					parseSchema(derivation.getModel(), schemas, rootElement, rootNamespace);
				} else if (derivation.getModel() instanceof ModelGroup) {
					parseSchema(derivation.getModel(), schemas, rootElement, rootNamespace);
				}
			}
		} else if (sc instanceof SimpleContent) {
			SimpleContent simpleContent = (SimpleContent) sc;
			Derivation derivation = (Derivation) simpleContent.getDerivation();

			if (derivation.getAllAttributes().size() > 0) {
				buildXPath(simpleContent.getParent(), null, true, rootElement); // has
																				// attributes
			} else {
				buildXPath(simpleContent.getParent(), null, false, rootElement); // has
																					// no
																					// attributes
			}
		} else {
			// TODO: handle this
			LOGGER.warning("unhandle conditions - " + sc.getClass().getName());
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private TypeDefinition getTypeFromSchema(QName qName, List<Schema> schemas) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (qName != null) {
			for (Schema schema : schemas) {
				try {
					final TypeDefinition type = schema.getType(qName);
					if (type != null) {
						return type;
					}
				} catch (Exception e) {
					// Fail silently
					LOGGER.warning("unhandle conditions: " + e.getMessage());
				}
			}
		}
		return null;
	}

	private void buildXPath(XMLElement xml, String elementName, boolean hasAttributes, String rootElement) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String start = "/" + rootElement + "/*/";
		Rule r = null;

		getXPathElement(xml);

		if (elementName != null)
			xpaths.add(elementName);

		for (String s : xpaths) {
			r = new Rule(start + s, getPrefix(xml.getNamespaceUri()), xml.getNamespaceUri());
			ruleList.add(r);
			start = start + s + "/";
		}
		if (hasAttributes && xpaths.size() > 0) {
			start = start + "@*";
			r = new Rule(start, getPrefix(xml.getNamespaceUri()), xml.getNamespaceUri());
			ruleList.add(r);
		}
		xpaths.clear();

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	private void getXPathElement(XMLElement xml) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (xml == null) {
			return;
		} else if (xml instanceof com.predic8.schema.Element) {
			xpaths.add(((com.predic8.schema.Element) xml).getName());
			getXPathElement(xml.getParent());
		} else {
			getXPathElement(xml.getParent());
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	private String getParentNamepace(com.predic8.schema.Element e) {
		return e.getParent().getNamespaceUri();
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
			if (service == null) { // didn't find any service matching name
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

		if (portName != null) {
			for (Port prt : service.getPorts()) {
				if (prt.getName().equalsIgnoreCase(portName)) {
					port = prt;
				}
			}
			if (port == null) { // didn't find any port matching name
				LOGGER.severe("No matching port were found in the WSDL");
				throw new NoServicesFoundException("No matching were found in the WSDL");
			}
		} else {
			port = service.getPorts().get(0); // get first port
		}
		LOGGER.fine("Found Port: " + port.getName());

		Binding binding = port.getBinding();
		bindingName = binding.getName();
		soapVersion = binding.getProtocol().toString();

		if (!binding.getStyle().equalsIgnoreCase("Document/Literal")) {
			throw new UnSupportedWSDLException("Only Docuement/literal is supported");
		}

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

					if (op.getInput().getMessage().getParts().size() < 1) {
						LOGGER.warning("wsdl operation " + op.getName() + " has no parts.");
					} else if (op.getInput().getMessage().getParts().size() > 1) {
						LOGGER.warning(
								"wsdl operation " + op.getName() + " has > 1 part. This is not currently supported");
					} else {
						com.predic8.schema.Element requestElement = op.getInput().getMessage().getParts().get(0)
								.getElement();
						namespace = (Map<String, String>) requestElement.getNamespaceContext();
						if (verb.equalsIgnoreCase("GET")) {
							creator.setCreator(new RequestTemplateCreator());
							// use membrane SOAP to generate a SOAP Request
							creator.createRequest(port.getName(), op.getName(), binding.getName());
							// store the operation name, SOAP Request and the
							// expected JSON Body in the map
							KeyValue<String, String> kv = xmlUtils.replacePlaceHolders(writer.toString());
							apiMap = new APIMap(kv.getValue(), kv.getKey(), resourcePath, verb, false);
							writer.getBuffer().setLength(0);
						} else {
							String namespaceUri = null;
							if (requestElement.getType() != null) {
								namespaceUri = requestElement.getType().getNamespaceURI();
							} else {
								namespaceUri = requestElement.getEmbeddedType().getNamespaceUri();
							}
							String prefix = getPrefix(namespaceUri);
							xmlUtils.generateRootNamespaceXSLT(SOAP2API_XSLT_TEMPLATE, SOAP2API_XSL, op.getName(),
									prefix, namespaceUri);

							Schema s = requestElement.getSchema();
							for (ComplexType ct : s.getComplexTypes()) {
								SchemaComponent sc = ct.getModel();
								parseSchema(sc, wsdl.getSchemas(), requestElement.getName(),
										requestElement.getNamespaceUri());
							}
							if (ruleList.size() > 0) {
								RuleSet rs = new RuleSet();
								rs.addRuleList(ruleList);
								xmlUtils.generateOtherNamespacesXSLT(SOAP2API_XSL, op.getName(), rs.getTransform());
								ruleList.clear();
								apiMap = new APIMap("", "", resourcePath, verb, true);
							} else {
								apiMap = new APIMap("", "", resourcePath, verb, false);
							}
						}
						messageTemplates.put(op.getName(), apiMap);
					}
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
						if (bnd.getBinding() instanceof AbstractSOAPBinding) {
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
					File xsltFolder = new File(
							apiproxy.getAbsolutePath() + File.separator + "resources" + File.separator + "xsl");
					xsltFolder.mkdirs();
					SOAP2API_XSL = xsltFolder.getAbsolutePath() + File.separator;
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
		String zipFolder = null;
        Path tempDirectory = null;

		try {
            tempDirectory = Files.createTempDirectory(null);
            targetFolder = tempDirectory.toAbsolutePath().toString();
            zipFolder = targetFolder + File.separator + "apiproxy";
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
			LOGGER.severe(e.getMessage());
		} catch (TargetFolderException e) {
			LOGGER.severe(e.getMessage());
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
		}
        finally {
            if (tempDirectory != null) {
                try {
                    Files.delete(tempDirectory);
                } catch (IOException e) {
                    LOGGER.severe(e.getMessage());
                }
            }
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
			LOGGER.setLevel(Level.FINEST);
			handler.setLevel(Level.FINEST);
		}

		// genProxy.PASSTHRU = true;

		genProxy.begin(proxyDescription, wsdlPath);

	}
}
