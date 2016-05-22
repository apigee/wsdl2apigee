package com.apigee.utils;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StringUtils {
	
	private static final Logger LOGGER = Logger.getLogger(StringUtils.class.getName());

	static {
		LOGGER.setLevel(Level.WARNING);
		ConsoleHandler handler = new ConsoleHandler();
		// PUBLISH this level
		handler.setLevel(Level.WARNING);
		LOGGER.addHandler(handler);		
	}
	
	public static String lastWordAfterDot(String word) {
		int index = word.lastIndexOf(".");
		return word.substring(index + 1, word.length());
	}
	
	public static KeyValue<String, String> proxyNameAndBasePath(String url) {

		KeyValue<String, String> map;
		int endIndex = url.indexOf(".wsdl");
		
		if (endIndex == -1) {
			endIndex = url.indexOf("?wsdl");
			if (endIndex == -1) {
				LOGGER.warning("Unable to get proxy name from WSDL. Generating default name");
				map = new KeyValue<String,String>("SOAP2REST","/soap2rest");
			}
		}
		
		int beginIndex = url.lastIndexOf("/");
		
		map = new KeyValue<String,String>(url.substring(beginIndex+1, endIndex), url.substring(beginIndex, endIndex).toLowerCase());
		
		return map;
	}
}
