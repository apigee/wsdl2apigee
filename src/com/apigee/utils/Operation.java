package com.apigee.utils;

public class Operation {

	private String verb;
	private String name;
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
	public String getName() {
		return name;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}
	
	Operation(String n, String l, String v) {
		name = n;
		verb = v;
		location = l;
	}
}
