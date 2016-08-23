package com.apigee.utils;

public class SelectedOperation {

	private String verb;
	private String resourcePath;
	
	public SelectedOperation (String v, String r) {
		verb = v;
		resourcePath = r;
	}

	/**
	 * @return the verb
	 */
	public String getVerb() {
		return verb;
	}

	/**
	 * @return the resourcePath
	 */
	public String getResourcePath() {
		return resourcePath;
	}
	
	
}
