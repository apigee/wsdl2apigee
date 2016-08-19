package com.apigee.oas;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONObject;

public class OASUtils {

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
}
