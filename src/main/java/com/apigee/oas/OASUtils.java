package com.apigee.oas;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class OASUtils {
	
	
	private static final Logger LOGGER = Logger.getLogger(OASUtils.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
		ConsoleHandler handler = new ConsoleHandler();
		// PUBLISH this level
		handler.setLevel(Level.INFO);
		LOGGER.addHandler(handler);
	}
	
	public static JsonObject getResponse(String element) {
        JsonObject success = new JsonObject();
		JsonObject details = new JsonObject();
		JsonObject schema = new JsonObject();
		schema.addProperty("$ref", "#/definitions/"+element);
		details.addProperty("description", "Successful response");
		details.add("schema", schema);
		success.add("200", details);
		return success;
	}
	

	public static JsonObject getSuccess() {
		JsonObject success = new JsonObject();
		JsonObject details = new JsonObject();
		JsonObject schema = new JsonObject();
		schema.addProperty("$ref", "#/definitions/undefined");
		details.addProperty("description", "Successful response");
		details.add("schema", schema);
		success.add("200", details);
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
	
	public static InputStream writeJson(String oasData, String fileName) throws Exception {
		final PrintWriter writer = new PrintWriter(fileName+".json");
		writer.println(oasData);
		writer.close();
		return new ByteArrayInputStream(Files.readAllBytes(Paths.get(fileName+".json")));
	}
	
    public static JsonArray getQueryParameters(ArrayList<String> queryParams) {
    	JsonArray parameters = new JsonArray();
    	JsonObject parameter = null;
    	for (String queryParam : queryParams) {
    		parameter = new JsonObject();
    		parameter.addProperty("name", queryParam);
    		parameter.addProperty("in", "query");
    		parameter.addProperty("required", false);
    		parameter.addProperty("type", "string");
    		parameters.add(parameter);
    	}
    	
    	return parameters;
    }
    
    public static JsonArray getBodyParameter(String name) {
    	JsonArray parameters = new JsonArray();
    	JsonObject parameter = new JsonObject();
    	JsonObject schemaReference = new JsonObject();
    	
    	schemaReference.addProperty("$ref", "#/definitions/"+name);
    	
    	parameter.addProperty("name", name);
    	parameter.addProperty("in", "body");
    	parameter.addProperty("required", true);
    	parameter.add("schema", schemaReference);
    	parameters.add(parameter);
    	
    	return parameters;
    }

	public static void addObject(JsonObject parent, String parentName, String objectName) {
		JsonObject properties = parent.getAsJsonObject("properties");
		JsonObject object = new JsonObject();
		object.addProperty("$ref", "#/definitions/"+objectName);
		properties.add(objectName, object);
	}
	
	public static void addObject(JsonObject parent, String parentName, String objectName,boolean isChildComplexType, String qNameLocal) {
		JsonObject properties = parent.getAsJsonObject("properties");
		JsonObject object = new JsonObject();
		if(isChildComplexType)
		{
			object.addProperty("$ref", "#/definitions/"+qNameLocal);
		}
		properties.add(objectName, object);
		
	}
	
	/*
	 * inorder to retrieve inner properties attribute to assign output elements
	 * 
	 */
	public static void addObjectOutputRes(JsonObject parent, String parentName, String objectName,boolean isChildComplexType, String qNameLocal) {
		JsonObject properties = parent.getAsJsonObject("properties").getAsJsonObject(parentName).getAsJsonObject("properties");
		JsonObject object = new JsonObject();
		if(isChildComplexType)
		{
			object.addProperty("$ref", "#/definitions/"+qNameLocal);
		}
		properties.add(objectName, object);
		
		
	}
	
	/*
	 * inorder to retrieve inner properties attribute to assign output elements
	 * 
	 */
	public static void addObjectOutputArrayProp(JsonObject parent, String parentName, String objectName,
			boolean isChildComplexType, String qNameLocal, String subArrayElement) {
		
		if(null != parent.getAsJsonObject("properties").getAsJsonObject(parentName)) {
			
			JsonObject properties = parent.getAsJsonObject("properties").getAsJsonObject(parentName).getAsJsonObject("properties");
			JsonObject innerProp = new JsonObject();
			JsonObject subArrayJson = new JsonObject();
			innerProp.add(subArrayElement, subArrayJson);
			
			JsonObject jsoonOuterProp = new JsonObject();
			jsoonOuterProp.add("properties", innerProp);
			jsoonOuterProp.addProperty("type", "object");
			properties.add(objectName, jsoonOuterProp);
		}else {
			JsonObject properties = parent.getAsJsonObject("properties");
			JsonObject innerProp = new JsonObject();
			JsonObject subArrayJson = new JsonObject();
			innerProp.add(subArrayElement, subArrayJson);
			
			JsonObject jsoonOuterProp = new JsonObject();
			jsoonOuterProp.add("properties", innerProp);
			jsoonOuterProp.addProperty("type", "object");
			properties.add(objectName, jsoonOuterProp);
		}
	}

	/*
	 * inorder to retrieve inner properties attribute to assign output elements
	 * 
	 */
	public static void addObjectOutputForArray(JsonObject parent, String parentName, String objectName,
			boolean isChildComplexType, String qNameLocal, String subArrayElement) {
		
		if(null != parent.getAsJsonObject("properties").getAsJsonObject(parentName)) {
			JsonObject rep = parent.getAsJsonObject("properties").getAsJsonObject(parentName).getAsJsonObject("properties").
					getAsJsonObject(objectName).getAsJsonObject("properties").getAsJsonObject(subArrayElement);
			JsonObject items = new JsonObject();

			items.addProperty("$ref", "#/definitions/"+subArrayElement);
			rep.add("items", items);
			rep.addProperty("type", "array");
		}else {
			JsonObject rep = parent.getAsJsonObject("properties").getAsJsonObject(objectName).getAsJsonObject("properties").
					getAsJsonObject(subArrayElement);
			JsonObject items = new JsonObject();

			items.addProperty("$ref", "#/definitions/"+subArrayElement);
			rep.add("items", items);
			rep.addProperty("type", "array");
		}
	}
	
	public static JsonObject createComplexType(String name, String min, String max) {
		JsonObject complexType = new JsonObject();
		JsonObject properties = new JsonObject();
		JsonObject items = new JsonObject();
		
		items.addProperty("$ref", "#/definitions/"+name);
		
		Integer maximum = 0;
		
		if (max.equalsIgnoreCase("unbounded")) {
			maximum = -1;
		} else {
			maximum = Integer.parseInt(max);
		}
		
		Integer minimum = Integer.parseInt(min);

		complexType.add("properties", properties);
		
		if (maximum == -1 || maximum > 1) {
			complexType.addProperty("type", "array");
			//in json schemas, if the elements are unbounded, don't set maxItems
			if (maximum != -1) complexType.addProperty("maxItems", maximum);
			complexType.addProperty("minItems", minimum);
			complexType.add("items", items);
		} else {
			complexType.addProperty("type", "object");
		}
		return complexType;
	}

	public static JsonObject createComplexTypeRep(String name, String min, String max) {
		JsonObject complexType = new JsonObject();
		JsonObject properties = new JsonObject();
		complexType.add("properties", properties);
		
		complexType.addProperty("type", "object");
		return complexType;
	}

	
	public static JsonObject createExtension(String baseName) {
		JsonObject extension = new JsonObject();
		JsonObject properties = new JsonObject();
		extension.addProperty("type", "object");
		extension.add("properties", properties);
		
		return extension;
	}
	public static JsonObject createRestriction(String type, String min, String max) {
		JsonObject restriction = createSimpleType(type,min,max);
		JsonArray enumArray = new JsonArray();
		if(restriction.get("items") != null)
		{
			JsonObject items = (JsonObject)restriction.get("items");
			items.add("enum", enumArray);
		}
		else
		{
			restriction.add("enum", enumArray);
		}
		
		return restriction;
	}
	
	/*
	 * Method used to create inner properties for each response attribute under #definitions of OAS
	 * 
	 */
	public static JsonObject createComplexTypeOP(String name, JsonObject jsonObj) {
		JsonObject properties = jsonObj.getAsJsonObject("properties");
		JsonObject innerProp = new JsonObject();
		JsonObject jsoonOuterProp = new JsonObject();
		jsoonOuterProp.add("properties", innerProp);
		properties.add(name, jsoonOuterProp);
		
		return properties;
	}
	
	
	public static JsonObject createSimpleType(String type, String min, String max) {
		JsonObject simpleType = new JsonObject();
		String oasDataType = "";
		String oasFormat = "";
		JsonObject items = new JsonObject();
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
		} else { //set the default data type to string
			//ex: there is no decimal data type. interpret as string.
			oasDataType = "string";
		}
		
		if (maximum ==-1 || maximum > 1) {
			simpleType.addProperty("type", "array");
			items.addProperty("type", oasDataType);
			if (oasFormat != "") {
				items.addProperty("format", oasFormat);
			}
			if (maximum != -1) simpleType.addProperty("maxItems", maximum);
			simpleType.addProperty("minItems", minimum);
			simpleType.add("items", items);
		} else {
			simpleType.addProperty("type", oasDataType);
			if (oasFormat != "") {
				simpleType.addProperty("format", oasFormat);
			}
		}
		
		return simpleType;
	}
    
	
}
