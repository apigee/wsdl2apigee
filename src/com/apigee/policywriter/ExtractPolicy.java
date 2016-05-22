package com.apigee.policywriter;

public class ExtractPolicy {
	
	
	String operationName;
	String policyName;
	String httpVerb;
	
	/**
	 * @return the operationName
	 */
	public String getOperationName() {
		return operationName;
	}

	/**
	 * @param operationName the operationName to set
	 */
	public void setOperationName(String operationName) {
		this.operationName = operationName;
	}

	/**
	 * @return the policyName
	 */
	public String getPolicyName() {
		return policyName;
	}

	/**
	 * @param policyName the policyName to set
	 */
	public void setPolicyName(String policyName) {
		this.policyName = policyName;
	}

	/**
	 * @return the httpVerb
	 */
	public String getHttpVerb() {
		return httpVerb;
	}

	/**
	 * @param httpVerb the httpVerb to set
	 */
	public void setHttpVerb(String httpVerb) {
		this.httpVerb = httpVerb;
	}
	
	public ExtractPolicy (String opName, String poName, String htVerb) {
		operationName = opName;
		policyName = poName;
		httpVerb = htVerb;
	}
}
