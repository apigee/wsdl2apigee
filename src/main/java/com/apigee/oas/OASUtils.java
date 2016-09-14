package com.apigee.oas;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class OASUtils {
	
	public static JSONObject getResponse(String element) {
		JSONObject success = new JSONObject();
		JSONObject details = new JSONObject();
		JSONObject schema = new JSONObject();
		schema.put("$ref", "#/definitions/"+element);
		details.put("description", "Successful response");
		details.put("schema", schema);
		success.put("200", details);
		return success;
	}
	

	public static JSONObject getSuccess() {
		JSONObject success = new JSONObject();
		JSONObject details = new JSONObject();
		JSONObject schema = new JSONObject();
		schema.put("$ref", "#/definitions/undefined");
		details.put("description", "Successful response");
		details.put("schema", schema);
		success.put("200", details);
		return success;
	}

	public static String getHostname(String targetEndpoint) {
		try {
			URI uri = new URI(targetEndpoint);
			return uri.getHost();
		} catch (Exception e) {
			return "localhost";
		}
	}
	
	public static InputStream writeJSON(String oasData, String fileName) throws Exception {
		final PrintWriter writer = new PrintWriter(fileName+".json");
		writer.println(oasData);
		writer.close();
		return new ByteArrayInputStream(Files.readAllBytes(Paths.get(fileName+".json")));
	}
	
    public static JSONArray getQueryParameters(ArrayList<String> queryParams) {
    	JSONArray parameters = new JSONArray();
    	JSONObject parameter = null;
    	for (String queryParam : queryParams) {
    		parameter = new JSONObject();
    		parameter.put("name", queryParam);
    		parameter.put("in", "query");
    		parameter.put("required", false);
    		parameter.put("type", "string");
    		parameters.put(parameter);
    	}
    	
    	return parameters;
    }
    
    public static JSONArray getBodyParameter(String name) {
    	JSONArray parameters = new JSONArray();
    	JSONObject parameter = new JSONObject();
    	JSONObject schemaReference = new JSONObject();
    	
    	schemaReference.put("$ref", "#/definitions/"+name);
    	
    	parameter.put("name", name);
    	parameter.put("in", "body");
    	parameter.put("required", "true");
    	parameter.put("schema", schemaReference);
    	parameters.put(0, parameter);
    	
    	return parameters;
    }

	public static void addObject(JSONObject parent, String parentName, String objectName) {
		JSONObject properties = parent.getJSONObject("properties");
		JSONObject object = new JSONObject();
		object.put("$ref", "#/definitions/"+objectName);
		properties.put(objectName, object);
	}
	
	public static JSONObject createComplexType(String name, String min, String max) {
		JSONObject complexType = new JSONObject();
		JSONObject properties = new JSONObject();
		JSONObject items = new JSONObject();
		
		items.put("$ref", "#/definitions/"+name);
		
		Integer maximum = 0;
		
		if (max.equalsIgnoreCase("unbounded")) {
			maximum = -1;
		} else {
			maximum = Integer.parseInt(max);
		}
		
		Integer minimum = Integer.parseInt(min);

		complexType.put("properties", properties);

		if (maximum == -1 || maximum > 1) {
			complexType.put("type", "array");
			//in json schemas, if the elements are unbounded, don't set maxItems
			if (maximum != -1) complexType.put("maxItems", maximum);
			complexType.put("minItems", minimum);
			complexType.put("items", items);
		} else {
			complexType.put("type", "object");
		}
		return complexType;
	}
	
	public static JSONObject createSimpleType(String type, String min, String max) {
		JSONObject simpleType = new JSONObject();
		String oasDataType = "";
		String oasFormat = "";
		JSONObject items = new JSONObject();
		Integer maximum = 0;
		
		if (max.equalsIgnoreCase("unbounded")) {
			maximum = -1;
		} else {
			maximum = Integer.parseInt(max);
		}
		
		Integer minimum = Integer.parseInt(min);
		
		if (type.equals("string")) {
			oasDataType = "string";
		} else if (type.equalsIgnoreCase("int")) {
			oasDataType = "integer";
			oasFormat = "int32";			
		} else if (type.equalsIgnoreCase("long")) {
			oasDataType = "integer";
			oasFormat = "int64";			
		} else if (type.equalsIgnoreCase("boolean")) {
			oasDataType = "boolean";		
		} else if (type.equalsIgnoreCase("float")) {
			oasDataType = "number";
			oasFormat = "float";			
		} else if (type.equalsIgnoreCase("double")) {
			oasDataType = "number";
			oasFormat = "double";			
		} else if (type.equalsIgnoreCase("date")) {
			oasDataType = "string";
			oasFormat = "date";			
		} else if (type.equalsIgnoreCase("dateTime")) {
			oasDataType = "string";
			oasFormat = "date-time";			
		} else if (type.equalsIgnoreCase("base64binary")) {
			oasDataType = "string";
			oasFormat = "byte";			
		}
		
		if (maximum ==-1 || maximum > 1) {
			simpleType.put("type", "array");
			items.put("type", oasDataType);
			if (oasFormat != "") {
				items.put("format", oasFormat);
			}
			if (maximum != -1) simpleType.put("maxItems", maximum);
			simpleType.put("minItems", minimum);
			simpleType.put("items", items);
		} else {
			simpleType.put("type", oasDataType);
			if (oasFormat != "") {
				simpleType.put("format", oasFormat);
			}
		}
		
		return simpleType;
	}
    
	
}
