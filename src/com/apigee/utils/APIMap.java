package com.apigee.utils;

public class APIMap {

	private String jsonBody;
	private String soapBody;
	private String resourcePath;
	private String verb;
	private String soapAction;
	private boolean otherNamespaces;
	
	public APIMap(String j, String s, String r, String v, boolean ons) {
		jsonBody = j;
		soapBody = s;
		resourcePath = r;
		verb = v;
		otherNamespaces = ons;
	}
	
	public String getJsonBody() {
		return jsonBody;
	}
	
	public String getSoapBody() {
		return soapBody;
	}

	public String getResourcePath() {
		return resourcePath;
	}

	public String getVerb() {
		return verb;
	}
	
	public String getSoapAction() {
		return soapAction;
	}

	public void setSoapAction(String s) {
		soapAction = s;
	}
	
	public boolean getOthernamespaces() {
		return otherNamespaces;
	}
}
