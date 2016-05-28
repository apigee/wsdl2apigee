package com.apigee.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class OpsMap {

	private static final Logger LOGGER = Logger.getLogger(OpsMap.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
		ConsoleHandler handler = new ConsoleHandler();
		// PUBLISH this level
		handler.setLevel(Level.INFO);
		LOGGER.addHandler(handler);
	}

	private static List<Operation> opsMap = new ArrayList<Operation>();

	public static void addOps (String operationName, String location, String verb) {
		Operation o = new Operation(operationName, location, verb);
		opsMap.add(o);
	}
	
	public static String getOpsMap(String operationName) {
		String lcOperationName = operationName.toLowerCase();
		for (Operation o : opsMap) {
			//found key in the operation name
			if (lcOperationName.contains(o.getName())) {
				if (o.getLocation().equalsIgnoreCase("beginsWith")) {
					if (lcOperationName.startsWith(o.getName())) {
						return o.getVerb();
					}
				} else if (o.getLocation().equalsIgnoreCase("endsWith")) {
					if (lcOperationName.startsWith(o.getName())) {
						return o.getVerb();
					}
				} else { //assume contains
					return o.getVerb();
				}
			}
		}
		return "GET";
	}
	
	public static String getResourcePath (String operationName) {

		String resourcePath = operationName;
		
		for (Operation o : opsMap) {
			if (operationName.toLowerCase().startsWith(o.getName()) && !operationName.toLowerCase().startsWith("address") 
					&& !operationName.equalsIgnoreCase(o.getName())) { //don't replace the entire resource
				resourcePath = operationName.toLowerCase().replaceFirst(o.getName(), "");
				LOGGER.fine("Replacing " + operationName + " with " + resourcePath.toLowerCase());
				return "/" + resourcePath.toLowerCase();
			}
		}
		return "/" + resourcePath.toLowerCase();
	}
	
	public static void readOperationsMap(String OPSMAPPING_TEMPLATE) throws Exception {
		
		LOGGER.entering(OpsMap.class.getName(), new Object() {
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
					addOps(currentNode.getTextContent(), locationAttr.getNodeValue(), "GET");
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
					addOps(currentNode.getTextContent(), locationAttr.getNodeValue(), "POST");
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
					addOps(currentNode.getTextContent(), locationAttr.getNodeValue(), "PUT");
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
					addOps(currentNode.getTextContent(), locationAttr.getNodeValue(), "DELETE");
				}
			}
		}
		LOGGER.entering(OpsMap.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

	}
	
}
