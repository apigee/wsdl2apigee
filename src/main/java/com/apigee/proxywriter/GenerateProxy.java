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

import com.apigee.oas.OASUtils;
import com.apigee.proxywriter.exception.*;
import com.apigee.utils.*;
import com.google.gson.*;
import com.predic8.soamodel.*;
import com.predic8.util.HTTPUtil;
import com.predic8.wsdl.*;
import com.predic8.wsdl.Operation;
import com.predic8.wsi.WSIResult;
import com.predic8.xml.util.ExternalResolver;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.apigee.utils.Options.Multiplicity;
import com.apigee.utils.Options.Separator;
import com.apigee.xsltgen.Rule;
import com.apigee.xsltgen.RuleSet;
import com.predic8.schema.All;
import com.predic8.schema.Attribute;
import com.predic8.schema.BuiltInSchemaType;
import com.predic8.schema.Choice;
import com.predic8.schema.ComplexContent;
import com.predic8.schema.ComplexType;
import com.predic8.schema.Derivation;
import com.predic8.schema.GroupRef;
import com.predic8.schema.ModelGroup;
import com.predic8.schema.Schema;
import com.predic8.schema.SchemaComponent;
import com.predic8.schema.Sequence;
import com.predic8.schema.SimpleContent;
import com.predic8.schema.TypeDefinition;
import com.predic8.wstool.creator.RequestTemplateCreator;
import com.predic8.wstool.creator.SOARequestCreator;

import groovy.xml.MarkupBuilder;
import groovy.xml.QName;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class GenerateProxy {

	private static final Logger LOGGER = Logger.getLogger(GenerateProxy.class.getName());
	private static final ConsoleHandler handler = new ConsoleHandler();
	public static final String SOAP_11 = "SOAP11";
	public static final String SOAP_12 = "SOAP12";

	public static String OPSMAPPING_TEMPLATE = "/templates/opsmap/opsmapping.xml";//"src/main/resources/templates/opsmap/opsmapping.xml";

	private static String SOAP2API_XSL = "";

	private static final List<String> primitiveTypes = Arrays.asList(new String[] { "int", "string", "boolean",
			"decimal", "float", "double", "duration", "dateTime", "time", "date", "long", "gYearMonth", "gYear",
			"gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION" });
	private final String soap11Namespace = " xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ";
			//" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ";

	private final String soap12Namespace = " xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ";

	private static final List<String> blackListedNamespaces = Arrays.asList("http://schemas.xmlsoap.org/wsdl/",
			"http://schemas.xmlsoap.org/wsdl/soap/");

	private static final String emptySoap12 = "<?xml version=\"1.0\"?><soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope/\" soapenv:encodingStyle=\"http://www.w3.org/2003/05/soap-encoding\"><soapenv:Header/><!-- THERE WAS A PROBLEM GENERATING THE TEMPLATE. PLEASE CREATE THE REQUEST MANUALLY --><soapenv:Body/></soapenv:Envelope>";

	private static final String emptySoap11 = "<?xml version=\"1.0\"?><soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" soapenv:encodingStyle=\"http://www.w3.org/2003/05/soap-encoding\"><soapenv:Header/><!-- THERE WAS A PROBLEM GENERATING THE TEMPLATE. PLEASE CREATE THE REQUEST MANUALLY --><soapenv:Body/></soapenv:Envelope>";

	private static final String SOAP2API_APIPROXY_TEMPLATE = "/templates/soap2api/apiProxyTemplate.xml";
	private static final String SOAP2API_PROXY_TEMPLATE = "/templates/soap2api/proxyDefault.xml";
	private static final String SOAP2API_TARGET_TEMPLATE = "/templates/soap2api/targetDefault.xml";
	private static final String SOAP2API_EXTRACT_TEMPLATE = "/templates/soap2api/ExtractPolicy.xml";
	private static final String SOAP2API_ASSIGN_TEMPLATE = "/templates/soap2api/AssignMessagePolicy.xml";
	private static final String SOAP2API_XSLT11POLICY_TEMPLATE = "/templates/soap2api/add-namespace11.xml";
	private static final String SOAP2API_XSLT11_TEMPLATE = "/templates/soap2api/add-namespace11.xslt";
	private static final String SOAP2API_XSLT12POLICY_TEMPLATE = "/templates/soap2api/add-namespace12.xml";
	private static final String SOAP2API_XSLT12_TEMPLATE = "/templates/soap2api/add-namespace12.xslt";
	private static final String SOAP2API_JSON_TO_XML_TEMPLATE = "/templates/soap2api/json-to-xml.xml";
	private static final String SOAP2API_ADD_SOAPACTION_TEMPLATE = "/templates/soap2api/add-soapaction.xml";
	// open-api feature
	private static final String SOAP2API_RETURN_OPENAPI_TEMPLATE = "/templates/soap2api/return-open-api.xml";
	// private static final String SOAP2API_JSPOLICY_TEMPLATE =
	// "/templates/soap2api/root-wrapper.xml";

	private static final String SOAPPASSTHRU_APIPROXY_TEMPLATE = "/templates/soappassthru/apiProxyTemplate.xml";
	private static final String SOAPPASSTHRU_PROXY_TEMPLATE = "/templates/soappassthru/proxyDefault.xml";
	private static final String SOAPPASSTHRU_TARGET_TEMPLATE = "/templates/soappassthru/targetDefault.xml";
	private static final String SOAPPASSTHRU_GETWSDL_TEMPLATE = "/templates/soappassthru/Return-WSDL.xml";

	private static final String OAS_TEMPLATE = "/templates/oas/oastemplate.json";

	private static final String SOAP11_CONTENT_TYPE = "text/xml; charset=utf-8";// "text&#x2F;xml;
																				// charset=utf-8";
	private static final String SOAP12_CONTENT_TYPE = "application/soap+xml";

	private static final String SOAP11_PAYLOAD_TYPE = "text/xml";// "text&#x2F;xml";
	private static final String SOAP12_PAYLOAD_TYPE = "application/soap+xml";

	private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
	private static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

	// set this to true if SOAP passthru is needed
	private boolean PASSTHRU;
	// set this to true if all operations are to be consumed via POST verb
	private boolean ALLPOST;
	// set this to true if oauth should be added to the proxy
	private boolean OAUTH;
	// set this to true if apikey should be added to the proxy
	private boolean APIKEY;
	// enable this flag if api key based quota is enabled
	private boolean QUOTAAPIKEY;
	// enable this flag if oauth based quota is enabled
	private boolean QUOTAOAUTH;
	// enable this flag is cors is enabled
	private boolean CORS;
	// enable this flag if wsdl is of rpc style
	private boolean RPCSTYLE;
	// enable this flag if user sets desc
	private boolean DESCSET;
	// fail safe measure when schemas are heavily nested or have 100s of
	// elements
	private boolean TOO_MANY;

	private String targetEndpoint;

	private String soapVersion;

	private String serviceName;

	private String portName;

	private String basePath;

	private String proxyName;

	private String opsMap;

	private String wsdlContent;

	private String selectedOperationsJson;

	private List<String> vHosts;

	private SelectedOperations selectedOperations;

	private OpsMap operationsMap;

	// default build folder is ./build
	private String buildFolder;

	// Each row in this Map has the key as the operation name. The operation
	// name has SOAP Request
	// and JSON Equivalent of SOAP (without the SOAP Envelope) as values.
	// private Map<String, KeyValue<String, String>> messageTemplates;
	private Map<String, APIMap> messageTemplates;
	// tree to hold elements to build an xpath
	private HashMap<Integer, String> xpathElement;
	// xpath tree element level
	private int level;
	// List of rules to generate XSLT
	private ArrayList<Rule> ruleList = new ArrayList<Rule>();

	private Definitions wsdl = null;

	private com.predic8.wsdl.Port port = null;

	public Map<String, String> namespace = new LinkedHashMap<String, String>();

	// open-api feature
	private JsonObject definitions;
	private String oasContent;
	// store openapi query params
	private ArrayList<String> queryParams;

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

	public GenerateProxy() {
		// initialize hashmap
		messageTemplates = new HashMap<String, APIMap>();
		xpathElement = new HashMap<Integer, String>();
		operationsMap = new OpsMap();
		selectedOperations = new SelectedOperations();
		vHosts = new ArrayList<String>();
		// open-api feature
		queryParams = new ArrayList<String>();

		vHosts.add("default");

		buildFolder = null;
		soapVersion = SOAP_12;
		ALLPOST = false;
		PASSTHRU = false;
		OAUTH = false;
		APIKEY = false;
		QUOTAAPIKEY = false;
		QUOTAOAUTH = false;
		CORS = false;
		RPCSTYLE = false;
		DESCSET = false;
		basePath = null;
		TOO_MANY = false;
		level = 0;
	}

	public void setSelectedOperationsJson(String json) throws Exception {
		selectedOperations.parseSelectedOperations(json);
	}

	public void setDesc(boolean descset) {
		DESCSET = descset;
	}

	public void setCORS(boolean cors) {
		CORS = cors;
	}

	public void setBasePath(String bp) {
		basePath = bp;
	}

	public void setQuotaAPIKey(boolean quotaAPIKey) {
		QUOTAAPIKEY = quotaAPIKey;
	}

	public void setQuotaOAuth(boolean quotaOAuth) {
		QUOTAOAUTH = quotaOAuth;
	}

	public void setAPIKey(boolean apikey) {
		APIKEY = apikey;
	}

	public void setOpsMap(String oMap) {
		opsMap = oMap;
	}

	public void setVHost(String vhosts) {
		if (vhosts.indexOf(",") != -1) {
			// contains > 1 vhosts
			vHosts = Arrays.asList(vhosts.split(","));
		} else {
			vHosts.remove(0);// remove default
			vHosts.add(vhosts);
		}
	}

	public void setAllPost(boolean allPost) {
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

	public void setOAuth(boolean oauth) {
		OAUTH = oauth;
	}

	public String getTargetEndpoint() {
		return targetEndpoint;
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

	private void writeSOAP2APIProxyEndpoint(String proxyDescription) throws Exception {

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

		Document returnOASTemplate = xmlUtils.readXML(SOAP2API_RETURN_OPENAPI_TEMPLATE);

		// Document jsPolicyTemplate =
		// xmlUtils.readXML(SOAP2API_JSPOLICY_TEMPLATE);

		Document addNamespaceTemplate = null;
		if (soapVersion.equalsIgnoreCase(SOAP_11)) {
			addNamespaceTemplate = xmlUtils.readXML(SOAP2API_XSLT11POLICY_TEMPLATE);
		} else {
			addNamespaceTemplate = xmlUtils.readXML(SOAP2API_XSLT12POLICY_TEMPLATE);
		}

		Document jsonXMLTemplate = xmlUtils.readXML(SOAP2API_JSON_TO_XML_TEMPLATE);

		Document addSoapActionTemplate = xmlUtils.readXML(SOAP2API_ADD_SOAPACTION_TEMPLATE);

		Node description = proxyDefault.getElementsByTagName("Description").item(0);
		description.setTextContent(proxyDescription);

		Node policies = apiTemplateDocument.getElementsByTagName("Policies").item(0);
		Node resources = apiTemplateDocument.getElementsByTagName("Resources").item(0);

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition, condition2;
		Node step1, step2, step3, step4, step5;
		Node name1, name2, name3, name4, name5;
		boolean once = false;

		// add oauth policies if set
		if (OAUTH) {
			String oauthPolicy = "verify-oauth-v2-access-token";
			String remoOAuthPolicy = "remove-header-authorization";
			String quota = "impose-quota-oauth";

			// Add policy to proxy.xml
			Node policy1 = apiTemplateDocument.createElement("Policy");
			policy1.setTextContent(oauthPolicy);

			Node policy2 = apiTemplateDocument.createElement("Policy");
			policy2.setTextContent(remoOAuthPolicy);

			policies.appendChild(policy1);
			policies.appendChild(policy2);

			Node preFlowRequest = proxyDefault.getElementsByTagName("PreFlow").item(0).getChildNodes().item(1);

			step1 = proxyDefault.createElement("Step");
			name1 = proxyDefault.createElement("Name");
			name1.setTextContent(oauthPolicy);
			step1.appendChild(name1);

			step2 = proxyDefault.createElement("Step");
			name2 = proxyDefault.createElement("Name");
			name2.setTextContent(remoOAuthPolicy);
			step2.appendChild(name2);

			preFlowRequest.appendChild(step1);
			preFlowRequest.appendChild(step2);

			if (QUOTAOAUTH) {
				Node policy3 = apiTemplateDocument.createElement("Policy");
				policy2.setTextContent(quota);
				policies.appendChild(policy3);
				step3 = proxyDefault.createElement("Step");
				name3 = proxyDefault.createElement("Name");
				name3.setTextContent(quota);
				step3.appendChild(name3);
				preFlowRequest.appendChild(step3);
			}
		}

		if (APIKEY) {
			String apiKeyPolicy = "verify-api-key";
			String remoAPIKeyPolicy = "remove-query-param-apikey";
			String quota = "impose-quota-apikey";

			// Add policy to proxy.xml
			Node policy1 = apiTemplateDocument.createElement("Policy");
			policy1.setTextContent(apiKeyPolicy);

			Node policy2 = apiTemplateDocument.createElement("Policy");
			policy2.setTextContent(remoAPIKeyPolicy);

			policies.appendChild(policy1);
			policies.appendChild(policy2);

			Node preFlowRequest = proxyDefault.getElementsByTagName("PreFlow").item(0).getChildNodes().item(1);

			step1 = proxyDefault.createElement("Step");
			name1 = proxyDefault.createElement("Name");
			name1.setTextContent(apiKeyPolicy);
			step1.appendChild(name1);

			step2 = proxyDefault.createElement("Step");
			name2 = proxyDefault.createElement("Name");
			name2.setTextContent(remoAPIKeyPolicy);
			step2.appendChild(name2);

			preFlowRequest.appendChild(step1);
			preFlowRequest.appendChild(step2);

			if (QUOTAAPIKEY) {
				Node policy3 = apiTemplateDocument.createElement("Policy");
				policy2.setTextContent(quota);
				policies.appendChild(policy3);
				step3 = proxyDefault.createElement("Step");
				name3 = proxyDefault.createElement("Name");
				name3.setTextContent(quota);
				step3.appendChild(name3);
				preFlowRequest.appendChild(step3);
			}

		}

		if (CORS) {
			Node proxyEndpoint = proxyDefault.getElementsByTagName("ProxyEndpoint").item(0);
			Node routeRule = proxyDefault.createElement("RouteRule");
			((Element) routeRule).setAttribute("name", "NoRoute");

			Node routeCondition = proxyDefault.createElement("Condition");
			routeCondition.setTextContent("request.verb == \"OPTIONS\"");
			routeRule.appendChild(routeCondition);
			proxyEndpoint.appendChild(routeRule);

			String cors = "add-cors";
			String corsCondition = "request.verb == \"OPTIONS\"";

			// Add policy to proxy.xml
			Node policy1 = apiTemplateDocument.createElement("Policy");
			policy1.setTextContent(cors);
			policies.appendChild(policy1);

			flow = proxyDefault.createElement("Flow");
			((Element) flow).setAttribute("name", "OptionsPreFlight");

			flowDescription = proxyDefault.createElement("Description");
			flowDescription.setTextContent("OptionsPreFlight");
			flow.appendChild(flowDescription);

			request = proxyDefault.createElement("Request");
			response = proxyDefault.createElement("Response");
			condition = proxyDefault.createElement("Condition");

			step1 = proxyDefault.createElement("Step");
			name1 = proxyDefault.createElement("Name");

			name1.setTextContent(cors);
			step1.appendChild(name1);

			response.appendChild(step1);

			condition.setTextContent(corsCondition);

			flow.appendChild(request);
			flow.appendChild(response);
			flow.appendChild(condition);

			flows.appendChild(flow);
		}

		// open-api feature
		flow = proxyDefault.createElement("Flow");
		((Element) flow).setAttribute("name", "GetOAS");
		flowDescription = proxyDefault.createElement("Description");
		flowDescription.setTextContent("Get OpenAPI Specification");
		flow.appendChild(flowDescription);
		request = proxyDefault.createElement("Request");
		response = proxyDefault.createElement("Response");
		condition = proxyDefault.createElement("Condition");
		condition.setTextContent("(proxy.pathsuffix MatchesPath \"/openapi.json\") and (request.verb = \"GET\")");
		step1 = proxyDefault.createElement("Step");
		name1 = proxyDefault.createElement("Name");
		name1.setTextContent("return-open-api");
		step1.appendChild(name1);
		request.appendChild(step1);
		flow.appendChild(request);
		flow.appendChild(response);
		flow.appendChild(condition);
		flows.appendChild(flow);

		writeOAS(returnOASTemplate);

		for (Map.Entry<String, APIMap> entry : messageTemplates.entrySet()) {
			String operationName = entry.getKey();
			APIMap apiMap = entry.getValue();
			String buildSOAPPolicy = operationName + "-build-soap";
			String extractPolicyName = operationName + "-extract-query-param";
			String jsonToXML = operationName + "-json-to-xml";
			// String jsPolicyName = operationName + "-root-wrapper";
			String jsonToXMLCondition = "(request.header.Content-Type == \"application/json\")";

			String httpVerb = apiMap.getVerb();
			String resourcePath = apiMap.getResourcePath();
			String Condition = "(proxy.pathsuffix MatchesPath \"" + resourcePath + "\") and (request.verb = \""
					+ httpVerb + "\")";
			String addSoapAction = operationName + "-add-soapaction";

			flow = proxyDefault.createElement("Flow");
			((Element) flow).setAttribute("name", operationName);

			flowDescription = proxyDefault.createElement("Description");
			flowDescription.setTextContent(operationName);
			flow.appendChild(flowDescription);

			request = proxyDefault.createElement("Request");
			response = proxyDefault.createElement("Response");
			condition = proxyDefault.createElement("Condition");
			condition2 = proxyDefault.createElement("Condition");
			condition2.setTextContent(jsonToXMLCondition);

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
				if (apiMap.getJsonBody() != null) {
					name1.setTextContent(extractPolicyName);
					step1.appendChild(name1);
					request.appendChild(step1);
				}

				step2 = proxyDefault.createElement("Step");
				name2 = proxyDefault.createElement("Name");
				name2.setTextContent(buildSOAPPolicy);
				step2.appendChild(name2);
				request.appendChild(step2);

				if (apiMap.getJsonBody() != null) {
					step3 = proxyDefault.createElement("Step");
					name3 = proxyDefault.createElement("Name");
					name3.setTextContent("remove-empty-nodes");
					Node condition3 = proxyDefault.createElement("Condition");
					condition3.setTextContent("(verb == \"GET\")");
					step3.appendChild(name3);
					step3.appendChild(condition3);
					request.appendChild(step3);
				}

				LOGGER.fine("Assign Message: " + buildSOAPPolicy);
				LOGGER.fine("Extract Variable: " + extractPolicyName);

			} else {
				// add root wrapper policy
				/*
				 * name3.setTextContent(jsPolicyName); step3.appendChild(name3);
				 * step3.appendChild(condition2.cloneNode(true));
				 * request.appendChild(step3);
				 */

				// add the root wrapper only once
				/*
				 * if (!once) { Node resourceRootWrapper =
				 * apiTemplateDocument.createElement("Resource");
				 * resourceRootWrapper.setTextContent("jsc://root-wrapper.js");
				 * resources.appendChild(resourceRootWrapper); once = true; }
				 */

				name1.setTextContent(jsonToXML);
				step1.appendChild(name1);
				step1.appendChild(condition2);
				request.appendChild(step1);
				// TODO: add condition here to convert to XML only if
				// Content-Type is json;

				name2.setTextContent(operationName + "-add-namespace");
				step2.appendChild(name2);
				request.appendChild(step2);

				if (apiMap.getOthernamespaces()) {
					name4.setTextContent(operationName + "-add-other-namespaces");
					step4.appendChild(name4);
					request.appendChild(step4);
				}

				// for soap 1.1 add soap action
				if (soapVersion.equalsIgnoreCase(SOAP_11)) {
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
				if (apiMap.getJsonBody() != null) {
					Node policy1 = apiTemplateDocument.createElement("Policy");
					policy1.setTextContent(extractPolicyName);
					policies.appendChild(policy1);
					// write Extract Variable Policy
					writeSOAP2APIExtractPolicy(extractTemplate, operationName, extractPolicyName);
				}
				Node policy2 = apiTemplateDocument.createElement("Policy");
				policy2.setTextContent(buildSOAPPolicy);
				policies.appendChild(policy2);
				// write Assign Message Policy
				writeSOAP2APIAssignMessagePolicies(assignTemplate, operationName, buildSOAPPolicy,
						apiMap.getSoapAction());
			} else {

				/*
				 * Node policy2 = apiTemplateDocument.createElement("Policy");
				 * policy2.setTextContent(jsPolicyName);
				 * policies.appendChild(policy2);
				 */

				// writeRootWrapper(jsPolicyTemplate, operationName,
				// apiMap.getRootElement());

				Node policy1 = apiTemplateDocument.createElement("Policy");
				policy1.setTextContent(jsonToXML);
				policies.appendChild(policy1);

				writeJsonToXMLPolicy(jsonXMLTemplate, operationName, apiMap.getRootElement());

				Node policy3 = apiTemplateDocument.createElement("Policy");
				policy3.setTextContent(operationName + "add-namespace");
				policies.appendChild(policy3);
				Node resourceAddNamespaces = apiTemplateDocument.createElement("Resource");
				resourceAddNamespaces.setTextContent("xsl://" + operationName + "add-namespace.xslt");
				resources.appendChild(resourceAddNamespaces);

				if (apiMap.getOthernamespaces()) {
					Node policy4 = apiTemplateDocument.createElement("Policy");
					policy4.setTextContent(operationName + "add-other-namespaces");

					policies.appendChild(policy4);

					Node resourceAddOtherNamespaces = apiTemplateDocument.createElement("Resource");
					resourceAddOtherNamespaces.setTextContent("xsl://" + operationName + "add-other-namespaces.xslt");
					resources.appendChild(resourceAddOtherNamespaces);

					writeAddNamespace(addNamespaceTemplate, operationName, true);
				} else {
					writeAddNamespace(addNamespaceTemplate, operationName, false);
				}
				// for soap 1.1 add soap action
				if (soapVersion.equalsIgnoreCase(SOAP_11)) {
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

		Node conditionA = proxyDefault.createElement("Condition");
		conditionA.setTextContent(
				"(verb != \"GET\" AND contentformat == \"application/json\") OR (verb == \"GET\" AND acceptformat !~ \"*/xml\")");
		Node conditionB = proxyDefault.createElement("Condition");
		conditionB.setTextContent(
				"(verb != \"GET\" AND contentformat != \"application/json\") OR (verb == \"GET\" AND acceptformat ~ \"*/xml\")");

		step1 = proxyDefault.createElement("Step");
		name1 = proxyDefault.createElement("Name");
		name1.setTextContent("unknown-resource");
		step1.appendChild(name1);
		step1.appendChild(conditionA);// added
		request.appendChild(step1);

		step2 = proxyDefault.createElement("Step");
		name2 = proxyDefault.createElement("Name");
		name2.setTextContent("unknown-resource-xml");
		step2.appendChild(name2);
		step2.appendChild(conditionB);
		request.appendChild(step2);

		flow.appendChild(request);
		flow.appendChild(response);
		flow.appendChild(condition);

		flows.appendChild(flow);

		LOGGER.fine(
				"Edited proxy xml: " + buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");
		xmlUtils.writeXML(apiTemplateDocument,
				buildFolder + File.separator + "apiproxy" + File.separator + proxyName + ".xml");

		xmlUtils.writeXML(proxyDefault, buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeOAS(Document returnOpenApiTemplate) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies" + File.separator;
		XMLUtils xmlUtils = new XMLUtils();
		Document returnOASXML = xmlUtils.cloneDocument(returnOpenApiTemplate);

		Node payload = returnOASXML.getElementsByTagName("Payload").item(0);
		payload.setTextContent(oasContent);

		xmlUtils.writeXML(returnOASXML, targetPath + "return-open-api.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeRootWrapper(Document rootWrapperTemplate, String operationName, String rootElement)
			throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies" + File.separator;

		XMLUtils xmlUtils = new XMLUtils();
		Document jsPolicyXML = xmlUtils.cloneDocument(rootWrapperTemplate);

		Node root = jsPolicyXML.getFirstChild();
		NamedNodeMap attr = root.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(operationName + "-root-wrapper");

		Node displayName = jsPolicyXML.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(operationName + " Root Wrapper");

		Node propertyElement = jsPolicyXML.getElementsByTagName("Property").item(0);
		propertyElement.setTextContent(rootElement);

		xmlUtils.writeXML(jsPolicyXML, targetPath + operationName + "-root-wrapper.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeAddSoapAction(Document addSoapActionTemplate, String operationName, String soapAction)
			throws Exception {
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

			xmlUtils.writeXML(soapActionPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator
					+ "policies" + File.separator + policyName + ".xml");

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeAddNamespace(Document namespaceTemplate, String operationName, boolean addOtherNamespaces)
			throws Exception {

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

				xmlUtils.writeXML(xslPolicyXMLOther, buildFolder + File.separator + "apiproxy" + File.separator
						+ "policies" + File.separator + policyNameOther + ".xml");
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

		// edgeui-654
		if (apiMap.getSoapBody().getBytes().length < 4096) {
			List<String> elementList = xmlUtils.getElementList(apiMap.getSoapBody());
			for (String elementName : elementList) {
				queryParam = extractPolicyXML.createElement("QueryParam");
				queryParam.setAttribute("name", getQueryParamName(elementName));

				pattern = extractPolicyXML.createElement("Pattern");
				pattern.setAttribute("ignoreCase", "true");
				pattern.setTextContent("{" + elementName + "}");

				queryParam.appendChild(pattern);
				rootElement.appendChild(queryParam);
			}
		} else {
			// setting a sample query param;edgeui-654
			LOGGER.warning("Large SOAP Message Template; Skipping extract policy");
			queryParam = extractPolicyXML.createElement("QueryParam");
			queryParam.setAttribute("name", "sample");

			pattern = extractPolicyXML.createElement("Pattern");
			pattern.setAttribute("ignoreCase", "true");
			pattern.setTextContent("{sample}");

			queryParam.appendChild(pattern);
			rootElement.appendChild(queryParam);
		}

		xmlUtils.writeXML(extractPolicyXML, buildFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator + policyName + ".xml");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	// EDGEUI-672
	private String replaceReservedVariables(String xmlPayload) {
		final String findOrg = "organization>\\{organization\\}</";
		final String replaceOrg = "organization>{org}</";

		return xmlPayload.replaceAll(findOrg, replaceOrg);
	}

	// EDGEUI-672
	private String getQueryParamName(String elementName) {
		if (elementName.equalsIgnoreCase("organization")) {
			return "org";
		} else {
			return elementName;
		}
	}

	private void writeSOAP2APIAssignMessagePolicies(Document assignTemplate, String operationName, String policyName,
			String soapAction) throws Exception {

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

		if (soapVersion.equalsIgnoreCase(SOAP_11)) {
			payloadAttr.setNodeValue(StringEscapeUtils.escapeXml10(SOAP11_PAYLOAD_TYPE));

			assignPolicyXML.getElementsByTagName("Header").item(1)
					.setTextContent(StringEscapeUtils.escapeXml10(SOAP11_CONTENT_TYPE));

			if (soapAction != null) {
				Node header = assignPolicyXML.getElementsByTagName("Header").item(0);
				header.setTextContent(soapAction);
			} else {
				final Node add = assignPolicyXML.getElementsByTagName("Add").item(0);
				add.getParentNode().removeChild(add);
			}
		} else {
			payloadAttr.setNodeValue(StringEscapeUtils.escapeXml10(SOAP12_PAYLOAD_TYPE));

			assignPolicyXML.getElementsByTagName("Header").item(1)
					.setTextContent(StringEscapeUtils.escapeXml10(SOAP12_CONTENT_TYPE));
		}

		APIMap apiMap = messageTemplates.get(operationName);
		Document operationPayload = null;
		// edgeui-654 (check for getBytes().length
		if (xmlUtils.isValidXML(apiMap.getSoapBody()) && apiMap.getSoapBody().getBytes().length < 4096) {
			// JIRA-EDGEUI-672
			operationPayload = xmlUtils.getXMLFromString(replaceReservedVariables(apiMap.getSoapBody()));
		} else {
			LOGGER.warning("Operation " + operationName + " soap template could not be generated");
			if (soapVersion.equalsIgnoreCase(SOAP_11)) {
				operationPayload = xmlUtils.getXMLFromString(emptySoap11);
			} else {
				operationPayload = xmlUtils.getXMLFromString(emptySoap12);
			}
		}

		Node importedNode = assignPolicyXML.importNode(operationPayload.getDocumentElement(), true);
		payload.appendChild(importedNode);

		Node value = assignPolicyXML.getElementsByTagName("Value").item(0);
		value.setTextContent(targetEndpoint);

		LOGGER.fine("Generated resource xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "policies"
				+ File.separator + policyName + ".xml");

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
			if (CORS) {
				Node response = targetDefault.getElementsByTagName("Response").item(0);
				Node step = targetDefault.createElement("Step");
				Node name = targetDefault.createElement("Name");
				name.setTextContent("add-cors");
				step.appendChild(name);
				response.appendChild(step);
			}
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

	private void writeJsonToXMLPolicy(Document jsonXMLTemplate, String operationName, String rootElement)
			throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String targetPath = buildFolder + File.separator + "apiproxy" + File.separator + "policies" + File.separator;

		XMLUtils xmlUtils = new XMLUtils();
		Document jsonxmlPolicyXML = xmlUtils.cloneDocument(jsonXMLTemplate);

		Node root = jsonxmlPolicyXML.getFirstChild();
		NamedNodeMap attr = root.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(operationName + "-json-to-xml");

		Node displayName = jsonxmlPolicyXML.getElementsByTagName("DisplayName").item(0);
		displayName.setTextContent(operationName + " JSON TO XML");

		/*
		 * Node objectRootElement =
		 * jsonxmlPolicyXML.getElementsByTagName("ObjectRootElementName").item(0
		 * ); objectRootElement.setTextContent(rootElement);
		 * 
		 * Node arrayRootElement =
		 * jsonxmlPolicyXML.getElementsByTagName("ArrayRootElementName").item(0)
		 * ; arrayRootElement.setTextContent(rootElement);
		 */

		xmlUtils.writeXML(jsonxmlPolicyXML, targetPath + operationName + "-json-to-xml.xml");

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
			/*
			 * String jsResourcePath = buildFolder + File.separator + "apiproxy"
			 * + File.separator + "resources" + File.separator + "jsc" +
			 * File.separator;
			 */

			LOGGER.fine("Source Path: " + sourcePath);
			LOGGER.fine("Target Path: " + targetPath);
			if (PASSTHRU) {
				sourcePath += "soappassthru/";
				Files.copy(getClass().getResourceAsStream(sourcePath + "Extract-Operation-Name.xml"),
						Paths.get(targetPath + "Extract-Operation-Name.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "Invalid-SOAP.xml"),
						Paths.get(targetPath + "Invalid-SOAP.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				/*
				 * Files.copy(getClass().getResourceAsStream(sourcePath +
				 * "Return-WSDL.xml"), Paths.get(targetPath +
				 * "Return-WSDL.xml"),
				 * java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				 */
			} else {
				sourcePath += "soap2api/";
				Files.copy(getClass().getResourceAsStream(sourcePath + "xml-to-json.xml"),
						Paths.get(targetPath + "xml-to-json.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "set-response-soap-body.xml"),
						Paths.get(targetPath + "set-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "set-response-soap-body-accept.xml"),
						Paths.get(targetPath + "set-response-soap-body-accept.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "get-response-soap-body.xml"),
						Paths.get(targetPath + "get-response-soap-body.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "get-response-soap-body-xml.xml"),
						Paths.get(targetPath + "get-response-soap-body-xml.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "set-target-url.xml"),
						Paths.get(targetPath + "set-target-url.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "extract-format.xml"),
						Paths.get(targetPath + "extract-format.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "unknown-resource.xml"),
						Paths.get(targetPath + "unknown-resource.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "unknown-resource-xml.xml"),
						Paths.get(targetPath + "unknown-resource-xml.xml"),
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
				Files.copy(getClass().getResourceAsStream(sourcePath + "return-generic-error-accept.xml"),
						Paths.get(targetPath + "return-generic-error-accept.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "remove-namespaces.xml"),
						Paths.get(targetPath + "remove-namespaces.xml"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.copy(getClass().getResourceAsStream(sourcePath + "remove-namespaces.xslt"),
						Paths.get(xslResourcePath + "remove-namespaces.xslt"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				/*
				 * Files.copy(getClass().getResourceAsStream(sourcePath +
				 * "root-wrapper.js"), Paths.get(jsResourcePath +
				 * "root-wrapper.js"),
				 * java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				 */

				if (OAUTH) {
					Files.copy(getClass().getResourceAsStream(sourcePath + "verify-oauth-v2-access-token.xml"),
							Paths.get(targetPath + "verify-oauth-v2-access-token.xml"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					Files.copy(getClass().getResourceAsStream(sourcePath + "remove-header-authorization.xml"),
							Paths.get(targetPath + "remove-header-authorization.xml"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					if (QUOTAOAUTH) {
						Files.copy(getClass().getResourceAsStream(sourcePath + "impose-quota-oauth.xml"),
								Paths.get(targetPath + "impose-quota-oauth.xml"),
								java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}

				if (APIKEY) {
					Files.copy(getClass().getResourceAsStream(sourcePath + "verify-api-key.xml"),
							Paths.get(targetPath + "verify-api-key.xml"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					Files.copy(getClass().getResourceAsStream(sourcePath + "remove-query-param-apikey.xml"),
							Paths.get(targetPath + "remove-query-param-apikey.xml"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					if (QUOTAAPIKEY) {
						Files.copy(getClass().getResourceAsStream(sourcePath + "impose-quota-apikey.xml"),
								Paths.get(targetPath + "impose-quota-apikey.xml"),
								java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}

				if (CORS) {
					Files.copy(getClass().getResourceAsStream(sourcePath + "add-cors.xml"),
							Paths.get(targetPath + "add-cors.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
			e.printStackTrace();
			throw e;
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAPPassThruProxyEndpointConditions(String proxyDescription) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String soapConditionText = "((envelope != \"Envelope\") or (body != \"Body\") or (envelopeNamespace !=\"";

		XMLUtils xmlUtils = new XMLUtils();
		Document proxyDefault = xmlUtils.readXML(SOAPPASSTHRU_PROXY_TEMPLATE);
		Document getWsdlTemplate = xmlUtils.readXML(SOAPPASSTHRU_GETWSDL_TEMPLATE);

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

		Node description = proxyDefault.getElementsByTagName("Description").item(0);
		description.setTextContent(proxyDescription);

		Node soapCondition = proxyDefault.getElementsByTagName("Condition").item(2);
		if (soapVersion.equalsIgnoreCase(SOAP_11)) {
			soapCondition.setTextContent(soapConditionText + SOAP11 + "\"))  and (request.verb != \"GET\")");
		} else {
			soapCondition.setTextContent(soapConditionText + SOAP12 + "\"))  and (request.verb != \"GET\")");
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

		// Add get wsdl
		flow = proxyDefault.createElement("Flow");
		((Element) flow).setAttribute("name", "Get WSDL");
		flowDescription = proxyDefault.createElement("Description");
		flowDescription.setTextContent("Unknown Resource");
		flow.appendChild(flowDescription);

		request = proxyDefault.createElement("Request");
		response = proxyDefault.createElement("Response");
		condition = proxyDefault.createElement("Condition");
		condition.setTextContent(
				"(proxy.pathsuffix MatchesPath \"/\") and (request.verb = \"GET\") and (request.queryparam.wsdl != \"\")");

		step1 = proxyDefault.createElement("Step");
		name1 = proxyDefault.createElement("Name");
		name1.setTextContent("Return-WSDL");

		step1.appendChild(name1);
		request.appendChild(step1);

		flow.appendChild(request);
		flow.appendChild(response);
		flow.appendChild(condition);

		flows.appendChild(flow);

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

		writeRaiseFault(getWsdlTemplate);

		xmlUtils.writeXML(proxyDefault, buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");
		LOGGER.fine("Edited target xml: " + buildFolder + File.separator + "apiproxy" + File.separator + "proxies"
				+ File.separator + "default.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}
	
	/**
	 * 
	 * @param wsdlContent
	 * @return
	 * 
	 * The predic8 "getAsString" method does not include a namespace prefix for the definitions
	 * element. It assumes default namespace. If the WSDL didn't include a default namespace, then 
	 * WSDL importers like SOAP UI and others fail to import the WSDL. This method looks to see if the
	 * WSDL has a default namespace and adds it if missing
	 */
	private String addDefaultNamespace(String wsdlContent) {
		
		String containsString = "xmlns='http://schemas.xmlsoap.org/wsdl/'";
		String replaceString = "xmlns:wsdl='http://schemas.xmlsoap.org/wsdl/' xmlns='http://schemas.xmlsoap.org/wsdl/'";
		String findString = "xmlns:wsdl='http://schemas.xmlsoap.org/wsdl/'";
		
		if (wsdlContent.indexOf(containsString) != -1) { //already has default namespace.
			return wsdlContent;
		} else {
			return wsdlContent.replaceAll(findString, replaceString);
		}
	}

	private void writeRaiseFault(Document getWsdlTemplate) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();
		Document getWsdlRaiseFaultPolicy = xmlUtils.cloneDocument(getWsdlTemplate);

		Node payload = getWsdlRaiseFaultPolicy.getElementsByTagName("Payload").item(0);
		payload.setTextContent(addDefaultNamespace(wsdlContent));

		xmlUtils.writeXML(getWsdlRaiseFaultPolicy, buildFolder + File.separator + "apiproxy" + File.separator
				+ "policies" + File.separator + "Return-WSDL.xml");

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	private static Boolean isPrimitive(String type) {
		return primitiveTypes.contains(type);
	}

	private String getNamespace(String prefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(prefix)) {
				return entry.getValue();
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return null;
	}

	public String getPrefix(String namespaceUri) {
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

	private void parseElement(com.predic8.schema.Element e, List<Schema> schemas, JsonObject parent,
			String parentName) {
		if (e.getName() == null) {
			if (e.getRef() != null) {
				final String localPart = e.getRef().getLocalPart();
				final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
				JsonObject complexType = OASUtils.createComplexType(element.getName(), element.getMinOccurs(),
						element.getMaxOccurs());
				OASUtils.addObject(parent, parentName, element.getName());
				definitions.add(element.getName(), complexType);
				parseSchema(element, schemas, element.getName(), complexType);
			} else {
				// TODO: handle this
				LOGGER.warning("unhandle conditions getRef() = null");
			}
		} else {
			if (e.getEmbeddedType() instanceof ComplexType) {
				ComplexType ct = (ComplexType) e.getEmbeddedType();
				JsonObject rootElement = OASUtils.createComplexType(e.getName(), e.getMinOccurs(), e.getMaxOccurs());
				OASUtils.addObject(parent, parentName, e.getName());
				definitions.add(e.getName(), rootElement);
				parseSchema(ct.getModel(), schemas, e.getName(), rootElement);
			} else if (e.getType() != null) {
				TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
				if (typeDefinition instanceof ComplexType) {
					ComplexType ct = (ComplexType) typeDefinition;
					JsonObject rootElement = OASUtils.createComplexType(e.getName(), e.getMinOccurs(),
							e.getMaxOccurs());
					OASUtils.addObject(parent, parentName, e.getName());
					definitions.add(e.getName(), rootElement);
					parseSchema(ct.getModel(), schemas, e.getName(), rootElement);
				}
			}
		}
	}

	private void parseElement(com.predic8.schema.Element e, List<Schema> schemas, String rootElement,
			String rootNamespace, String rootPrefix) {
		if (e.getName() == null) {
			if (e.getRef() != null) {
				final String localPart = e.getRef().getLocalPart();
				final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
				parseSchema(element, schemas, rootElement, rootNamespace, rootPrefix);
			} else {
				// fail silently
				LOGGER.warning("unhandled conditions getRef() = null");
			}
		} else {
			if (!e.getName().equalsIgnoreCase(rootElement)) {
				if (e.getEmbeddedType() instanceof ComplexType) {
					ComplexType ct = (ComplexType) e.getEmbeddedType();
					if (!e.getNamespaceUri().equalsIgnoreCase(rootNamespace)) {
						buildXPath(e, rootElement, rootNamespace, rootPrefix);
					}
					parseSchema(ct.getModel(), schemas, rootElement, rootNamespace, rootPrefix);
				} else {
					if (e.getType() != null) {
						if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
								&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						}
						TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
						if (typeDefinition instanceof ComplexType) {
							parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace,
									rootPrefix);
						}
					} else {
						// handle this as anyType
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
			for (Schema schema : schemas) {
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

	private void parseSchema(SchemaComponent sc, List<Schema> schemas, String rootElementName, JsonObject rootElement) {

		// fail safe measure.
		if (Thread.currentThread().getStackTrace().length >= 128) {
			TOO_MANY = true;
			return;
		} else if (TOO_MANY)
			return;
		else if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			for (com.predic8.schema.Element e : seq.getElements()) {
				if (e.getType() != null) {
					if (isPrimitive(e.getType().getLocalPart())) {
						if (rootElement == null) {
							rootElement = OASUtils.createComplexType(e.getName(), e.getMinOccurs(), e.getMaxOccurs());
							rootElementName = e.getName();
							definitions.add(e.getName(), rootElement);
						}
						JsonObject properties = rootElement.getAsJsonObject("properties");
						properties.add(e.getName(), OASUtils.createSimpleType(e.getType().getLocalPart(),
								e.getMinOccurs(), e.getMaxOccurs()));
						queryParams.add(e.getName());
					} else {
						parseElement(e, schemas, rootElement, rootElementName);
					}
				} else {
					parseElement(e, schemas, rootElement, rootElementName);
				}
			}
		} else if (sc instanceof Choice) {
			Choice ch = (Choice) sc;
			for (com.predic8.schema.Element e : ch.getElements()) {
				if (e.getType() != null) {
					if (isPrimitive(e.getType().getLocalPart())) {
						if (rootElement == null) {
							rootElement = OASUtils.createComplexType(e.getName(), e.getMinOccurs(), e.getMaxOccurs());
							rootElementName = e.getName();
							definitions.add(e.getName(), rootElement);
						}
						JsonObject properties = rootElement.getAsJsonObject("properties");
						properties.add(e.getName(), OASUtils.createSimpleType(e.getType().getLocalPart(),
								e.getMinOccurs(), e.getMaxOccurs()));
						queryParams.add(e.getName());
					}
				} else {
					parseElement(e, schemas, rootElement, rootElementName);
				}
			}
		} else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent) sc;
			Derivation derivation = complexContent.getDerivation();

			if (derivation != null) {
				TypeDefinition typeDefinition = getTypeFromSchema(derivation.getBase(), schemas);
				if (typeDefinition instanceof ComplexType) {
					String name = ((ComplexType) typeDefinition).getName();
					JsonObject complexType = OASUtils.createComplexType(name, "0", "1");
					parseSchema(((ComplexType) typeDefinition).getModel(), schemas, name, complexType);
					definitions.add(name, complexType);
				}
			}
		} else if (sc instanceof SimpleContent) {
			SimpleContent simpleContent = (SimpleContent) sc;
			Derivation derivation = (Derivation) simpleContent.getDerivation();

			JsonObject properties = rootElement.getAsJsonObject("properties");

			if (derivation.getAllAttributes().size() > 0) {
				// has attributes
				for (Attribute attribute : derivation.getAllAttributes()) {
					properties.add("@" + attribute.getName(), OASUtils.createSimpleType("string", "0", "1"));
				}
			}
		} else if (sc instanceof com.predic8.schema.Element) {
			parseElement((com.predic8.schema.Element) sc, schemas, rootElement, rootElementName);
		} else if (sc instanceof All) {
			All all = (All) sc;
			for (com.predic8.schema.Element e : all.getElements()) {
				parseElement(e, schemas, rootElement, rootElementName);
			}
		}
	}

	private void parseSchema(SchemaComponent sc, List<Schema> schemas, String rootElement, String rootNamespace,
			String rootPrefix) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		// fail safe measures.
		if (Thread.currentThread().getStackTrace().length >= 128) {// edgeui-654
			TOO_MANY = true;
			return;
		}

		else if (TOO_MANY)
			return;// edgeui-654

		else if (ruleList.size() >= 100) {
			// the rules are too big. clear the rules.
			TOO_MANY = true;
			return;
		} else if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			level++;
			for (com.predic8.schema.Element e : seq.getElements()) {
				if (e.getName() == null)
					level--;
				if (e.getName() != null) {
					xpathElement.put(level, e.getName());
					if (e.getType() != null) {
						if (e.getType().getLocalPart().equalsIgnoreCase("anyType")) {
							// found a anyType. remove namespaces for
							// descendents
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						}
					}
				}
				parseElement(e, schemas, rootElement, rootNamespace, rootPrefix);
			}
			level--;
			cleanUpXPath();
		} else if (sc instanceof Choice) {
			Choice ch = (Choice) sc;
			level++;
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
							parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace,
									rootPrefix);
						}
						if (e.getType() == null) {
							// handle this any anyType
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
							LOGGER.warning("Element " + e.getName() + " type was null; treating as anyType");
						} else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
								&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						} else if (e.getType().getLocalPart().equalsIgnoreCase("anyType")) {
							// if you find a anyType, remove namespace for the
							// descendents.
							buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						}
					}
				}
			}
			level--;
			cleanUpXPath();
		} else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent) sc;
			Derivation derivation = complexContent.getDerivation();

			if (derivation != null) {
				TypeDefinition typeDefinition = getTypeFromSchema(derivation.getBase(), schemas);
				if (typeDefinition instanceof ComplexType) {
					parseSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement, rootNamespace,
							rootPrefix);
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
				// has attributes
				buildXPath(derivation.getNamespaceUri(), rootElement, rootNamespace, rootPrefix);
			}
		} else if (sc instanceof com.predic8.schema.Element) {
			level++;
			xpathElement.put(level, ((com.predic8.schema.Element) sc).getName());
			parseElement((com.predic8.schema.Element) sc, schemas, rootElement, rootNamespace, rootPrefix);
		} else if (sc instanceof All) {
			All all = (All) sc;
			level++;
			for (com.predic8.schema.Element e : all.getElements()) {
				if (e.getName() == null)
					level--;
				if (e.getName() != null)
					xpathElement.put(level, e.getName());
				parseElement(e, schemas, rootElement, rootNamespace, rootPrefix);
			}
			level--;
			cleanUpXPath();
		} else if (sc != null) {
			// fail silently
			LOGGER.warning("unhandled conditions - " + sc.getClass().getName());
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void parseParts(List<Part> parts, List<Schema> schemas, String rootElementName, JsonObject rootElement) {
		for (Part part : parts) {
			if (rootElement == null) {
				rootElement = OASUtils.createComplexType(part.getName(), "0", "1");
				rootElementName = part.getName();
				definitions.add(part.getName(), rootElement);
			}
			if (isPrimitive(part.getType().getQname().getLocalPart())) {
				JsonObject properties = rootElement.getAsJsonObject("properties");
				properties.add(part.getName(),
						OASUtils.createSimpleType(part.getType().getQname().getLocalPart(), "0", "1"));
				queryParams.add(part.getName());
			} else {
				TypeDefinition typeDefinition = part.getType();
				if (typeDefinition instanceof ComplexType) {
					ComplexType ct = (ComplexType) typeDefinition;
					parseSchema(ct.getModel(), schemas, rootElementName, rootElement);
				}
			}
		}
	}

	private String parseParts(List<Part> parts, List<Schema> schemas, String rootElement, String rootNamespace,
			String rootPrefix, String soapRequest) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		for (Part part : parts) {
			if (part.getType() != null) {
				if (isPrimitive(part.getType().getQname().getLocalPart())) {
					soapRequest = soapRequest + "<" + part.getName()
							+ " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"" + part.getTypePN()
							+ "\">" + "?</" + part.getName() + ">\n";
					// primitive elements are in the same namespace, skip xpath
				} else {
					TypeDefinition typeDefinition = part.getType();
					if (typeDefinition instanceof ComplexType) {
						ComplexType ct = (ComplexType) typeDefinition;
						SchemaComponent sc = ct.getModel();
						try {
							soapRequest = soapRequest + "<" + part.getName()
									+ " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\""
									+ part.getTypePN() + "\" xmlns:" + part.getType().getPrefix() + "=\""
									+ part.getType().getNamespaceUri() + "\">\n";
							soapRequest += sc.getRequestTemplate();
							soapRequest += "\n</" + part.getName() + ">";
							xpathElement.put(++level, part.getName());
							// since we already have the soap request template,
							// no need to pass it.
							// call parseRPCschema to find any elements with a
							// different namespace
							parseRPCSchema(sc, schemas, rootElement, rootNamespace, rootPrefix, "");
							level--;
						} catch (Exception e) {
							soapRequest += "\n</" + part.getName() + ">";
							soapRequest = parseRPCSchema(sc, schemas, rootElement, rootNamespace, rootPrefix,
									soapRequest);
						}
					} else if (typeDefinition instanceof com.predic8.schema.SimpleType) {
						com.predic8.schema.SimpleType st = (com.predic8.schema.SimpleType)typeDefinition;
						//TODO: 
						//LOGGER.warning("Handle simple type");
					} else if (typeDefinition instanceof BuiltInSchemaType) {
						BuiltInSchemaType bst = (BuiltInSchemaType) typeDefinition;
						soapRequest = soapRequest + bst.getRequestTemplate();
					} else {
						LOGGER.warning("WARNING: unhandled type - " + typeDefinition.getClass().getName());
					}
				}
			} else {
				soapRequest += part.getElement().getRequestTemplate();
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return soapRequest;
	}

	private String parseRPCSchema(SchemaComponent sc, List<Schema> schemas, String rootElement, String rootNamespace,
			String rootPrefix, String soapRequest) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		if (sc instanceof Sequence) {
			Sequence sequence = (Sequence) sc;
			level++;
			soapRequest = soapRequest + sequence.getRequestTemplate();
			for (com.predic8.schema.Element e : sequence.getElements()) {
				if (e.getName() == null)
					level--;
				if (e.getName() != null)
					xpathElement.put(level, e.getName());
				parseRPCElement(e, schemas, rootElement, rootNamespace, rootPrefix, soapRequest);
			}
			level--;
			cleanUpXPath();
		} else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent) sc;
			Derivation derivation = complexContent.getDerivation();

			if (derivation != null) {
				TypeDefinition typeDefinition = getTypeFromSchema(derivation.getBase(), schemas);
				if (typeDefinition instanceof ComplexType) {
					soapRequest = parseRPCSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement,
							rootNamespace, rootPrefix, soapRequest);
				}
				if (derivation.getModel() instanceof Sequence) {
					soapRequest = parseRPCSchema((Sequence) derivation.getModel(), schemas, rootElement, rootNamespace,
							rootPrefix, soapRequest);
				} else if (derivation.getModel() instanceof ModelGroup) {
					soapRequest = parseRPCSchema((ModelGroup) derivation.getModel(), schemas, rootElement,
							rootNamespace, rootPrefix, soapRequest);
				}
			}
		} else if (sc instanceof SimpleContent) {
			SimpleContent simpleContent = (SimpleContent) sc;
			Derivation derivation = (Derivation) simpleContent.getDerivation();

			if (derivation.getAllAttributes().size() > 0) {
				// has attributes
				buildXPath(derivation.getNamespaceUri(), rootElement, rootNamespace, rootPrefix);
			}
		} else if (sc instanceof GroupRef) {
			// GroupRef groupRef = (GroupRef)sc;
			LOGGER.fine("WARNING: GroupRef not handled.");
		} else if (sc instanceof All) {
			All all = (All) sc;
			level++;
			soapRequest = soapRequest + all.getRequestTemplate();
			for (com.predic8.schema.Element e : all.getElements()) {
				if (e.getName() == null)
					level--;
				if (e.getName() != null)
					xpathElement.put(level, e.getName());
				parseRPCElement(e, schemas, rootElement, rootNamespace, rootPrefix, soapRequest);
			}
			level--;
			cleanUpXPath();
		} else {
			LOGGER.warning("WARNING: unhandled type " + sc.getClass().getName());
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return soapRequest;
	}

	private void parseRPCElement(com.predic8.schema.Element e, List<Schema> schemas, String rootElement,
			String rootNamespace, String rootPrefix, String soapRequest) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		if (e.getName() == null) {
			if (e.getRef() != null) {
				final String localPart = e.getRef().getLocalPart();
				final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
				parseRPCSchema(element, schemas, rootElement, rootNamespace, rootPrefix, soapRequest);
			} else {
				// TODO: handle this
				LOGGER.warning("unhandle conditions getRef() = null");
			}
		} else {
			if (!e.getName().equalsIgnoreCase(rootElement)) {
				if (e.getEmbeddedType() instanceof ComplexType) {
					ComplexType ct = (ComplexType) e.getEmbeddedType();
					if (!e.getNamespaceUri().equalsIgnoreCase(rootNamespace)) {
						buildXPath(e, rootElement, rootNamespace, rootPrefix);
					}
					parseRPCSchema(ct.getModel(), schemas, rootElement, rootNamespace, rootPrefix, soapRequest);
				} else {
					if (e.getType() != null) {
						if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)
								&& !e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						}
						TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
						if (typeDefinition instanceof ComplexType) {
							parseRPCSchema(((ComplexType) typeDefinition).getModel(), schemas, rootElement,
									rootNamespace, rootPrefix, soapRequest);
						}
					} else {
						// handle this as anyType
						buildXPath(e, rootElement, rootNamespace, rootPrefix, true);
						if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace)) {
							buildXPath(e, rootElement, rootNamespace, rootPrefix);
						}
						LOGGER.warning("Found element " + e.getName() + " with no type. Handling as xsd:anyType");
					}
				}
			}
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

	private void cleanUpXPath() {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		final Iterator<Map.Entry<Integer, String>> iterator = xpathElement.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<Integer, String> next = iterator.next();
			if (next.getKey() > level) {
				iterator.remove();
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private String getSoapNamespace() {
		if (soapVersion.equalsIgnoreCase(SOAP_11)) {
			return soap11Namespace;
		} else {
			return soap12Namespace;
		}
	}

	private String buildSOAPRequest(List<Part> parts, List<Schema> schemas, String rootElement, String rootNamespace,
			boolean generateParts) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		String prefix = getPrefix(rootNamespace);
		String soapRequest = null;

		if (RPCSTYLE) {
			soapRequest = "<soapenv:Envelope " + getSoapNamespace() + getNamespacesAsString(true)
					+ " soapenv:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n<soapenv:Body>\n" + "<"
					+ prefix + ":" + rootElement + ">\n";

		} else {
			soapRequest = "<soapenv:Envelope " + getSoapNamespace() + getNamespacesAsString(true)
					+ " >\n<soapenv:Body>\n";
		}

		if (generateParts) {
			try {
				soapRequest = parseParts(parts, schemas, rootElement, rootNamespace, prefix, soapRequest);
			} catch (Exception e) {
				LOGGER.warning(
						"Failed to parse parts. Not generating SOAP Template. Try changing the verb to POST/PUT");
			}
		}

		if (RPCSTYLE) {
			soapRequest += "</" + prefix + ":" + rootElement + ">\n";
		}

		soapRequest += "</soapenv:Body>\n</soapenv:Envelope>";

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return soapRequest;
	}

	private void buildXPath(com.predic8.schema.Element e, String rootElement, String rootNamespace, String rootPrefix,
			boolean removeNamespace) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		Rule r = null;
		String xpathString = "";
		String prefix = "NULL";
		String namespaceUri = "NULL";
		String soapElements = "/soapenv:Envelope/soapenv:Body";
		String lastElement = "";

		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
			lastElement = entry.getValue();
		}

		// add the last element to xpath
		if (!lastElement.equalsIgnoreCase(e.getName()))
			xpathString = xpathString + "/" + rootPrefix + ":" + e.getName();

		r = new Rule(soapElements + xpathString, prefix, namespaceUri, "descendant");
		ruleList.add(r);
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	private void buildXPath(com.predic8.schema.Element e, String rootElement, String rootNamespace, String rootPrefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		Rule r = null;
		String xpathString = "";
		String soapElements = "/soapenv:Envelope/soapenv:Body";
		String prefix = getPrefix(e.getNamespaceUri());
		String namespaceUri = e.getNamespaceUri();
		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
		}

		r = new Rule(soapElements + xpathString, prefix, namespaceUri);
		ruleList.add(r);
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void buildXPath(String namespaceUri, String rootElement, String rootNamespace, String rootPrefix) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		Rule r = null;
		String prefix = getPrefix(namespaceUri);
		String soapElements = "/soapenv:Envelope/soapenv:Body";
		String xpathString = "";

		for (Map.Entry<Integer, String> entry : xpathElement.entrySet()) {
			xpathString = xpathString + "/" + rootPrefix + ":" + entry.getValue();
		}

		r = new Rule(soapElements + xpathString + "/@*", prefix, namespaceUri);
		ruleList.add(r);

		cleanUpXPath();
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private String getNamespacesAsString(boolean removeBlackListed) {
		String namespaces = "";
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (removeBlackListed) {
				if (!isBlackListed(entry.getValue())) {
					namespaces += " xmlns:" + entry.getKey() + "=\"" + entry.getValue() + "\"";
				}
			} else {
				namespaces += " xmlns:" + entry.getKey() + "=\"" + entry.getValue() + "\"";
			}
		}
		return namespaces;
	}

	private static boolean isBlackListed(String namespaceURI) {
		return blackListedNamespaces.contains(namespaceURI);
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

	@SuppressWarnings("unchecked")
	private APIMap createAPIMap(Operation op, Definitions wsdl, String verb, String resourcePath, XMLUtils xmlUtils)
			throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		APIMap apiMap = null;
		String soapRequest = "";

		namespace = (Map<String, String>) op.getNamespaceContext();

		try {
			if (verb.equalsIgnoreCase("GET")) {

				soapRequest = buildSOAPRequest(op.getInput().getMessage().getParts(), wsdl.getSchemas(), op.getName(),
						op.getNamespaceUri(), true);

				if (op.getInput().getMessage().getParts().size() == 0) {
					apiMap = new APIMap(null, soapRequest, resourcePath, verb, op.getName(), false);
				} else {
					KeyValue<String, String> kv = xmlUtils.replacePlaceHolders(soapRequest);
					apiMap = new APIMap(kv.getValue(), kv.getKey(), resourcePath, verb, op.getName(), false);
				}
			} else {
				soapRequest = buildSOAPRequest(op.getInput().getMessage().getParts(), wsdl.getSchemas(), op.getName(),
						op.getNamespaceUri(), true);

				String namespaceUri = op.getNamespaceUri();
				String prefix = getPrefix(namespaceUri);

				if (soapVersion.equalsIgnoreCase(SOAP_11)) {
					xmlUtils.generateRootNamespaceXSLT(SOAP2API_XSLT11_TEMPLATE, SOAP2API_XSL, op.getName(), prefix,
							null, namespaceUri, namespace);
				} else {
					xmlUtils.generateRootNamespaceXSLT(SOAP2API_XSLT12_TEMPLATE, SOAP2API_XSL, op.getName(), prefix,
							null, namespaceUri, namespace);
				}

				if (ruleList.size() > 0) {
					RuleSet rs = new RuleSet();
					rs.addRuleList(ruleList);
					xmlUtils.generateOtherNamespacesXSLT(SOAP2API_XSL, op.getName(), rs.getTransform(soapVersion),
							namespace);
					ruleList.clear();
					apiMap = new APIMap("", soapRequest, resourcePath, verb, op.getName(), true);
				} else {
					apiMap = new APIMap("", soapRequest, resourcePath, verb, op.getName(), false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return apiMap;
	}

	public String getSOAPVersion(String wsdlPath) throws NoServicesFoundException {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		Service service = null;
		com.predic8.wsdl.Port port = null;
		WSDLParser2 parser = new WSDLParser2();
		Definitions wsdl = null;

		wsdl = parser.parse(wsdlPath);
		if (wsdl.getServices().size() == 0) {
			LOGGER.severe("No services were found in the WSDL");
			throw new NoServicesFoundException("No services were found in the WSDL");
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

		if (portName != null) {
			for (com.predic8.wsdl.Port prt : service.getPorts()) {
				if (prt.getName().equalsIgnoreCase(portName)) {
					port = prt;
				}
			}
			if (port == null) { // didn't find any port matching name
				LOGGER.severe("No matching port was found in the WSDL");
				throw new NoServicesFoundException("No matching port found in the WSDL");
			}
		} else {
			port = service.getPorts().get(0); // get first port
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return port.getBinding().getProtocol().toString();

	}

	private void getOASDefinitions(Definitions wsdl, com.predic8.schema.Element e) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (e != null) {
			TypeDefinition typeDefinition = null;
			if (e.getEmbeddedType() != null) {
				typeDefinition = e.getEmbeddedType();
			} else {
				typeDefinition = getTypeFromSchema(e.getType(), wsdl.getSchemas());
			}
			if (typeDefinition instanceof ComplexType) {
				ComplexType ct = (ComplexType) typeDefinition;
				JsonObject rootElement = OASUtils.createComplexType(e.getName(), e.getMinOccurs(), e.getMaxOccurs());
				definitions.add(e.getName(), rootElement);
				parseSchema(ct.getModel(), wsdl.getSchemas(), e.getName(), rootElement);
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void getWSDLDetails(String wsdlPath) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		Service service = null;
		List<com.predic8.wsdl.Port> ports = new ArrayList<com.predic8.wsdl.Port>();
		List<Service> services = new ArrayList<Service>();

		try {
			WSDLParser2 parser = new WSDLParser2();
			wsdl = parser.parse(wsdlPath);
			if (wsdl.getServices().size() == 0) {
				LOGGER.severe("No services were found in the WSDL");
				throw new NoServicesFoundException("No services were found in the WSDL");
			}
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.severe(e.getLocalizedMessage());
			throw e;
		}

		KeyValue<String, String> map = StringUtils.proxyNameAndBasePath(wsdlPath);

		if (serviceName != null) {
			for (Service svc : wsdl.getServices()) {
				if (svc.getName().equalsIgnoreCase(serviceName)) {
					service = svc;
					LOGGER.fine("Found Service: " + service.getName());
					break;
				}
			}
			if (service == null) { // didn't find any service matching name
				LOGGER.severe("No matching services were found in the WSDL");
				throw new NoServicesFoundException("No matching services were found in the WSDL");
			} else {
				proxyName = serviceName;
			}
		} else {
			service = wsdl.getServices().get(0); // get the first service
			LOGGER.fine("Found Service: " + service.getName());
			serviceName = service.getName();
			proxyName = serviceName;
		}

		if (basePath == null) {
			if (serviceName != null) {
				basePath = "/" + serviceName.toLowerCase();
			} else {
				basePath = map.getValue();
			}
		}

		if (portName != null) {
			for (com.predic8.wsdl.Port prt : service.getPorts()) {
				if (prt.getName().equalsIgnoreCase(portName)) {
					port = prt;
				}
			}
			if (port == null) { // didn't find any port matching name
				LOGGER.severe("No matching port was found in the WSDL");
				throw new NoServicesFoundException("No matching port found in the WSDL");
			}
		} else {
			port = service.getPorts().get(0); // get first port
			portName = port.getName();
		}
		LOGGER.fine("Found Port: " + port.getName());

		Binding binding = port.getBinding();
		soapVersion = binding.getProtocol().toString();

		// EDGEUI-659
		if (!soapVersion.equalsIgnoreCase(SOAP_11) && !soapVersion.equalsIgnoreCase(SOAP_12)) {
			// set default
			LOGGER.warning("Unknow SOAP Version. Setting to SOAP 1.1");
			soapVersion = SOAP_11;
		}

		if (binding.getStyle().toLowerCase().contains("rpc")) {
			LOGGER.info("Binding Stype: " + binding.getStyle());
			RPCSTYLE = true;
		} else if (binding.getStyle().toLowerCase().contains("document")) {
			LOGGER.info("Binding Stype: " + binding.getStyle());
			RPCSTYLE = false;
		} else {
			LOGGER.info(binding.getStyle() + ". Treating as document");
			RPCSTYLE = false;
		}

		targetEndpoint = port.getAddress().getLocation();

		// start feature
		port.getAddress().setLocation("@request.header.host#" + basePath);
		ports.add(port);
		service.setPorts(ports);
		services.add(service);
		wsdlContent = wsdl.getAsString();
		// end feature

		LOGGER.info("Retrieved WSDL endpoint: " + targetEndpoint);

		String[] schemes = { "http", "https" };
		UrlValidator urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);

		if (!urlValidator.isValid(targetEndpoint)) {
			LOGGER.warning("Target endpoint is not http/https URL. Assigning a default value");
			targetEndpoint = "http://localhost:8080/soap";
		}

		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	@SuppressWarnings("unchecked")
	private void parseWSDL() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();

		StringWriter writer = new StringWriter();
		SOARequestCreator creator = new SOARequestCreator(wsdl, new RequestTemplateCreator(),
				new MarkupBuilder(writer));
		// edgeui-654
		creator.setMaxRecursionDepth(2);

		Binding binding = port.getBinding();
		PortType portType = binding.getPortType();
		String bindingName = binding.getName();

		LOGGER.fine("Found Binding: " + bindingName + " Binding Protocol: " + soapVersion + " Prefix: "
				+ binding.getPrefix() + " NamespaceURI: " + binding.getNamespaceUri());

		APIMap apiMap = null;
		HashMap<String, SelectedOperation> selectedOperationList = selectedOperations.getSelectedOperations();

		for (Operation op : portType.getOperations()) {
			LOGGER.fine("Found Operation Name: " + op.getName() + " Prefix: " + op.getPrefix() + " NamespaceURI: "
					+ op.getNamespaceUri());
			try {
				// the current operations is not in the list; skip.
				if (selectedOperationList.size() > 0 && !selectedOperationList.containsKey(op.getName())) {
					continue;
				}
				// if passthru, then do nothing much
				if (PASSTHRU) {
					if (RPCSTYLE) {
						apiMap = new APIMap(null, null, null, "POST", op.getName(), false);
					} else {
						com.predic8.schema.Element requestElement = null;
						if (op.getInput().getMessage().getParts().size() > 0) {
							// get root element
							requestElement = op.getInput().getMessage().getParts().get(0).getElement();
						}
						if (requestElement != null) {
							apiMap = new APIMap(null, null, null, "POST", requestElement.getName(), false);
						} else {
							apiMap = new APIMap(null, null, null, "POST", op.getName(), false);
						}
					}
				} else {
					String resourcePath = operationsMap.getResourcePath(op.getName(), selectedOperationList);
					String verb = "";
					// if all post options is not turned on, then interpret the
					// operation from opsmap
					if (!ALLPOST) {
						verb = operationsMap.getVerb(op.getName(), selectedOperationList);
					} else { // else POST
						verb = "POST";
					}

					if (RPCSTYLE) {
						apiMap = createAPIMap(op, wsdl, verb, resourcePath, xmlUtils);
					} else {// document style
						// there can be soap messages with no parts. membrane
						// soap can't construct soap
						// template for such messages. manually build
						if (op.getInput().getMessage().getParts().size() == 0) {
							apiMap = createAPIMap(op, wsdl, verb, resourcePath, xmlUtils);
						} else {
							com.predic8.schema.Element requestElement = op.getInput().getMessage().getParts().get(0)
									.getElement();

							if (requestElement != null) {
								namespace = (Map<String, String>) requestElement.getNamespaceContext();

								if (verb.equalsIgnoreCase("GET")) {
									if (soapVersion.equalsIgnoreCase(SOAP_11)
											|| soapVersion.equalsIgnoreCase(SOAP_12)) {
										creator.setCreator(new RequestTemplateCreator());
										// use membrane SOAP to generate a SOAP
										// Request
										try {
											creator.createRequest(port.getName(), op.getName(), binding.getName());
											KeyValue<String, String> kv = xmlUtils
													.replacePlaceHolders(writer.toString());

											// sometimes membrane soa generates
											// invalid soap
											// this will cause the bundle to not
											// be uploaded

											// store the operation name, SOAP
											// Request and
											// the
											// expected JSON Body in the map
											apiMap = new APIMap(kv.getValue(), kv.getKey(), resourcePath, verb,
													requestElement.getName(), false);
											writer.getBuffer().setLength(0);
										} catch (Exception e) {
											LOGGER.warning("Membrane SOA failed to generate template.");
											apiMap = createAPIMap(op, wsdl, verb, resourcePath, xmlUtils);
										}
									} else {
										apiMap = createAPIMap(op, wsdl, verb, resourcePath, xmlUtils);
									}
								} else {
									String namespaceUri = null;
									if (requestElement.getType() != null) {
										namespaceUri = requestElement.getType().getNamespaceURI();
									} else {
										namespaceUri = requestElement.getEmbeddedType().getNamespaceUri();
									}
									String prefix = getPrefix(namespaceUri);

									if (soapVersion.equalsIgnoreCase(SOAP_11)) {
										xmlUtils.generateRootNamespaceXSLT(SOAP2API_XSLT11_TEMPLATE, SOAP2API_XSL,
												op.getName(), prefix, requestElement.getName(), namespaceUri,
												namespace);
									} else {
										xmlUtils.generateRootNamespaceXSLT(SOAP2API_XSLT12_TEMPLATE, SOAP2API_XSL,
												op.getName(), prefix, requestElement.getName(), namespaceUri,
												namespace);
									}

									TypeDefinition typeDefinition = null;

									if (requestElement.getEmbeddedType() != null) {
										typeDefinition = requestElement.getEmbeddedType();
									} else {
										typeDefinition = getTypeFromSchema(requestElement.getType(), wsdl.getSchemas());
									}
									if (typeDefinition instanceof ComplexType) {
										ComplexType ct = (ComplexType) typeDefinition;
										xpathElement.put(level, requestElement.getName());
										parseSchema(ct.getModel(), wsdl.getSchemas(), requestElement.getName(),
												namespaceUri, prefix);
									}
									if (TOO_MANY) {
										LOGGER.warning(op.getName()
												+ ": Too many nested schemas or elements. Skipping XSLT gen. Manual intervention necessary");
										ruleList.clear();
										apiMap = new APIMap("", "", resourcePath, verb, requestElement.getName(),
												false);
									} else {
										// rule list is > 0, there are
										// additional
										// namespaces to add
										if (ruleList.size() > 0) {
											RuleSet rs = new RuleSet();
											rs.addRuleList(ruleList);
											xmlUtils.generateOtherNamespacesXSLT(SOAP2API_XSL, op.getName(),
													rs.getTransform(soapVersion), namespace);
											ruleList.clear();
											apiMap = new APIMap("", "", resourcePath, verb, requestElement.getName(),
													true);
										} else {
											apiMap = new APIMap("", "", resourcePath, verb, requestElement.getName(),
													false);
										}
									}
								}
							} else {// if the request element is null, it
									// appears membrane soap failed again.
									// attempting manual build
								apiMap = createAPIMap(op, wsdl, verb, resourcePath, xmlUtils);
							}
						}
					}
				}
				messageTemplates.put(op.getName(), apiMap);
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
						if (selectedOperationList.size() > 0 && !selectedOperationList.containsKey(bop.getName())) {
							// the current operations is not in the list; skip.
							continue;
						}
						if (bnd.getBinding() instanceof AbstractSOAPBinding) {
							String soapAction = getOperationSOAPAction(bop);
							LOGGER.fine("Found Operation Name: " + bop.getName() + " SOAPAction: "
									+ soapAction);
							APIMap apiM = messageTemplates.get(bop.getName());
							apiM.setSoapAction(soapAction);
							messageTemplates.put(bop.getName(), apiM);
						}
					}
				}
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private String getOperationSOAPAction(BindingOperation bop) {
		ExtensibilityOperation op = bop.getOperation();
		if (op == null) {
			return null;
		}

		return op.getSoapAction();
	}

	private String generateOAS() throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		@SuppressWarnings("resource")
		String oasTemplate = new Scanner(getClass().getResourceAsStream(OAS_TEMPLATE), "UTF-8").useDelimiter("\\A")
				.next();
        JsonObject oasObject = new JsonParser().parse(oasTemplate).getAsJsonObject();
		JsonObject paths = oasObject.getAsJsonObject("paths");
		JsonObject info = oasObject.getAsJsonObject("info");
		JsonObject operation = null;
		JsonObject operationDetails = null;
		JsonArray parameters = null;

		String verb = "";

		definitions = oasObject.getAsJsonObject("definitions");

		info.addProperty("title", proxyName);
		oasObject.addProperty("host", "@request.header.host#");
		oasObject.addProperty("basePath", basePath);

		Binding binding = port.getBinding();
		PortType portType = binding.getPortType();
		HashMap<String, SelectedOperation> selectedOperationList = selectedOperations.getSelectedOperations();

		for (Operation op : portType.getOperations()) {
			// the current operations is not in the list; skip.
			if (selectedOperationList.size() > 0 && !selectedOperationList.containsKey(op.getName())) {
				continue;
			}

			operation = new JsonObject();
			operationDetails = new JsonObject();

			String resourcePath = operationsMap.getResourcePath(op.getName(), selectedOperationList);
			if (!ALLPOST) {
				verb = operationsMap.getVerb(op.getName(), selectedOperationList).toLowerCase();
			} else { // else POST
				verb = new String("POST").toLowerCase();
			}

			if (RPCSTYLE) {
				parseParts(op.getInput().getMessage().getParts(), wsdl.getSchemas(), op.getName(),
						OASUtils.createComplexType(op.getName(), "0", "1"));

				if (!verb.equalsIgnoreCase("GET")) {
					parameters = OASUtils.getBodyParameter(op.getName());
				} else {
					parameters = OASUtils.getQueryParameters(queryParams);
				}
				queryParams.clear();

			} else {
				if (op.getInput().getMessage().getParts().size() == 0) {
					// TODO: handle 0 parts
				} else {
					com.predic8.schema.Element eInput = op.getInput().getMessage().getParts().get(0).getElement();
					getOASDefinitions(wsdl, eInput);

					if (!verb.equalsIgnoreCase("GET")) {
						parameters = OASUtils.getBodyParameter(eInput.getName());
					} else {
						parameters = OASUtils.getQueryParameters(queryParams);
					}
				}

				queryParams.clear();

				try {
					if (op.getOutput() != null) {
						if (op.getOutput().getMessage().getParts().size() > 0) {
							com.predic8.schema.Element eOutput = op.getOutput().getMessage().getParts().get(0).getElement();
							if (eOutput != null) {
								getOASDefinitions(wsdl, eOutput);
								operationDetails.add("responses", OASUtils.getResponse(eOutput.getName()));
							}
						}
					}
				} catch (Exception e) {
					//Ignore any errors here. Just don't generate the OAS for this portion
				}
			}

			operationDetails.addProperty("description", "Implements WSDL operation " + op.getName());
			operationDetails.add("parameters", parameters);

			operation.add(verb, operationDetails);

			if (paths.has(resourcePath) && !paths.get(resourcePath).isJsonNull()) {
				JsonObject resource = paths.getAsJsonObject(resourcePath);
				resource.add(verb.toLowerCase(), operationDetails);
			} else {
				paths.add(resourcePath, operation);
			}
		}

        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        oasContent = gson.toJson(oasObject);
		LOGGER.fine(oasContent);

		LOGGER.info("Generated OpenAPI Spec successfully.");
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		return "";
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

					File jsFolder = new File(
							apiproxy.getAbsolutePath() + File.separator + "resources" + File.separator + "jsc");
					jsFolder.mkdirs();

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

	public InputStream begin(String proxyDescription, String wsdlPath) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		LOGGER.fine("Preparing target folder");
		String zipFolder = null;
		Path tempDirectory = null;
		InputStream is = null;
		GenerateBundle generateBundle = new GenerateBundle();

		try {
			if (buildFolder == null) {
				tempDirectory = Files.createTempDirectory(null);
				buildFolder = tempDirectory.toAbsolutePath().toString();
			}
			zipFolder = buildFolder + File.separator + "apiproxy";

			// prepare the target folder (create apiproxy folder and sub-folders
			if (prepareTargetFolder()) {

				// if not passthru read conf file to interpret soap operations
				// to resources
				if (!PASSTHRU) {
					operationsMap.readOperationsMap(opsMap);
					LOGGER.info("Read operations map");
				}

				getWSDLDetails(wsdlPath);

				// generate OpenAPI Specification
				generateOAS();
				// parse the wsdl
				parseWSDL();
				LOGGER.info("Parsed WSDL Successfully.");

				if (!DESCSET) {
					proxyDescription += serviceName;
				}

				LOGGER.info("Base Path: " + basePath + "\nWSDL Path: " + wsdlPath);
				LOGGER.info("Build Folder: " + buildFolder + "\nSOAP Version: " + soapVersion);
				LOGGER.info("Proxy Name: " + proxyName + "\nProxy Description: " + proxyDescription);

				// create the basic proxy structure from templates
				writeAPIProxy(proxyDescription);
				LOGGER.info("Generated Apigee proxy file.");

				if (!PASSTHRU) {
					LOGGER.info("Generated SOAP Message Templates.");
					writeSOAP2APIProxyEndpoint(proxyDescription);
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
					writeSOAPPassThruProxyEndpointConditions(proxyDescription);
				}

				File file = generateBundle.build(zipFolder, proxyName);
				LOGGER.info("Generated Apigee Edge API Bundle file: " + proxyName + ".zip");
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				return new ByteArrayInputStream(Files.readAllBytes(file.toPath()));
			} else {
				LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
				}.getClass().getEnclosingMethod().getName());
				throw new TargetFolderException(
						"Erorr is preparing target folder; target folder not empty " + buildFolder);
			}
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
			throw e;
		} finally {
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
		System.out.println("-oauth=<true|false>       default is false");
		System.out.println("-apikey=<true|false>      default is false");
		System.out.println("-quota=<true|false>       default is false; works only if apikey or oauth is set");
		System.out.println("-basepath=specify base path");
		System.out.println("-cors=<true|false>        default is false");
		System.out.println("-debug=<true|false>       default is false");
		System.out.println("");
		System.out.println("");
		System.out.println("Examples:");
		System.out.println("$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\"");
		System.out.println(
				"$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\" -passthru=true");
		System.out.println(
				"$ java -jar wsdl2apigee.jar -wsdl=\"https://paypalobjects.com/wsdl/PayPalSvc.wsdl\" -vhosts=secure");
		System.out.println("");
		System.out.println("OpsMap:");
		System.out.println("A file that maps WSDL operations to HTTP Verbs. A Sample Ops Mapping file looks like:");
		System.out.println("");
		System.out.println(
				"{\r\n  \"proxywriter\": {\r\n    \"get\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"get\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"inq\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"search\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"list\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"retrieve\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"post\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"create\",\r\n          \"location\": \"contains\"\r\n        },\r\n        {\r\n          \"pattern\": \"add\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"process\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"put\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"update\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"change\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"modify\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"set\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    },\r\n    \"delete\": {\r\n      \"operation\": [\r\n        {\r\n          \"pattern\": \"delete\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"remove\",\r\n          \"location\": \"beginsWith\"\r\n        },\r\n        {\r\n          \"pattern\": \"del\",\r\n          \"location\": \"beginsWith\"\r\n        }\r\n      ]\r\n    }\r\n  }\r\n}");

		System.out.println("\n\n");
		System.out.println("Examples:");
		System.out.println(
				"$ java -jar wsdl2apigee.jar -wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\" -oas=true");
	}

	private static List<WsdlDefinitions.Port> convertPorts(List<com.predic8.wsdl.Port> ports,
			List<PortType> portTypes) {
		List<WsdlDefinitions.Port> list = new ArrayList<>(ports.size());
		for (com.predic8.wsdl.Port port : ports) {
			final Object protocol = port.getBinding().getProtocol();
			if (protocol != null) {
				final String protocolStr = protocol.toString();
				if (SOAP_11.equalsIgnoreCase(protocolStr) || SOAP_12.equalsIgnoreCase(protocolStr)) {
					list.add(new WsdlDefinitions.Port(port.getName(), convertOperations(port.getBinding(), portTypes)));
				}
			}
		}
		return list;
	}

	private static List<WsdlDefinitions.Operation> convertOperations(Binding binding, List<PortType> portTypes) {
		List<WsdlDefinitions.Operation> list = new ArrayList<>();
		OpsMap opsMap = null;
		try {
			opsMap = new OpsMap(OPSMAPPING_TEMPLATE);
		} catch (Exception e) {
		}

		binding.getOperations();
		for (BindingOperation bindingOperation : binding.getOperations()) {
			final String operationName = bindingOperation.getName();
			final WsdlDefinitions.Operation operation = new WsdlDefinitions.Operation(operationName,
					findDocForOperation(operationName, portTypes), opsMap.getVerb(operationName, null),
					opsMap.getResourcePath(operationName, null), null);
			list.add(operation);
		}
		return list;
	}

	private static String findDocForOperation(String operationName, List<PortType> portTypes) {
		for (PortType portType : portTypes) {
			final Operation operation = portType.getOperation(operationName);
			if (operation != null) {
				return operation.getDocumentation() != null ? operation.getDocumentation().getContent() : "";
			}
		}
		return "";
	}

	private static WsdlDefinitions definitionsToWsdlDefinitions(Definitions definitions) {
		List<WsdlDefinitions.Service> services = new ArrayList<>();
		definitions.getServices();
		for (Service service : definitions.getServices()) {
			final WsdlDefinitions.Service service1 = new WsdlDefinitions.Service(service.getName(),
					convertPorts(service.getPorts(), definitions.getPortTypes()));
			services.add(service1);
		}
		return new WsdlDefinitions(services);
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
		// set virtual hosts
		opt.getSet().addOption("vhosts", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set build path
		opt.getSet().addOption("build", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// add verify oauth policy
		opt.getSet().addOption("oauth", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// add verify apikey policy
		opt.getSet().addOption("apikey", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// add impose quota policy
		opt.getSet().addOption("quota", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// set basepath
		opt.getSet().addOption("basepath", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// add enable cors conditions
		opt.getSet().addOption("cors", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		// generate OAS spec
		opt.getSet().addOption("oas", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
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
			if (opt.getSet().isSet("cors")) {
				LOGGER.warning("WARNING: cors can only be enabled for SOAP to REST. This flag will be ignored.");
			}
			if (opt.getSet().isSet("desc")) {
				// React to option -des
				proxyDescription = opt.getSet().getOption("desc").getResultValue(0);
				genProxy.setDesc(true);
			} else {
				proxyDescription = "Generated SOAP proxy from ";
			}
		} else {
			genProxy.setPassThru(false);
			if (opt.getSet().isSet("desc")) {
				// React to option -des
				proxyDescription = opt.getSet().getOption("desc").getResultValue(0);
				genProxy.setDesc(true);
			} else {
				proxyDescription = "Generated SOAP to API proxy from ";
			}
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
			genProxy.setOpsMap(opt.getSet().getOption("opsmap").getResultValue(0));
		} else {
			genProxy.setOpsMap(GenerateProxy.OPSMAPPING_TEMPLATE);
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

		if (opt.getSet().isSet("basepath")) {
			genProxy.setBasePath(opt.getSet().getOption("basepath").getResultValue(0));
		}

		if (opt.getSet().isSet("cors")) {
			genProxy.setCORS(new Boolean(opt.getSet().getOption("cors").getResultValue(0)));
		}

		if (opt.getSet().isSet("oauth")) {
			genProxy.setOAuth(new Boolean(opt.getSet().getOption("oauth").getResultValue(0)));
			if (opt.getSet().isSet("quota")) {
				genProxy.setQuotaOAuth(new Boolean(opt.getSet().getOption("quota").getResultValue(0)));
			}
		}

		if (opt.getSet().isSet("apikey")) {
			genProxy.setAPIKey(new Boolean(opt.getSet().getOption("apikey").getResultValue(0)));
			if (opt.getSet().isSet("quota")) {
				genProxy.setQuotaAPIKey(new Boolean(opt.getSet().getOption("quota").getResultValue(0)));
			}
		}

		if (!opt.getSet().isSet("apikey") && !opt.getSet().isSet("oauth") && opt.getSet().isSet("quota")) {
			LOGGER.warning("WARNING: Quota is applicable with apikey or oauth flags. This flag will be ignored");
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

	public static InputStream generateProxy(GenerateProxyOptions generateProxyOptions) throws Exception {
		GenerateProxy genProxy = new GenerateProxy();
		genProxy.setOpsMap(OPSMAPPING_TEMPLATE);

		genProxy.setPassThru(generateProxyOptions.isPassthrough());
		genProxy.setBasePath(generateProxyOptions.getBasepath());
		genProxy.setVHost(generateProxyOptions.getvHosts());
		genProxy.setPort(generateProxyOptions.getPort());
		genProxy.setCORS(generateProxyOptions.isCors());
		genProxy.setAPIKey(generateProxyOptions.isApiKey());
		genProxy.setOAuth(generateProxyOptions.isOauth());
		genProxy.setQuotaAPIKey(generateProxyOptions.isApiKey() && generateProxyOptions.isQuota());
		genProxy.setQuotaOAuth(generateProxyOptions.isOauth() && generateProxyOptions.isQuota());
		if (generateProxyOptions.getOperationsFilter() != null
				&& generateProxyOptions.getOperationsFilter().length() > 0) {
			genProxy.setSelectedOperationsJson(generateProxyOptions.getOperationsFilter());
		}
		return genProxy.begin(generateProxyOptions.getDescription() != null ? generateProxyOptions.getDescription()
				: "Generated SOAP to API proxy", generateProxyOptions.getWsdl());
	}

	public static WsdlDefinitions parseWsdl(String wsdl) throws ErrorParsingWsdlException {
		final WSDLParser2 wsdlParser = new WSDLParser2();
		try {
			final Definitions definitions = wsdlParser.parse(wsdl);
			return definitionsToWsdlDefinitions(definitions);
		} catch (com.predic8.xml.util.ResourceDownloadException e) {
			String message = formatResourceError(e);
			throw new ErrorParsingWsdlException(message, e);
		} catch (Throwable t) {
			String message = t.getLocalizedMessage();
			if (message == null) {
				message = t.getMessage();
			}
			if (message == null) {
				message = "Error processing WSDL.";
			}
			throw new ErrorParsingWsdlException(message, t);
		}
	}

	private static String formatResourceError(com.predic8.xml.util.ResourceDownloadException e) {
		StringBuffer errorMessage = new StringBuffer("Could not download resource.");
		String rootCause = e.getRootCause().getLocalizedMessage();
		if (!rootCause.isEmpty()) {
			int pos = rootCause.indexOf("status for class:");
			if (pos == -1) {
				errorMessage.append(" " + rootCause + ".");
			}
		}

		return (errorMessage.toString());

	}

    /**
     * Copy and modify for Java com.predic8.wsdl.WSDLParser.groovy. Then update it to protect against XSS attacks
	 * in getToken
     */
	private static class WSDLParser2 {

		private ExternalResolver resourceResolver = new ExternalResolver();

		public Definitions parse(String input)  {
			final WSDLParserContext wsdlParserContext = new WSDLParserContext();
			wsdlParserContext.setInput(input);
			try {
				return parse(wsdlParserContext);
			}
			catch (XMLStreamException e) {
				throw new RuntimeException(e.getMessage());
			}
		}

		protected Definitions parseLocal(XMLStreamReader token, AbstractParserContext ctx) throws XMLStreamException {
			final String encoding = token.getCharacterEncodingScheme();
			if( encoding == null || (!encoding.equals("UTF-8") && !encoding.equals("UTF-16"))) {
				final WSIResult wsiResults = new WSIResult();
				wsiResults.setRule("R4003");
				ArrayList<WSIResult> wsiResultsList = (ArrayList<WSIResult>) ctx.getWsiResults();
				wsiResultsList.add(wsiResults);
			}
			Definitions definitions = null;
			while(token.hasNext()) {
				if (token.isStartElement()) {
					if(token.getName().equals(Definitions.ELEMENTNAME)) {
						definitions = new Definitions();
						definitions.setBaseDir(ctx.getNewBaseDir());
						definitions.setResourceResolver(ctx.getResourceResolver());
						definitions.setRegistry(new Registry());
						((WSDLParserContext) ctx).getWsdlElementOrder().add(definitions);
						definitions.parse(token, ctx);
					}
					else if(token.getName().getNamespaceURI().equals(Consts.WSDL20_NS)) {
						throw new WSDLVersion2NotSupportedException("WSDL 2.0 is not supported yet.");
					}
					else {
						throw new WrongGrammarException("Expected root element '{http://schemas.xmlsoap.org/wsdl/}definitions' for the WSDL document but was '${token.name}'.", token.getName(), token.getLocation());
					}
				}
				if(token.hasNext()) token.next();
			}
			if(definitions == null) throw new RuntimeException("The parsed document ${ctx.input} is not a valid WSDL document.");
			return definitions;

		}

		protected Definitions parse(AbstractParserContext ctx) throws XMLStreamException {
			updateCtx(ctx);
			return parseLocal(getResourceToken(ctx), ctx);
		}

		private void updateCtx(AbstractParserContext ctx) {
			if (ctx.getBaseDir() == null) {
				ctx.setBaseDir("");
			}
			ctx.setNewBaseDir(HTTPUtil.updateBaseDir(ctx.getInput(), ctx.getBaseDir()));
			if (ctx.getResourceResolver() == null) {
				ctx.setResourceResolver(resourceResolver);
			}
			if (ctx.getWsiResults() == null) {
				ctx.setWsiResults(new ArrayList<WSIResult>());
			}
			if (ctx.getErrors() == null) {
				ctx.setErrors(new ArrayList<ValidationError>());
			}
		}

		private XMLStreamReader getResourceToken(AbstractParserContext ctx) throws XMLStreamException {
			return getToken(resourceResolver.resolve(ctx.getInput(), ctx.getBaseDir()));
		}

		private XMLStreamReader getToken(Object res) throws XMLStreamException {
			final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
			// XSS Protection added here
			xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
			xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
			xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			// End XSS Protection
			if (res instanceof InputStream) {
				return xmlInputFactory.createXMLStreamReader((InputStream) res);
			}
			else if (res instanceof Reader) {
				return xmlInputFactory.createXMLStreamReader((Reader) res);
			}
			return null;
		}
	}
}
