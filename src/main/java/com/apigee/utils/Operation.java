package com.apigee.utils;

public class Operation {

	private String verb;
	private String pattern;
	private String location;

	/**
	 * @return the verb
	 */
	public String getVerb() {
		return verb;
	}

	/**
	 * @return the name
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}
	
	Operation(String p, String l, String v) {
		pattern = p;
		verb = v;
		location = l;
	}
}
