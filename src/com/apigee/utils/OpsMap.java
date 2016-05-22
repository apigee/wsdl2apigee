package com.apigee.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpsMap {

	private static final Logger LOGGER = Logger.getLogger(OpsMap.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
		ConsoleHandler handler = new ConsoleHandler();
		// PUBLISH this level
		handler.setLevel(Level.INFO);
		LOGGER.addHandler(handler);
	}

	private static Map<String, String> getOps = new HashMap<String, String>();
	private static Map<String, String> postOps = new HashMap<String, String>();
	private static Map<String, String> deleteOps = new HashMap<String, String>();
	private static Map<String, String> putOps = new HashMap<String, String>();
	private static ArrayList<String> opsList = new ArrayList<String>();

	public static void addGetOps(String name, String location) {
		getOps.put(name, location);
		opsList.add(name);
	}

	public static void addPostOps(String name, String location) {
		postOps.put(name, location);
		opsList.add(name);
	}

	public static void addDeleteOps(String name, String location) {
		putOps.put(name, location);
		opsList.add(name);
	}

	public static void addPutOps(String name, String location) {
		deleteOps.put(name, location);
		opsList.add(name);
	}

	public static String getGetOps(String name) {

		for (String key : getOps.keySet()) {
			// found the key in the operation name
			if (name.indexOf(key) != -1) {
				String location = getOps.get(key);
				if (location.equalsIgnoreCase("beginsWith")) {
					if (name.startsWith(key)) {
						return "GET";
					}
				} else if (location.equalsIgnoreCase("endsWith")) {
					if (name.endsWith(key)) {
						return "GET";
					}
				} else if (location.equalsIgnoreCase("contains")) {
					return "GET";
				}
			}
		}
		return null;
	}

	public static String getPostOps(String name) {
		for (String key : postOps.keySet()) {
			// found the key in the operation name
			if (name.indexOf(key) != -1) {
				String location = postOps.get(key);
				if (location.equalsIgnoreCase("beginsWith")) {
					if (name.startsWith(key)) {
						return "POST";
					}
				} else if (location.equalsIgnoreCase("endsWith")) {
					if (name.endsWith(key)) {
						return "POST";
					}
				} else if (location.equalsIgnoreCase("contains")) {
					return "POST";
				}
			}
		}
		return null;
	}

	public static String getDeleteOps(String name) {
		for (String key : deleteOps.keySet()) {
			// found the key in the operation name
			if (name.indexOf(key) != -1) {
				String location = deleteOps.get(key);
				if (location.equalsIgnoreCase("beginsWith")) {
					if (name.startsWith(key)) {
						return "DELETE";
					}
				} else if (location.equalsIgnoreCase("endsWith")) {
					if (name.endsWith(key)) {
						return "DELETE";
					}
				} else if (location.equalsIgnoreCase("contains")) {
					return "DELETE";
				}
			}
		}
		return null;
	}

	public static String getPutOps(String name) {
		for (String key : putOps.keySet()) {
			// found the key in the operation name
			if (name.indexOf(key) != -1) {
				String location = putOps.get(key);
				if (location.equalsIgnoreCase("beginsWith")) {
					if (name.startsWith(key)) {
						return "PUT";
					}
				} else if (location.equalsIgnoreCase("endsWith")) {
					if (name.endsWith(key)) {
						return "PUT";
					}
				} else if (location.equalsIgnoreCase("contains")) {
					return "PUT";
				}
			}
		}
		return null;
	}

	public static String[] getOpsList() {
		String[] opsArray = new String[opsList.size()];
		opsArray = opsList.toArray(opsArray);
		return opsArray;
	}
}
