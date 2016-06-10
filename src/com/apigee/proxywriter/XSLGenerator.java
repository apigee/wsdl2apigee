package com.apigee.proxywriter;

import java.io.File;
import java.io.StringWriter;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import com.predic8.schema.*;
import com.predic8.soamodel.XMLElement;

import groovy.xml.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.apigee.utils.XMLUtils;
import com.apigee.xsltgen.Rule;
import com.apigee.xsltgen.RuleSet;
import com.predic8.wsdl.*;
import com.predic8.wstool.creator.SOARequestCreator;

public class XSLGenerator {

	public static Map<String, String> namespaceCtx = new LinkedHashMap<String, String>();
	private static int count = 1;
	
	private static final Set<String> blacklist = new HashSet<String>(Arrays.asList(
		     new String[] {"http://schemas.xmlsoap.org/wsdl/soap/",
		    		 "http://schemas.xmlsoap.org/wsdl/", 
		    		 "http://schemas.xmlsoap.org/ws/2003/05/partner-link/",
		    		 "http://www.w3.org/2001/XMLSchema",
		    		 "http://schemas.xmlsoap.org/soap/encoding/"}
		));	

	private static String rootElement, name="", rootNamespace="";
	
	private static ArrayList<String> xpaths = new ArrayList<String>();
	private static ArrayList<Rule> ruleList = new ArrayList<Rule>();
	
	public static void main(String[] args) throws Exception {
		WSDLParser parser = new WSDLParser();
		
		Definitions defs = //parser.parse("/Users/srinandansridhar/Documents/Accounts/Prudential/annuities-wsdl/ContractInformation_V1.3.wsdl")
		parser.parse("/Users/srinandansridhar/Downloads/FoxEDFEmailEBO/FoxEDFEventProducer_BPEL_Client_ep.wsdl");
//				parser.parse("https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl");
		//parser.parse("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
				
				//TODO: has messages with no parts. messages with no input.
				//parser.parse("/Users/srinandansridhar/Downloads/KKWebServiceEng.wsdl");
        StringWriter writer = new StringWriter();
        
		out("Style: " + defs.getBindings().get(0).getStyle());
		
		Binding binding = defs.getBindings().get(0);
		out(binding.getName());
		PortType pt = binding.getPortType();
		out(pt.getName());
        for (Operation op : pt.getOperations()) {
        	if (op.getInput().getMessage().getParts().size() == 1) {
        		out(op.getName());
        		com.predic8.schema.Element e = op.getInput().getMessage().getParts().get(0).getElement();
        		namespaceCtx = (Map<String, String>) e.getNamespaceContext();
        		rootElement = e.getName();
        		rootNamespace = e.getNamespaceUri();
        		out("Root Element: " + rootElement + " Root Namespace: " + rootNamespace);
        		Schema s = e.getSchema();
        		for (ComplexType ct : s.getComplexTypes()) {
            		SchemaComponent sc = ct.getModel();
            		parse(sc, defs.getSchemas());
        		}
            	RuleSet rs = new RuleSet();
            	rs.addRuleList(ruleList);
            	out(rs.getTransform());
        	}
        }
	}
	
	private static void parse(SchemaComponent sc, List<Schema> schemas) {
		if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			for (com.predic8.schema.Element e : seq.getElements()) {
                if (e.getName() == null) {
                    if (e.getRef() != null) {
                        out(e.getRef().getLocalPart());
                        final TypeDefinition typeDefinition = getTypeFromSchema(e.getRef(), schemas);
                        //out(typeDefinition.toString());
                    }
                    else {
                        //TODO: handle this
                    }
                }
				else if (!e.getName().equalsIgnoreCase(rootElement)) {					
					if (e.getEmbeddedType() instanceof ComplexType) {
						ComplexType ct = (ComplexType)e.getEmbeddedType();
						parse(ct.getModel(), schemas);
					} else {
                        final TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
                        if (typeDefinition instanceof ComplexType) {
                        	parse(((ComplexType) typeDefinition).getModel(), schemas);
                        }						
                        if (e.getType() == null) {
                            //TODO: handle this
                        }
						else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace) &&
								!e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e.getParent(), e.getName(), false);						} 
                    }
				}
			}
		}
		else if (sc instanceof Choice) {
			Choice ch = (Choice)sc;
			for (com.predic8.schema.Element e : ch.getElements()) {
				if (!e.getName().equalsIgnoreCase(rootElement)) {
					if (e.getEmbeddedType() instanceof ComplexType) {
						ComplexType ct = (ComplexType)e.getEmbeddedType();
						parse(ct.getModel(), schemas);
					} else {
                        final TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
                        if (typeDefinition instanceof ComplexType) {
                            parse(((ComplexType) typeDefinition).getModel(), schemas);
                        }
                        if (e.getType() == null) {
                            //TODO: handle this
                        }
						else if (!getParentNamepace(e).equalsIgnoreCase(rootNamespace) &&
								!e.getType().getNamespaceURI().equalsIgnoreCase(rootNamespace)) {
							buildXPath(e.getParent(), e.getName(), false);
						} 
					}
				}
			}
		}
		else if (sc instanceof ComplexContent) {
			ComplexContent complexContent = (ComplexContent)sc;
            Derivation derivation = complexContent.getDerivation();
            if (derivation != null) {
                if (derivation.getModel() instanceof Sequence) {
                    parse(derivation.getModel(), schemas);
                }
                else if (derivation.getModel() instanceof ModelGroup) {
                    parse(derivation.getModel(), schemas);
                }
            }
		}
		else if (sc instanceof SimpleContent) {
			SimpleContent simpleContent = (SimpleContent)sc;
			Derivation derivation = (Derivation)simpleContent.getDerivation();
			
			if (derivation.getAllAttributes().size() > 0) {
				buildXPath(simpleContent.getParent(), null, true); //has attributes
			} else {
				buildXPath(simpleContent.getParent(), null, false); //has no attributes
			}
		} 
		else {
			//TODO: handle this
			out("here");
		}
		
	}

    private static TypeDefinition getTypeFromSchema(QName qName, List<Schema> schemas) {
        if (qName != null) {
            for (Schema schema: schemas) {
                try {
                    final TypeDefinition type = schema.getType(qName);
                    if (type != null) {
                        return type;
                    }
                } catch (Exception e) {
                    // Fail silently
                }
            }
        }
        return null;
    }
	
	private static void buildXPath(XMLElement xml, String elementName, boolean hasAttributes) {
		String start = "/" + rootElement + "/*/";
		Rule r = null;
		
		getXPath(xml);
		
		if (elementName != null) xpaths.add(elementName);
		
		for (String s : xpaths) {
			r = new Rule(start+s, getPrefix(xml.getNamespaceUri()), xml.getNamespaceUri());
			ruleList.add(r);
			start = start + s + "/";
		}
		if (hasAttributes && xpaths.size() > 0) {
			start = start +"@*";
			r = new Rule(start, getPrefix(xml.getNamespaceUri()), xml.getNamespaceUri());
			ruleList.add(r);
		}
		xpaths.clear();
	}
    
    private static void getXPath (XMLElement xml) {
		if (xml == null) {
			return;
		}
		else if (xml instanceof com.predic8.schema.Element) {
			xpaths.add(((com.predic8.schema.Element) xml).getName());
			getXPath(xml.getParent());
		}
		else {
			getXPath(xml.getParent());
		}
	}
	
	private static String getParentNamepace (com.predic8.schema.Element e) {
		return e.getParent().getNamespaceUri();
	}
    
    private static void out(String str) {
		System.out.println(str);
	}
	
	public static String getPrefix( String namespaceUri) {
		for (Map.Entry<String, String> entry : namespaceCtx.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(namespaceUri)) {
				return entry.getKey();
			}
		}
		return null;
	}
}