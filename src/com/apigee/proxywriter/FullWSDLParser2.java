package com.apigee.proxywriter;
 
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.predic8.schema.*;
import com.predic8.wsdl.*;
import com.predic8.wstool.creator.RequestTemplateCreator;
import com.predic8.wstool.creator.SOARequestCreator;

import groovy.xml.MarkupBuilder;
 
public class FullWSDLParser2 {
 
    public static void main(String[] args) throws Exception{
        WSDLParser parser = new WSDLParser();
        StringWriter writer = new StringWriter();
        SOARequestCreator creator = null;
        Definitions defs = //parser.parse("/Users/srinandansridhar/Documents/Accounts/Prudential/annuities-wsdl/ContractInformation_V1.3.wsdl");
        		//parser.parse("/Users/srinandansridhar/Downloads/FoxEDFEmailEBO/FoxEDFEventProducer_BPEL_Client_ep.wsdl");
        		parser.parse("https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl");
        		//parser.parse("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
 
        out("-------------- WSDL Details --------------");
        out("TargenNamespace: \t" + defs.getTargetNamespace());
 
        /* For detailed schema information see the FullSchemaParser.java sample.*/
        out("Schemas: ");
        for (Schema schema : defs.getSchemas()) {
            out("  TargetNamespace: \t" + schema.getTargetNamespace());
        }
        out("\n");
          
        out("PortTypes: ");
        
        creator = new SOARequestCreator(defs, new RequestTemplateCreator(), new MarkupBuilder(writer));
        
        for (PortType pt : defs.getPortTypes()) {
            out("  PortType Name: " + pt.getName());
            out("  PortType Operations: ");
            for (Operation op : pt.getOperations()) {
                out("    Operation Name: " + op.getName());
				creator.setCreator(new RequestTemplateCreator());
				// use membrane SOAP to generate a SOAP Request
				creator.createRequest(pt.getName(), op.getName(), defs.getServices().get(0).getPorts().get(0).getBinding().getName());
				//parseXML(writer.toString());
                writer.getBuffer().setLength(0);
            }
            out("");
        }
        out("");
    }
 
    private static void out(String str) {
        System.out.println(str);
    }
    
   
    public static void parseXML(String xml) throws Exception{
	   DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
		        .newInstance();
	    docBuilderFactory.setNamespaceAware(true);
	    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
	    Document document = docBuilder.parse(new InputSource(new StringReader(xml)));    	
	    traverse(document.getDocumentElement());
    }
    
    public static void traverse(Node node) {
    	out(node.getPrefix() + " - " +  node.getNodeName() + " - " + node.getNamespaceURI());
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                //calls this method for all the children which is Element
                traverse(currentNode);
            } 
        }
    }    
}