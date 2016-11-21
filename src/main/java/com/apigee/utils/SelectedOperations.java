package com.apigee.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;


public class SelectedOperations {

	HashMap<String, SelectedOperation> selectedOperations;
	//String selectedOperationsJson = "[{\"operationName\":\"op1\",\"verb\":\"get\",\"resourcePath\":\"/nandan\"},{\"operationName\":\"op2\",\"verb\":\"post\",\"resourcePath\":\"/aadya\"}]";
	
	public SelectedOperations () {
		selectedOperations = new HashMap<String, SelectedOperation>();
	}
	
	public void parseSelectedOperations (String selectedOperationsJson) throws Exception {
        JsonArray array = (JsonArray) new JsonParser().parse(selectedOperationsJson);

		for (int i=0; i<array.size(); i++) {
			JsonObject json = array.get(i).getAsJsonObject();
			SelectedOperation selectedOperation = new SelectedOperation(json.get("verb").getAsString(), json.get("resourcePath").getAsString());
			selectedOperations.put(json.get("operationName").getAsString(), selectedOperation);
		}
	}
	
	public HashMap<String, SelectedOperation> getSelectedOperations() {
		return selectedOperations;
	}	
}
