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
import groovy.xml.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.apigee.utils.XMLUtils;
import com.predic8.wsdl.*;
import com.predic8.wstool.creator.SOARequestCreator;

public class XSLGenerator {

	public static Map<String, String> namespace = new LinkedHashMap<String, String>();
	private static String elementName = ":{local-name()}";
	private static int count = 1;
	
	private static final Set<String> blacklist = new HashSet<String>(Arrays.asList(
		     new String[] {"http://schemas.xmlsoap.org/wsdl/soap/",
		    		 "http://schemas.xmlsoap.org/wsdl/", 
		    		 "http://schemas.xmlsoap.org/ws/2003/05/partner-link/",
		    		 "http://www.w3.org/2001/XMLSchema",
		    		 "http://schemas.xmlsoap.org/soap/encoding/"}
		));	

	private static String rootElement, name="";
	
	public static void main(String[] args) throws Exception {
		WSDLParser parser = new WSDLParser();
		SOARequestCreator creator = null;
		
		Definitions defs = //parser.parse("/Users/srinandansridhar/Documents/Accounts/Prudential/annuities-wsdl/ContractInformation_V1.3.wsdl");
//				parser.parse("/Users/srinandansridhar/Downloads/FoxEDFEmailEBO/FoxEDFEventProducer_BPEL_Client_ep.wsdl");
//        parser.parse("/Users/ApigeeCorporation/Downloads/fox/FoxEDFEventProducer_BPEL_Client_ep.wsdl");
				parser.parse("https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl");
		//parser.parse("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
				
				//TODO: has messages with no parts. messages with no input.
				//parser.parse("/Users/srinandansridhar/Downloads/KKWebServiceEng.wsdl");

        namespace = (Map<String, String>) defs.getPortTypes().get(0).getNamespaceContext();
        
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
        		rootElement = e.getName();
        		Schema s = e.getSchema();
        		for (ComplexType ct : s.getComplexTypes()) {
            		SchemaComponent sc = ct.getModel();
            		parse(sc, defs.getSchemas());
        		}
        	}
        }
	}
	
	
	private static void parse(SchemaComponent sc, List<Schema> schemas) {
		if (sc instanceof Sequence) {
			Sequence seq = (Sequence) sc;
			for (com.predic8.schema.Element e : seq.getElements()) {
                parseElement(e, schemas);
            }
		}
		else if (sc instanceof Choice) {
			Choice ch = (Choice)sc;
			for (com.predic8.schema.Element e : ch.getElements()) {
				if (!e.getName().equalsIgnoreCase(rootElement)) {
					if (e.getEmbeddedType() instanceof ComplexType) {
						ComplexType ct = (ComplexType)e.getEmbeddedType();
                        out("\t\t" + ct.getName());
						parse(ct.getModel(), schemas);
					} else {
                        final TypeDefinition typeDefinition = getTypeFromSchema(e.getType(), schemas);
                        if (typeDefinition instanceof ComplexType) {
                            out("\t\t" + e.getName());
//                            out("\t\t" + typeDefinition.getName());
                            parse(((
                                    ComplexType) typeDefinition).getModel(), schemas);
                        }
                        if (!e.getType().getNamespaceURI().equalsIgnoreCase("http://www.w3.org/2001/XMLSchema")){
							out("\t\t"+e.getName() + " - " + e.getNamespaceUri() + " - " + e.getType().getNamespaceURI());
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
        else if (sc instanceof com.predic8.schema.Element) {
            parseElement((com.predic8.schema.Element) sc, schemas);
        }
		else {
			out(sc.getClass().getName());
			out("here....");
		}
	}

    private static void parseElement(com.predic8.schema.Element e, List<Schema> schemas) {
        if (e.getName() == null) {
            if (e.getRef() != null) {
                final String localPart = e.getRef().getLocalPart();
                final com.predic8.schema.Element element = elementFromSchema(localPart, schemas);
                out(element.getName());
                parse(element, schemas);
            }
            else {
                out("Trouble");
            }
        }
        else {
            out("->"+e.getName());
            if (!e.getName().equalsIgnoreCase(rootElement)) {
                if (e.getEmbeddedType() instanceof ComplexType) {
                    out("\t\t" + e.getName());
                    ComplexType ct = (ComplexType)e.getEmbeddedType();
                    parse(ct.getModel(), schemas);
                } else {
                    if (e.getType() == null) {
                        out("No type: " + e.toString());
                    }
                    else if (!e.getType().getNamespaceURI().equalsIgnoreCase("http://www.w3.org/2001/XMLSchema")){
                        out("\t\t"+e.getName() + " - " + e.getNamespaceUri() + " - " + e.getType().getNamespaceURI());
                    }
                }
            }
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

    private static com.predic8.schema.Element elementFromSchema(String name, List<Schema> schemas) {
        if (name != null) {
            for (Schema schema: schemas) {
                try {
                    final com.predic8.schema.Element element = schema.getElement(name);
                    if (element != null) {
                        return element;
                    }
                } catch (Exception e) {
                    // Fail silently
                }
            }
        }
        return null;
    }
	
	
	private static void out(String str) {
		System.out.println(str);
	}

	public static void parseXSL(String xslt, String prefix, String namespaceUri) throws Exception {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setNamespaceAware(true);
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document document = docBuilder.parse(new File(xslt));

		addNamespaces(document, prefix, namespaceUri);
	}

	public static void addNamespaces(Document document, String prefix, String namespaceUri) throws Exception {
		Node stylesheet = document.getDocumentElement();
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (!blacklist.contains(value)) {
				((Element) stylesheet).setAttribute("xmlns:"+key, value);
			}
		}
		
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		// there's no default implementation for NamespaceContext...seems kind of silly, no?
		xp.setNamespaceContext(new NamespaceContext() {
			
			@Override
			public Iterator getPrefixes(String namespaceURI) {
		        throw new UnsupportedOperationException();
			}
			
			@Override
			public String getPrefix(String namespaceURI) {
		        throw new UnsupportedOperationException();
			}
			
			@Override
			public String getNamespaceURI(String prefix) {
		        if (prefix == null) throw new NullPointerException("Null prefix");
		        else if ("xsl".equals(prefix)) return "http://www.w3.org/1999/XSL/Transform";
		        else if ("xml".equals(prefix)) return XMLConstants.XML_NS_URI;
		        return XMLConstants.NULL_NS_URI;
			}
		});		

		NodeList nodes = (NodeList) xp.evaluate("/xsl:stylesheet/xsl:template/xsl:element", document, XPathConstants.NODESET);
		Node element = nodes.item(0);

		NamedNodeMap attr = element.getAttributes();
		Node nodeAttr = attr.getNamedItem("name");
		nodeAttr.setNodeValue(prefix+elementName);
		
		Node nspace = document.createElementNS("http://www.w3.org/1999/XSL/Transform", "xsl:namespace");
		((Element) nspace).setAttribute("name", prefix);
		((Element) nspace).setAttribute("select", "'"+namespaceUri+"'");
		
		element.insertBefore(nspace, element.getFirstChild());
		
		XMLUtils xml = new XMLUtils();
		xml.writeXML(document, "./templates/soap2api/xsl/testing" + count + ".xsl");
		count++;
	}
	
	public static String getPrefix( String namespaceUri) {
		for (Map.Entry<String, String> entry : namespace.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(namespaceUri)) {
				return entry.getKey();
			}
		}
		return null;
	}
}