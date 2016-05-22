package com.apigee.proxywriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.apigee.policywriter.ExtractPolicy;
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

	private List<String> fileList;

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

	private static final int BUFFER = 2048;
	private static boolean PASSTHRU = false;

	private List<ExtractPolicy> extractList;
	private Map<String, String> buildSOAPList;
	private Map<String, KeyValue<String, String>> messageTemplates;

	private StringWriter errors;
	
	static {
		LOGGER.setUseParentHandlers(false);
		
		Handler[] handlers = LOGGER.getHandlers();
		for(Handler handler : handlers)
		{
	        if(handler.getClass() == ConsoleHandler.class)
	            LOGGER.removeHandler(handler);
		}
		LOGGER.addHandler(handler);
	}

	private void zipIt(String zipFile, String targetFolder) {
		byte[] buffer = new byte[BUFFER];
		String source = "";
		File f = new File(zipFile);
		FileOutputStream fos = null;
		ZipOutputStream zos = null;
		try {
			try {
				source = targetFolder.substring(targetFolder.lastIndexOf(File.separator), targetFolder.length());
			} catch (Exception e) {
				source = targetFolder;
			}

			if (f.exists()) {
				File newFile = new File(f.getParent(),
						zipFile.substring(0, zipFile.lastIndexOf(".")) + java.lang.System.currentTimeMillis() + ".zip");
				Files.move(f.toPath(), newFile.toPath());
			}

			fos = new FileOutputStream(zipFile);
			zos = new ZipOutputStream(fos);

			LOGGER.fine("Output to Zip : " + zipFile);

			FileInputStream in = null;

			for (String file : this.fileList) {
				LOGGER.fine("File Added : " + file);
				ZipEntry ze = new ZipEntry(source + File.separator + file);
				zos.putNextEntry(ze);
				try {
					in = new FileInputStream(targetFolder + File.separator + file);
					int len;
					while ((len = in.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}
				} finally {
					in.close();
				}
			}

			zos.closeEntry();
			LOGGER.fine("Folder successfully compressed");

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			try {
				zos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void generateFileList(File node, String targetFolder) {

		// add file only
		if (node.isFile()) {
			fileList.add(generateZipEntry(node.toString(), targetFolder));

		}

		if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String filename : subNote) {
				generateFileList(new File(node, filename), targetFolder);
			}
		}
	}

	private String generateZipEntry(String file, String targetFolder) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		LOGGER.fine("File: " + file + " File Length: " + file.length());
		LOGGER.fine("Target Folder: " + targetFolder + " Target Folder length: " + targetFolder.length());
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return file.substring(targetFolder.length() + 1, file.length());
	}

	private GenerateProxy() {
		fileList = new ArrayList<String>();
		extractList = new ArrayList<ExtractPolicy>();
		buildSOAPList = new HashMap<String, String>();
		messageTemplates = new HashMap<String, KeyValue<String, String>>();
		errors = new StringWriter();
	}

	private void writeAPIProxy(String APIPROXY_TEMPLATE, String proxyName, String proxyDescription, String targetFolder)
			throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();

		LOGGER.finest("Read API Proxy template file");
		Document apiTemplateDocument = xmlUtils.readXML(APIPROXY_TEMPLATE);
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

	private void writeSOAP2APIProxyEndpoint(String proxyName, String basePath, Definitions wsdl, String targetFolder)
			throws Exception {

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
		Node policies = apiTemplateDocument.getElementsByTagName("Policies").item(0);

		Node flows = proxyDefault.getElementsByTagName("Flows").item(0);
		Node flow;
		Node flowDescription;
		Node request;
		Node response;
		Node condition;
		Node step1, step2;
		Node name1, name2;

		ExtractPolicy extractPolicy;

		for (PortType pt : wsdl.getPortTypes()) {
			for (Operation op : pt.getOperations()) {
				String operationName = op.getName();
				String extractPolicyName = "";
				String buildSOAPPolicy = op.getName() + "-build-soap";
				String httpVerb = determineHTTPVerb(operationName);
				String resourcePath = determineResourcePath(operationName);

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
				// store policy names for later use
				buildSOAPList.put(operationName, buildSOAPPolicy);
				extractPolicy = new ExtractPolicy(operationName, extractPolicyName, httpVerb);
				extractList.add(extractPolicy);
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

	private void writeSOAP2APIExtractPolicies(String targetFolder) throws Exception {
		XMLUtils xmlUtils = new XMLUtils();
		KeyValue<String, String> keyValue;
		Document extractTemplate = xmlUtils.readXML(SOAP2API_EXTRACT_TEMPLATE);

		for (ExtractPolicy extractPolicy : extractList) {
			String operationName = extractPolicy.getOperationName();// entry.getKey();
			String policyName = extractPolicy.getPolicyName();// entry.getValue();
			String httpVerb = extractPolicy.getHttpVerb();
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
					e.printStackTrace();
				}
			}

			xmlUtils.writeXML(extractPolicyXML, targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator + policyName + ".xml");
		}
	}

	private String determineHTTPVerb(String operationName) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		if (OpsMap.getGetOps(operationName) != null) {
			LOGGER.fine(operationName + " matches GET verb");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return "GET";
		} else if (OpsMap.getPutOps(operationName) != null) {
			LOGGER.fine(operationName + " matches PUT verb");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return "PUT";
		} else if (OpsMap.getPostOps(operationName) != null) {
			LOGGER.fine(operationName + " matches POST verb");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return "POST";
		} else if (OpsMap.getDeleteOps(operationName) != null) {
			LOGGER.fine(operationName + " matches DELETE verb");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return "DELETE";
		} else {
			LOGGER.fine(operationName + " does not match known verbs. Default to GET verb");
			LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			return "GET";
		}
	}

	private String determineResourcePath(String operationName) {

		// TODO: Handle verbs anywhere in the operation??
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		String resourcePath = operationName;
		String[] verbs = OpsMap.getOpsList();

		for (String verb : verbs) {
			if (operationName.toLowerCase().startsWith(verb) && !operationName.toLowerCase().startsWith("address")) {
				resourcePath = operationName.toLowerCase().replaceFirst(verb, "");
				LOGGER.fine("Replacing " + operationName + " with " + resourcePath.toLowerCase());
				break;
			}
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return "/" + resourcePath.toLowerCase();
	}

	private void writeSOAP2APIAssignMessagePolicies(String targetEndpoint, String targetFolder) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();
		Document assignTemplate = xmlUtils.readXML(SOAP2API_ASSIGN_TEMPLATE);

		for (Map.Entry<String, String> entry : buildSOAPList.entrySet()) {
			String operationName = entry.getKey();
			String policyName = entry.getValue();

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
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeTargetEndpoint(String TARGET_TEMPLATE, String targetURL, String targetFolder) throws Exception {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		XMLUtils xmlUtils = new XMLUtils();

		Document targetDefault = xmlUtils.readXML(TARGET_TEMPLATE);
		Node urlNode = targetDefault.getElementsByTagName("URL").item(0);

		if (targetURL != null && targetURL.equalsIgnoreCase("") != true) {
			urlNode.setTextContent(targetURL);
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

	private void writeSOAPPassThruStdPolicies(String targetFolder) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			String sourcePath = "." + File.separator + "templates" + File.separator + "soappassthru" + File.separator;
			String targetPath = targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			LOGGER.fine("Source Path: " + sourcePath);
			LOGGER.fine("Target Path: " + targetPath);

			Files.copy(Paths.get(sourcePath + "Extract-Operation-Name.xml"),
					Paths.get(targetPath + "Extract-Operation-Name.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(Paths.get(sourcePath + "Invalid-SOAP.xml"), Paths.get(targetPath + "Invalid-SOAP.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.severe(errors.toString());
			throw new IOException(e);
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAP2APIStdPolicies(String targetFolder) throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		try {
			String sourcePath = "." + File.separator + "templates" + File.separator + "soap2api" + File.separator;
			String targetPath = targetFolder + File.separator + "apiproxy" + File.separator + "policies"
					+ File.separator;
			LOGGER.fine("Source Path: " + sourcePath);
			LOGGER.fine("Target Path: " + targetPath);

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
			Files.copy(Paths.get(sourcePath + "unknown-resource.xml"), Paths.get(targetPath + "unknown-resource.xml"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.severe(errors.toString());
			throw new IOException(e);
		}
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
	}

	private void writeSOAPPassThruProxyEndpointConditions(String basePath, Definitions wsdl, String targetFolder,
			String soapVersion) throws Exception {
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

	private Definitions parseWSDL(String wsdlPath) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		WSDLParser parser = new WSDLParser();
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return parser.parse(wsdlPath);
	}

	private String getTargetEndpoint(Definitions wsdl) {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		Service service = wsdl.getServices().get(0);
		Port port = service.getPorts().get(0);
		LOGGER.exiting(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return port.getAddress().getLocation();
	}

	private KeyValue<String, String> getProxyNameAndBasePath(Definitions wsdl, String wsdlPath) throws Exception {

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

	private void storeMessages(String soapVersion, Definitions wsdl) throws Exception {
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
							creator.createRequest(port.getName(), op.getName(), binding.getName());
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

	private boolean prepareTargetFolder(String targetFolder) {
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

	private void readOperationsMap() throws Exception {
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		XMLUtils xmlUtils = new XMLUtils();
		Document opsMappingXML = xmlUtils.readXML(OPSMAPPING_TEMPLATE);

		Node getNode = opsMappingXML.getElementsByTagName("get").item(0);
		if (getNode != null) {
			NodeList getOpsList = getNode.getChildNodes();
			for (int i = 0; i < getOpsList.getLength(); i++) {
				Node currentNode = getOpsList.item(i);
				if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
					NamedNodeMap locationNodeMap = currentNode.getAttributes();
					Node locationAttr = locationNodeMap.getNamedItem("location");
					LOGGER.fine("Found GET: " + currentNode.getTextContent());
					OpsMap.addGetOps(currentNode.getTextContent(), locationAttr.getNodeValue());
				}
			}
		}

		Node postNode = opsMappingXML.getElementsByTagName("post").item(0);
		if (postNode != null) {
			NodeList postOpsList = postNode.getChildNodes();
			for (int i = 0; i < postOpsList.getLength(); i++) {
				Node currentNode = postOpsList.item(i);
				if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
					NamedNodeMap locationNodeMap = currentNode.getAttributes();
					Node locationAttr = locationNodeMap.getNamedItem("location");
					LOGGER.fine("Found POST: " + currentNode.getTextContent());
					OpsMap.addPostOps(currentNode.getTextContent(), locationAttr.getNodeValue());
				}
			}
		}

		Node putNode = opsMappingXML.getElementsByTagName("put").item(0);
		if (putNode != null) {
			NodeList putOpsList = putNode.getChildNodes();
			for (int i = 0; i < putOpsList.getLength(); i++) {
				Node currentNode = putOpsList.item(i);
				if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
					NamedNodeMap locationNodeMap = currentNode.getAttributes();
					Node locationAttr = locationNodeMap.getNamedItem("location");
					LOGGER.fine("Found PUT: " + currentNode.getTextContent());
					OpsMap.addPutOps(currentNode.getTextContent(), locationAttr.getNodeValue());
				}
			}
		}

		Node deleteNode = opsMappingXML.getElementsByTagName("delete").item(0);
		if (deleteNode != null) {
			NodeList deleteOpsList = deleteNode.getChildNodes();
			for (int i = 0; i < deleteOpsList.getLength(); i++) {
				Node currentNode = deleteOpsList.item(i);
				if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
					NamedNodeMap locationNodeMap = currentNode.getAttributes();
					Node locationAttr = locationNodeMap.getNamedItem("location");
					LOGGER.fine("Found DELETE: " + currentNode.getTextContent());
					OpsMap.addDeleteOps(currentNode.getTextContent(), locationAttr.getNodeValue());
				}
			}
		}
		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}

	public void begin(String proxyDescription, String wsdlPath, String targetFolder, String soapVersion) {

		LOGGER.entering(GenerateProxy.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		LOGGER.fine("Preparing target folder");
		String zipFolder = targetFolder + File.separator + "apiproxy";

		try {
			if (prepareTargetFolder(targetFolder)) {
				Definitions wsdl = parseWSDL(wsdlPath);
				LOGGER.info("Parsed WSDL Successfully.");

				KeyValue<String, String> map = getProxyNameAndBasePath(wsdl, wsdlPath);
				String proxyName = map.getKey();
				String basePath = map.getValue();

				LOGGER.info("Proxy Name: " + proxyName + "\nProxy Description: " + proxyDescription);
				LOGGER.info("Base Path: " + basePath + "\nWSDL Path: " + wsdlPath);
				LOGGER.info("Target Folder: " + targetFolder + "\nSOAP Version: " + soapVersion);

				if (!PASSTHRU) {
					readOperationsMap();
					LOGGER.info("Read operations map");
				}

				if (PASSTHRU)
					writeAPIProxy(SOAPPASSTHRU_APIPROXY_TEMPLATE, proxyName, proxyDescription, targetFolder);
				else
					writeAPIProxy(SOAP2API_APIPROXY_TEMPLATE, proxyName, proxyDescription, targetFolder);
				LOGGER.info("Generated Apigee proxy file.");

				String targetEndpoint = getTargetEndpoint(wsdl);
				LOGGER.info("Retrieved WSDL endpoint: " + targetEndpoint);

				if (!PASSTHRU) {
					storeMessages(soapVersion, wsdl);
					LOGGER.info("Generated SOAP Message Templates.");
					writeSOAP2APIProxyEndpoint(proxyName, basePath, wsdl, targetFolder);
					LOGGER.info("Generated proxies XML.");
					writeSOAP2APIStdPolicies(targetFolder);
					LOGGER.info("Copied standard policies.");
					writeTargetEndpoint(SOAP2API_TARGET_TEMPLATE, targetEndpoint, targetFolder);
					LOGGER.info("Generated target XML.");
					writeSOAP2APIExtractPolicies(targetFolder);
					LOGGER.info("Generated Extract Policies.");
					writeSOAP2APIAssignMessagePolicies(targetEndpoint, targetFolder);
					LOGGER.info("Generated Assign Message Policies.");
				} else {
					writeSOAPPassThruStdPolicies(targetFolder);
					LOGGER.info("Copied standard policies.");
					writeTargetEndpoint(SOAPPASSTHRU_TARGET_TEMPLATE, targetEndpoint, targetFolder);
					LOGGER.info("Generated target XML.");
					writeSOAPPassThruProxyEndpointConditions(basePath, wsdl, targetFolder, soapVersion);
				}

				generateFileList(new File(zipFolder), zipFolder);
				zipIt(proxyName + ".zip", zipFolder);
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

		String wsdlPath = "";
		String targetFolder = "";
		String proxyDescription = "";
		String soapVersion = "";

		Options opt = new Options(args, 1);
		opt.getSet().addOption("wsdl", Separator.EQUALS, Multiplicity.ONCE);
		opt.getSet().addOption("passthru", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		opt.getSet().addOption("target", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		opt.getSet().addOption("desc", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
		opt.getSet().addOption("soap", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
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
			targetFolder = opt.getSet().getOption("target").getResultValue(0);
		} else {
			targetFolder = "." + File.separator + "build";
		}

		if (opt.getSet().isSet("passthru")) {
			// React to option -passthru
			PASSTHRU = Boolean.parseBoolean(opt.getSet().getOption("passthru").getResultValue(0));
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

		GenerateProxy genProxy = new GenerateProxy();

		// wsdlPath =
		// "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";

		// PASSTHRU = true;
		// wsdlPath = "https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl";
		// wsdlPath =
		// "http://www.konakart.com/konakart/services/KKWebServiceEng?wsdl";

		genProxy.begin(proxyDescription, wsdlPath, targetFolder, soapVersion);

	}
}
