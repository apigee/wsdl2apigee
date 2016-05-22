package com.apigee.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONPathGenerator {

	private static final Logger LOGGER = Logger.getLogger(JSONPathGenerator.class.getName());

	static {
		LOGGER.setLevel(Level.WARNING);
		ConsoleHandler handler = new ConsoleHandler();
		// PUBLISH this level
		handler.setLevel(Level.WARNING);
		LOGGER.addHandler(handler);
	}

	private int counter = 0;
	private HashMap<Integer, String> map;
	private HashSet<String> jsonPathList;

	public JSONPathGenerator() {
		map = new HashMap<Integer, String>();
		jsonPathList = new HashSet<String>();
	}

	public Map<String, String> parse(JSONObject json, Map<String, String> out) throws Exception {
		Iterator<String> keys = json.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			String val = null;
			try {
				JSONArray array = json.getJSONArray(key);
				for (int i = 0; i < array.length(); i++) {
					JSONObject rec = array.getJSONObject(i);
					counter++;
					map.put(counter, key + "[" + i + "]");
					parse(rec, out);
				}

			} catch (Exception e) {
				try {
					JSONObject value = json.getJSONObject(key);
					if (value.length() != 0) {
						counter++;
						map.put(counter, key);
					}
					parse(value, out);
				} catch (Exception ex) {
					val = json.getString(key);
				}
				if (val != null) {
					out.put(key, val);
					if (key.equalsIgnoreCase("content")) {
						jsonPathList.add(createJSONPath(counter, key));
					}
				}
			}
		}
		counter--;
		return out;
	}

	private String createJSONPath(int counter, String key) {
		String jsonpath = "$";
		for (int i = 1; i <= counter; i++) {
			jsonpath += "." + map.get(i);
		}
		return (jsonpath);
	}

	public HashSet<String> getJsonPath() {
		return jsonPathList;
	}
}
