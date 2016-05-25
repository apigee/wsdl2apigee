package com.apigee.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	
	private String temp;
	private List<String> list = new ArrayList<String>();
	private List<String> jsonPathList = new ArrayList<String>();
	private int level = 0;
	

	public JSONPathGenerator() {
		jsonPathList = new ArrayList<String>();
		temp = "";
	}

	private String removeNamespaces (String data) {
		String result = "$";
		boolean first = true;
		
		if (data.indexOf(":") == -1) {
			return data;
		}
		
		for (String s: data.split(":")) {
			if (s.indexOf('.') != -1) {
				result = result + "." + s.substring(0, s.indexOf("."));
			} else {
				if (!first) result = result + "." + s;
			}
			first = false;
		}	
		return result;//result.replaceFirst(".","");
	}
		
	public void traverse (JSONObject parent) {
        Iterator<String> keys = parent.keys();
        
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            //System.out.println(key);
            if ( parent.get(key) instanceof JSONObject ) {
            	temp = temp + key + ".";
            	JSONObject child = (JSONObject)parent.get(key);
            	++level;
            	traverse(child);
            } else if (parent.get(key) instanceof String) {
            	list.add(temp+key);
            }
        }
        
        level --;
        //finished with a parent.
        for (String s : list) {
        	if (s !=null && s.indexOf("xmlns:") == -1) {
        		jsonPathList.add(removeNamespaces(s));
        	}
        }
        
    	if (temp.charAt(temp.length()-1) == '.' && level > 0) {
        	temp = temp.substring(0, temp.length()-1);
        	temp = temp.substring(0, temp.lastIndexOf(".")+1);		
        }
        
        list.clear();
	}

	public List<String> getJsonPath() {
		return jsonPathList;
	}
}
