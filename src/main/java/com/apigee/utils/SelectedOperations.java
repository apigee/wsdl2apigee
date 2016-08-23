package com.apigee.utils;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class SelectedOperations {

	HashMap<String, SelectedOperation> selectedOperations;
	//String selectedOperationsJson = "[{\"operationName\":\"op1\",\"verb\":\"get\",\"resourcePath\":\"/nandan\"},{\"operationName\":\"op2\",\"verb\":\"post\",\"resourcePath\":\"/aadya\"}]";
	
	public SelectedOperations () {
		selectedOperations = new HashMap<String, SelectedOperation>();
	}
	
	public void parseSelectedOperations (String selectedOperationsJson) throws Exception {
		JSONArray array = new JSONArray(selectedOperationsJson);
		
		for (int i=0; i<array.length(); i++) {
			JSONObject json = array.getJSONObject(i);
			SelectedOperation selectedOperation = new SelectedOperation(json.get("verb").toString(), json.get("resourcePath").toString());
			selectedOperations.put(json.get("operationName").toString(), selectedOperation);
		}
	}
	
	public HashMap<String, SelectedOperation> getSelectedOperations() {
		return selectedOperations;
	}	
}
