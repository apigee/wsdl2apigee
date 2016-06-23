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

import java.io.*;
import java.nio.file.*;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
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

public class GenerateProxy {

	private static final Logger LOGGER = Logger.getLogger(GenerateProxy.class.getName());
	private static final ConsoleHandler handler = new ConsoleHandler();

	public static String OPSMAPPING_TEMPLATE = "/templates/opsmap/opsmapping.xml";

	private static String SOAP2API_XSL = "";
	
	private static final String SOAP2API_APIPROXY_TEMPLATE = "/templates/soap2api/apiProxyTemplate.xml";
	private static final String SOAP2API_PROXY_TEMPLATE = "/templates/soap2api/proxyDefault.xml";
	private static final String SOAP2API_TARGET_TEMPLATE = "/templates/soap2api/targetDefault.xml";
	private static final String SOAP2API_EXTRACT_TEMPLATE = "/templates/soap2api/ExtractPolicy.xml";
	private static final String SOAP2API_ASSIGN_TEMPLATE = "/templates/soap2api/AssignMessagePolicy.xml";
	private static final String SOAP2API_XSLTPOLICY_TEMPLATE = "/templates/soap2api/add-namespace.xml";
	private static final String SOAP2API_XSLT_TEMPLATE = "/templates/soap2api/add-namespace.xslt";
	private static final String SOAP2API_JSON_TO_XML_TEMPLATE = "/templates/soap2api/json-to-xml.xml";
	private static final String SOAP2API_ADD_SOAPACTION_TEMPLATE = "/templates/soap2api/add-soapaction.xml";

	
	private static final String SOAPPASSTHRU_APIPROXY_TEMPLATE = "/templates/soappassthru/apiProxyTemplate.xml";
	private static final String SOAPPASSTHRU_PROXY_TEMPLATE = "/templates/soappassthru/proxyDefault.xml";
	private static final String SOAPPASSTHRU_TARGET_TEMPLATE = "/templates/soappassthru/targetDefault.xml";

	private static final String SOAP11_CONTENT_TYPE = "text/xml; charset=utf-8";// "text&#x2F;xml;
																				// charset=utf-8";
	private static final String SOAP12_CONTENT_TYPE = "application/soap+xml";

	private static final String SOAP11_PAYLOAD_TYPE = "text/xml";// "text&#x2F;xml";
	private static final String SOAP12_PAYLOAD_TYPE = "application/soap+xml";

	private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
	private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

	// set this to true if SOAP passthru is needed
	private boolean PASSTHRU;
	//set this to true if all operations are to be consumed via POST verb
	private boolean ALLPOST;

	private String targetEndpoint;

	private String soapVersion;

	private String serviceName;

	private String portName;

	private String basePath;

	private String proxyName;
	
	private List<String> vHosts;

	// default build folder is ./build
	private String buildFolder;

	// Each row in this Map has the key as the operation name. The operation
	// name has SOAP Request
	// and JSON Equivalent of SOAP (without the SOAP Envelope) as values.
	// private Map<String, KeyValue<String, String>> messageTemplates;
	private Map<String, APIMap> messageTemplates;
	//tree to hold elements to build an xpath
	private HashMap<Integer, String>xpathElement;	
	//xpath tree element level
	private int level;	
	//List of rules to generate XSLT
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
		xpathElement = new HashMap<Integer, String>();
		
		vHosts = new ArrayList<String>();
		vHosts.add("default");
		
		buildFolder = null;
		soapVersion = "SOAP12";
		ALLPOST = false;
		PASSTHRU = false;
		level = 0;
	}
	
	public void setVHost (String vhosts) {
		if (vhosts.indexOf(",") != -1) {
			//contains > 1 vhosts
			vHosts = Arrays.asList(vhosts.split(","));
		}
		else {
			vHosts.remove(0);//remove default
			vHosts.add(vhosts);
		}
	}
	
	public void setAllPost (boolean allPost) {
		ALLPOST = allPost;
	}
	
	public void setBuildFolder(String folder) {
		buildFolder = folder;
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
				buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");
		LOGGER.fine(
				"Generated file: " + buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");
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
		
		Node httpProxyConnection = proxyDefault.getElementsByTagName("HTTPProxyConnection").item(0);
		Node virtualHost = null;
		for (String vHost : vHosts) {
			virtualHost = proxyDefault.createElement("VirtualHost");
			virtualHost.setTextContent(vHost);
			httpProxyConnection.appendChild(virtualHost);
		}
		
		Document apiTemplateDocument = xmlUtils
				.readXML(buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");

		Document extractTemplate = xmlUtils.readXML(SOAP2API_EXTRACT_TEMPLATE);

		Document assignTemplate = xmlUtils.readXML(SOAP2API_ASSIGN_TEMPLATE);

		Document addNamespaceTemplate = xmlUtils.readXML(SOAP2API_XSLTPOLICY_TEMPLATE);
		
		Document jsonXMLTemplate = xmlUtils.readXML(SOAP2API_JSON_TO_XML_TEMPLATE);
		
		Document addSoapActionTemplate = xmlUtils.readXML(SOAP2API_ADD_SOAPACTION_TEMPLATE);

		Node policies = apiTemplateDocument.getElementsByTagName("Policies").item(0);

		boolean addSoapWrapperPolicy = false;

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1, step2, step3, step4, step5;
		Node name1, name2, name3, name4, name5;

		for (Map.Entry<String, APIMap> entry : messageTemplates.entrySet()) {
			String operationName = entry.getKey();
			APIMap apiMap = entry.getValue();
			String buildSOAPPolicy = operationName + "-build-soap";
			String extractPolicyName = operationName + "-extract-query-param";
			String jsonToXML = operationName + "-json-to-xml";
			String soap12 = "add-soap12";
			String soap11 = "add-soap11";

			String httpVerb = apiMap.getVerb();
			String resourcePath = apiMap.getResourcePath();
			String Condition = "(proxy.pathsuffix MatchesPath \"" + resourcePath + "\") and (request.verb = \""
					+ httpVerb + "\")";
			String addSoapAction = operationName+"-add-soapaction";

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

			step4 = proxyDefault.createElement("Step");
			name4 = proxyDefault.createElement("Name");
			
			step5 = proxyDefault.createElement("Step");
			name5 = proxyDefault.createElement("Name");
			

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
				//TODO: add condition here to convert to XML only if Content-Type is json; 

				name2.setTextContent(operationName + "-add-namespace");
				step2.appendChild(name2);
				request.appendChild(step2);
				
				if (apiMap.getOthernamespaces()) {
					name4.setTextContent(operationName + "-add-other-namespaces");
					step4.appendChild(name4);
					request.appendChild(step4);
				}

				if (soapVersion.equalsIgnoreCase("SOAP12")) {
					name3.setTextContent(soap12);
					step3.appendChild(name3);
					request.appendChild(step3);
				} else {
					name3.setTextContent(soap11);
					step3.appendChild(name3);
					request.appendChild(step3);
				}
				//for soap 1.1 add soap action
				if (soapVersion.equalsIgnoreCase("SOAP11")) {
					step5 = proxyDefault.createElement("Step");
					name5 = proxyDefault.createElement("Name");
					name5.setTextContent(addSoapAction);
					step5.appendChild(name5);
					request.appendChild(step5);
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
				writeSOAP2APIAssignMessagePolicies(assignTemplate, operationName, buildSOAPPolicy, apiMap.getSoapAction());
				// write Extract Variable Policy
				writeSOAP2APIExtractPolicy(extractTemplate, operationName, extractPolicyName);
			} else {

				Node policy1 = apiTemplateDocument.createElement("Policy");
				policy1.setTextContent(jsonToXML);
				policies.appendChild(policy1);
				
				Node policy2 = apiTemplateDocument.createElement("Policy");
				if (soapVersion.equalsIgnoreCase("SOAP12")) {
					policy2.setTextContent(soap12);
				} else {
					policy2.setTextContent(soap11);
				}
				policies.appendChild(policy2);
				
				if (!addSoapWrapperPolicy) {
					addSOAPWrapper();
					addSoapWrapperPolicy = true;
				}
				
				writeJsonToXMLPolicy(jsonXMLTemplate, operationName, apiMap.getRootElement());

				Node policy3 = apiTemplateDocument.createElement("Policy");
				policy3.setTextContent(operationName + "add-namespace");
				policies.appendChild(policy3);

				if (apiMap.getOthernamespaces()) {
					Node policy4 = apiTemplateDocument.createElement("Policy");
					policy4.setTextContent(operationName + "add-other-namespaces");

					policies.appendChild(policy4);

					writeAddNamespace(addNamespaceTemplate, operationName, true);
				} else {
					writeAddNamespace(addNamespaceTemplate, operationName, false);
				}
				//for soap 1.1 add soap action
				if (soapVersion.equalsIgnoreCase("SOAP11")) {
					// Add policy to proxy.xml
					Node policy5 = apiTemplateDocument.createElement("Policy");
					policy5.setTextContent(addSoapAction);
					policies.appendChild(policy5);
					writeAddSoapAction(addSoapActionTemplate, operationName, apiMap.getSoapAction());
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

		LOGGER.fine("Edited proxy xml: " + buildFolder + File.separator + "apiproxy" + File.separator + proxyName
				+ ".xml");
		xmlUtils.writeXML(apiTemplateDocument,
				buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");

		xmlUtils.writeXML(proxyDefault, buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}
	
	private void writeAddSoapAction(Document addSoapActionTemplate, String operationName, String soapAction) throws Exception{
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			XMLUtils xmlUtils = new XMLUtils();
			String policyName = operationName + "-add-soapaction";
			Document soapActionPolicyXML = xmlUtils.cloneDocument(addSoapActionTemplate);

			Node rootElement = soapActionPolicyXML.getFirstChild();
			NamedNodeMap attr = rootElement.getAttributes();
			Node nodeAttr = attr.getNamedItem("name");
			nodeAttr.setNodeValue(policyName);

			Node displayName = soapActionPolicyXML.getElementsByTagName("DisplayName").item(0);
			displayName.setTextContent(operationName + " Add SOAPAction");
			
			Node header = soapActionPolicyXML.getElementsByTagName("Header").item(0);
			header.setTextContent(soapAction);
			
			xmlUtils.writeXML(soapActionPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator + policyName + ".xml");
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;			
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeAddNamespace(Document namespaceTemplate, String operationName, boolean addOtherNamespaces) throws Exception {

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

			xmlUtils.writeXML(xslPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator + policyName + ".xml");

			if (addOtherNamespaces) {
				String policyNameOther = operationName + "-add-other-namespaces";
				Document xslPolicyXMLOther = xmlUtils.cloneDocument(namespaceTemplate);

				Node rootElementOther = xslPolicyXMLOther.getFirstChild();
				NamedNodeMap attrOther = rootElementOther.getAttributes();
				Node nodeAttrOther = attrOther.getNamedItem("name");
				nodeAttrOther.setNodeValue(policyNameOther);

				Node displayNameOther = xslPolicyXMLOther.getElementsByTagName("DisplayName").item(0);
				displayNameOther.setTextContent(operationName + " Add Other Namespaces");

				Node resourceURLOther = xslPolicyXMLOther.getElementsByTagName("ResourceURL").item(0);
				resourceURLOther.setTextContent("xsl://" + policyNameOther + ".xslt");

				xmlUtils.writeXML(xslPolicyXMLOther, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
						+ File.separator + policyNameOther + ".xml");
			}

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

		xmlUtils.writeXML(extractPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator + policyName + ".xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAP2APIAssignMessagePolicies(Document assignTemplate, String operationName, String policyName, String soapAction)
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

			Node header = assignPolicyXML.getElementsByTagName("Header").item(0);
			header.setTextContent(soapAction);
		}

		APIMap apiMap = messageTemplates.get(operationName);
		Document operationPayload = xmlUtils.getXMLFromString(apiMap.getSoapBody());
		Node importedNode = assignPolicyXML.importNode(operationPayload.getDocumentElement(), true);
		payload.appendChild(importedNode);

		Node value = assignPolicyXML.getElementsByTagName("Value").item(0);
		value.setTextContent(targetEndpoint);

		LOGGER.fine("Generated resource xml: " + buildFolder + File.separator + "apiproxy" + File.separator
				+ "policies" + File.separator + policyName + ".xml");

		xmlUtils.writeXML(assignPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
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

		xmlUtils.writeXML(targetDefault, buildFolder + File.separator + "apiproxy" + File.separator + "targets"
				+ File.separator + "default.xml");
		LOGGER.info("Generated Target xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "targets"
				+ File.separator + "default.xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeJsonToXMLPolicy(Document jsonXMLTemplate, String operationName, String rootElement) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

			String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			
			XMLUtils xmlUtils = new XMLUtils();
			Document jsonxmlPolicyXML = xmlUtils.cloneDocument(jsonXMLTemplate);

			Node root= jsonxmlPolicyXML.getFirstChild();
			NamedNodeMap attr = root.getAttributes();
			Node nodeAttr = attr.getNamedItem("name");
			nodeAttr.setNodeValue(operationName+"-json-to-xml");
			
			Node displayName = jsonxmlPolicyXML.getElementsByTagName("DisplayName").item(0);
			displayName.setTextContent(operationName + " JSON TO XML");
			
			Node objectRootElement = jsonxmlPolicyXML.getElementsByTagName("ObjectRootElementName").item(0);
			objectRootElement.setTextContent(rootElement);
			
			xmlUtils.writeXML(jsonxmlPolicyXML, targetPath + operationName + "-json-to-xml.xml");

			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
	}
	
	private void addSOAPWrapper() throws Exception{

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String xslResourcePath = buildFolder + File.separator + "apiproxy" + File.separator + "resources"
				+ File.separator + "xsl" + File.separator;
		String sourcePath = "/templates/soap2api/";

		String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator;
		
		if (soapVersion.equalsIgnoreCase("SOAP12")) {
			Files.copy(getClass().getResourceAsStream(sourcePath + "add-soap12.xml"), 
					Paths.get(targetPath + "add-soap12.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(getClass().getResourceAsStream(sourcePath + "add-soap12.xslt"), 
					Paths.get(xslResourcePath + "add-soap12.xslt"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} else {
			Files.copy(getClass().getResourceAsStream(sourcePath + "add-soap11.xml"), 
					Paths.get(targetPath + "add-soap11.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(getClass().getResourceAsStream(sourcePath + "add-soap11.xslt"), 
					Paths.get(xslResourcePath + "add-soap11.xslt"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeStdPolicies() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			String sourcePath = "/templates/";
			String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			String xslResourcePath = buildFolder + File.separator + "apiproxy" + File.separator + "resources"
					+ File.separator + "xsl" + File.separator;
			LOGGER.fine("Source Path: " + sourcePath);
			LOGGER.fine("Target Path: " + targetPath);
			if (PASSTHRU) {
				sourcePath += "soappassthru/";
				Files.copy(getClass().getResourceAsStream(sourcePath + "Extract-Operation-Name.xml"),
						Paths.get(targetPath + "Extract-Operation-Name.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "Invalid-SOAP.xml"), 
						Paths.get(targetPath + "Invalid-SOAP.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} else {
				sourcePath += "soap2api" + File.separator;
				Files.copy(getClass().getResourceAsStream(sourcePath + "xml-to-json.xml"), 
						Paths.get(targetPath + "xml-to-json.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "set-response-soap-body.xml"),
						Paths.get(targetPath + "set-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "get-response-soap-body.xml"),
						Paths.get(targetPath + "get-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "set-target-url.xml"), 
						Paths.get(targetPath + "set-target-url.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "unknown-resource.xml"),
						Paths.get(targetPath + "unknown-resource.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "remove-empty-nodes.xml"),
						Paths.get(targetPath + "remove-empty-nodes.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "remove-empty-nodes.xslt"),
						Paths.get(xslResourcePath + "remove-empty-nodes.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "return-generic-error.xml"),
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
		
		Node httpProxyConnection = proxyDefault.getElementsByTagName("HTTPProxyConnection").item(0);
		Node virtualHost = null;
		for (String vHost : vHosts) {
			virtualHost = proxyDefault.createElement("VirtualHost");
			virtualHost.setTextContent(vHost);
			httpProxyConnection.appendChild(virtualHost);
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

			String operationName = entry.getKey();
			APIMap apiMap = entry.getValue();
			
			flow = proxyDefault.createElement("Flow");
			((Element) flow).setAttribute("name", operationName);

			flowDescription = proxyDefault.createElement("Description");
			flowDescription.setTextContent(operationName);
			flow.appendChild(flowDescription);

			request = proxyDefault.createElement("Request");
			response = proxyDefault.createElement("Response");
			condition = proxyDefault.createElement("Condition");
			condition.setTextContent(conditionText + apiMap.getRootElement() + "\")");

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

		xmlUtils.writeXML(proxyDefault, buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	public static String getPrefix(String namespaceUri) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(namespaceUri)) {
				if (entry.getKey().length() == 0) {
					return "ns";
				} else {
					return entry.getKey();
				}
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
				return entry.getValue();
			}
		}
		return "ns";
	}

    private void parseElement(com.predic8.schema.Element e, List<Schema> schemas, String rootElement, String rootNamespace, String rootPrefix) {
        if (e.getName() == null) {
            if (e.getRef() != null) {
                final String localPart = e.getRef().getLocalPart();
                final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
                parseSchema(element, schemas, rootElement, rootNamespace, rootPrefix);
            }
            else {
            	//TODO: handle this
            	LOGGER.warning("unhandled conditions getRef() = null");
            }
        }
        else {
            if (!e.getName().equalsIgnoreCase(rootElement)) {
                if (e.getEmbeddedType() instanceof ComplexType) {
                    ComplexType ct = (ComplexType)e.getEmbeddedType();
                    if (!e.getNamespaceUri().equalsIgnoreCase(rootNamespace)) {
                    	buildXPath(e, rootElement, rootNamespace, rootPrefix);
                    }
                    parseSchema(ct.getModel(), schemas, rootElement, rootNamespace, rootPrefix);
                } else {
                	if (e.getType() != null){
                		if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
    							&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
                			buildXPath(e, rootElement, rootNamespace, rootPrefix);
                        } 
                    	TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
                    	if (typeDefinition instanceof ComplexType) {
                    		parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace, rootPrefix);
                    	} 
                	}
                	else  {
                    	//handle this as anyType
						buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						}
                    	LOGGER.warning("Found element " + e.getName() + " with no type. Handling as xsd:anyType");
                    	
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
                	LOGGER.warning("unhandled conditions: " + e.getMessage());
                }
            }
        }
        return null;
    }

	private void parseSchema(SchemaComponent sc, List<Schema> schemas, String rootElement, String rootNamespace, String rootPrefix) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			level ++;
			for (com.predic8.schema.Element e : seq.getElements()) {
				//System.out.println(e.getName() + " - " + " " + e.getType());
				if (e.getName() == null) level --;
				if (e.getName() != null) {
					xpathElement.put(level, e.getName());
					if (e.getType() != null) {
						if (e.getType().getLocalPart().equalsIgnoreCase("anyType")) {
							//found a anyType. remove namespaces for descendents
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						}
					}
				}
				parseElement(e, schemas, rootElement, rootNamespace, rootPrefix);
			}
			level --;
			cleanUpXPath();
		} else if (sc instanceof Choice) {
			Choice ch = (Choice) sc;
			level ++;
			for (com.predic8.schema.Element e : ch.getElements()) {
				if (!e.getName().equalsIgnoreCase(rootElement)) {
					if (e.getEmbeddedType() instanceof ComplexType) {
						ComplexType ct = (ComplexType) e.getEmbeddedType();
						xpathElement.put(level, e.getName());
						parseSchema(ct.getModel(), schemas, rootElement, rootNamespace, rootPrefix);
					} else {
						final TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
						if (typeDefinition instanceof ComplexType) {
							xpathElement.put(level, e.getName());
							parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace, rootPrefix);
						} 
						if (e.getType() == null) {
							//handle this any anyType
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
							LOGGER.warning("Element "+e.getName()+" type was null; treating as anyType");
						} else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
								&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						} else if (e.getType().getLocalPart().equalsIgnoreCase("anyType")) {
							//if you find a anyType, remove namespace for the descendents.
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						}
					}
				}
			}
			level --;
			cleanUpXPath();
		} else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent) sc;
			Derivation derivation = complexContent.getDerivation();

			if (derivation != null) {
				TypeDefinition typeDefinition = getTypeFromSchema(derivation.getBase(), schemas);				
				if (typeDefinition instanceof ComplexType) {
					parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace, rootPrefix);
				}
				if (derivation.getModel() instanceof Sequence) {
					parseSchema(derivation.getModel(), schemas, rootElement, rootNamespace, rootPrefix);
				} else if (derivation.getModel() instanceof ModelGroup) {
					parseSchema(derivation.getModel(), schemas, rootElement, rootNamespace, rootPrefix);
				} 
			} 
		} else if (sc instanceof SimpleContent) {
			SimpleContent simpleContent = (SimpleContent) sc;
			Derivation derivation = (Derivation) simpleContent.getDerivation();

			if (derivation.getAllAttributes().size() > 0) {
				//has attributes
				buildXPath(derivation.getNamespaceUri(), rootElement, rootNamespace, rootPrefix);
			} 
		} else if (sc instanceof com.predic8.schema.Element) {
			level ++;
			xpathElement.put(level, ((com.predic8.schema.Element)sc).getName());
            parseElement((com.predic8.schema.Element) sc, schemas, rootElement, rootNamespace, rootPrefix);
        } else if (sc != null) {
			// TODO: handle this
			LOGGER.warning("unhandled conditions - " + sc.getClass().getName());
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

	private void cleanUpXPath () {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Integer key : xpathElement.keySet()) {
			if (key > level)
				xpathElement.remove(key);
		}		
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}
	
	private void buildXPath (com.predic8.schema.Element e, String rootElement, String rootNamespace, String rootPrefix, boolean removeNamespace) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		Rule r = null;
		String xpathString = "";
		String prefix = "NULL";
		String namespaceUri = "NULL";
		String lastElement = "";
		
		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
			lastElement = entry.getValue();
		}
		
		//add the last element to xpath
		if (!lastElement.equalsIgnoreCase(e.getName()))
			xpathString = xpathString +"/" + rootPrefix + ":" + e.getName();

		r = new Rule(xpathString, prefix, namespaceUri, "descendant");
		ruleList.add(r);		
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		
	}
	
	private void buildXPath (com.predic8.schema.Element e, String rootElement, String rootNamespace, String rootPrefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		Rule r = null;
		String xpathString = "";
		String prefix = getPrefix(e.getNamespaceUri());
		String namespaceUri = e.getNamespaceUri();
		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
		}

		r = new Rule(xpathString, prefix, namespaceUri);
		ruleList.add(r);
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}	
	
	private void buildXPath(String namespaceUri, String rootElement, String rootNamespace, String rootPrefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		Rule r = null;
		String prefix = getPrefix(namespaceUri);
		String xpathString = "";
		
		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
		}		
		
		r = new Rule(xpathString+"/@*", prefix, namespaceUri);
		ruleList.add(r);

		cleanUpXPath();
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}	
	
	private String getParentNamepace(com.predic8.schema.Element e) {
		XMLElement parent = e.getParent();
		
		try {
			return parent.getNamespaceUri();
		} catch (NullPointerException npe) {
			if (e.getNamespaceUri() != null)
				return e.getNamespaceUri();
			else 
				return null;
		}
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

		if (!binding.getStyle().contains("Document/Literal")) {
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
				
				if (op.getInput().getMessage().getParts().size() < 1) {
					LOGGER.warning("wsdl operation " + op.getName() + " has no parts.");
				} else if (op.getInput().getMessage().getParts().size() > 1) {
					LOGGER.warning(
							"wsdl operation " + op.getName() + " has > 1 part. This is not currently supported");
				} else {
					com.predic8.schema.Element requestElement = op.getInput().getMessage().getParts().get(0)
							.getElement();
					namespace = (Map<String, String>) requestElement.getNamespaceContext();
					APIMap apiMap = null;
					
					if (PASSTHRU) {
						apiMap = new APIMap(null, null , null, "POST", requestElement.getName(), false);
						messageTemplates.put(op.getName(), apiMap);
					} else {
						String resourcePath = OpsMap.getResourcePath(op.getName());
						String verb = "";
						
						if (!ALLPOST) {
							verb = OpsMap.getOpsMap(op.getName());
						} else {
							verb = "POST";
						}
						if (verb.equalsIgnoreCase("GET")) {
							creator.setCreator(new RequestTemplateCreator());
							// use membrane SOAP to generate a SOAP Request
							creator.createRequest(port.getName(), op.getName(), binding.getName());
							// store the operation name, SOAP Request and the
							// expected JSON Body in the map
							KeyValue<String, String> kv = xmlUtils.replacePlaceHolders(writer.toString());
							apiMap = new APIMap(kv.getValue(), kv.getKey(), resourcePath, verb, requestElement.getName(), false);
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

			        		TypeDefinition typeDefinition = null;
			        		
			        		if ( requestElement.getEmbeddedType() != null) {
			        			typeDefinition = requestElement.getEmbeddedType();
			        		} else {
			        			typeDefinition = getTypeFromSchema(requestElement.getType(), wsdl.getSchemas());
			        		}
			        		if (typeDefinition instanceof ComplexType) {
			        			ComplexType ct = (ComplexType)typeDefinition;
			            		xpathElement.put(level, requestElement.getName());        			
			            		parseSchema(ct.getModel(), wsdl.getSchemas(), requestElement.getName(), 
			            				namespaceUri, prefix);
			        		}
							if (ruleList.size() > 0) {
								RuleSet rs = new RuleSet();
								rs.addRuleList(ruleList);
								xmlUtils.generateOtherNamespacesXSLT(SOAP2API_XSL, op.getName(), rs.getTransform());
								ruleList.clear();
								apiMap = new APIMap("", "", resourcePath, verb, requestElement.getName(), true);
							} else {
								apiMap = new APIMap("", "", resourcePath, verb, requestElement.getName(), false);
							}
						}
						messageTemplates.put(op.getName(), apiMap);						
					}
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
		File f = new File(buildFolder);

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

	public InputStream begin(String proxyDescription, String wsdlPath) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		LOGGER.fine("Preparing target folder");
		String zipFolder = null;
        Path tempDirectory = null;
        InputStream is = null;

		try {
			if (buildFolder == null ) {
	            tempDirectory = Files.createTempDirectory(null);
	            buildFolder = tempDirectory.toAbsolutePath().toString();
			}
            zipFolder = buildFolder + File.separator + "apiproxy";
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
				LOGGER.info("Base Path: " + basePath + "\nWSDL Path: " + wsdlPath);
				LOGGER.info("Build Folder: " + buildFolder + "\nSOAP Version: " + soapVersion);
				LOGGER.info("Proxy Name: " + proxyName + "\nProxy Description: " + proxyDescription);


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

				File file = GenerateBundle.build(zipFolder, proxyName);
				LOGGER.info("Generated Apigee Edge API Bundle file: " + proxyName + ".zip");
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
                return new ByteArrayInputStream(Files.readAllBytes(file.toPath()));
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
                    Files.walkFileTree(tempDirectory, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }

                    });
                } catch (IOException e) {
                    LOGGER.severe(e.getMessage());
                }
            }
        }
        return is;
	}

	public static void usage() {
		System.out.println("");
		System.out.println("Usage: java -jar wsdl2apigee.jar -wsdl={url or path to wsdl} <options>");
		System.out.println("");
		System.out.println("Options:");
		System.out.println("-passthru=<true|false>    default is false;");
		System.out.println("-desc=\"description for proxy\"");
		System.out.println("-service=servicename      if wsdl has > 1 service, enter service name");
		System.out.println("-port=portname            if service has > 1 port, enter port name");
		System.out.println("-opsmap=opsmapping.xml    mapping file that to map wsdl operation to http verb");
		System.out.println("-allpost=<true|false>     set to true if all operations are http verb; default is false");
		System.out.println("-vhosts=<comma separated values for virtuals hosts>");
		System.out.println("-build=specify build folder   default is temp/tmp");		
		System.out.println("-debug=<true|false>       default is false");
		System.out.println("");
		System.out.println("");
		System.out.println("Examples:");
		System.out.println("$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\"");
		System.out.println("$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\" -passthru=true");
		System.out.println("$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\" -vhosts=secure");
		System.out.println("");
		System.out.println("OpsMap:");
		System.out.println("A file that maps WSDL operations to HTTP Verbs. A Sample Ops Mapping file looks like:");
		System.out.println("");
		System.out.println("\t<proxywriter>");
		System.out.println("\t\t<get>");
		System.out.println("\t\t\t<name location=\"beginsWith\">get</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">list</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">inq</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">search</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">retrieve</name>");
		System.out.println("\t\t</get>");
		System.out.println("\t\t<post>");
		System.out.println("\t\t\t<name location=\"contains\">create</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">add</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">process</name>");
		System.out.println("\t\t</post>");
		System.out.println("\t\t<put>");
		System.out.println("\t\t\t<name location=\"contains\">update</name>");
		System.out.println("\t\t\t<name location=\"contains\">change</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">modify</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">set</name>");
		System.out.println("\t\t</put>");
		System.out.println("\t\t<delete>");
		System.out.println("\t\t\t<name location=\"beginsWith\">delete</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">remove</name>");
		System.out.println("\t\t\t<name location=\"beginsWith\">del</name>");
		System.out.println("\t\t</delete>");
		System.out.println("\t</proxywriter>");
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
		// set this flag to specify operations map
		opt.getSet().addOption("opsmap", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to handle all operations via post verb
		opt.getSet().addOption("allpost", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		//set virtual hosts
		opt.getSet().addOption("vhosts", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		//set build path
		opt.getSet().addOption("build", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set this flag to enable debug
		opt.getSet().addOption("debug", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);

		opt.check();

		if (opt.getSet().isSet("wsdl")) {
			// React to option -wsdl
			wsdlPath = opt.getSet().getOption("wsdl").getResultValue(0);
		} else {
			System.out.println("-wsdl is a madatory parameter");
			usage();
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
		
		if (opt.getSet().isSet("opsmap")) {
			GenerateProxy.OPSMAPPING_TEMPLATE = opt.getSet().getOption("opsmap").getResultValue(0);
		}
		
		if (opt.getSet().isSet("allpost")) {
			genProxy.setAllPost(new Boolean(opt.getSet().getOption("allpost").getResultValue(0)));
		}		
		
		if (opt.getSet().isSet("vhosts")) {
			genProxy.setVHost(opt.getSet().getOption("vhosts").getResultValue(0));
		} 
		
		if (opt.getSet().isSet("build")) {
			genProxy.setBuildFolder(opt.getSet().getOption("build").getResultValue(0));
		} 		

		if (opt.getSet().isSet("debug")) {
			// enable debug
			LOGGER.setLevel(Level.FINEST);
			handler.setLevel(Level.FINEST);
		} else {
			LOGGER.setLevel(Level.INFO);
			handler.setLevel(Level.INFO);
		}

        final InputStream begin = genProxy.begin(proxyDescription, wsdlPath);
        if (begin != null) {
            Files.copy(begin, new File(genProxy.proxyName + ".zip").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

    }

    public static InputStream generateProxy(String wsdl, boolean passthrough, String description) throws FileNotFoundException {
        GenerateProxy genProxy = new GenerateProxy();
        genProxy.setPassThru(passthrough);
        return genProxy.begin(description != null ? description : "Generated SOAP to API proxy", wsdl);
    }
}
