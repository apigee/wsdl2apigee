package com.apigee.proxywriter;

import java.io.StringReader;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Temp {
	
	private static DocumentBuilderFactory factory;
	private static DocumentBuilder builder;
	private static XPathFactory xpf = XPathFactory.newInstance();
	private static XPath xp = xpf.newXPath();
	
	static {
		factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
	}

	public static void main(String[] args) throws Exception{

		String xml = "<v1:FoxEDFEvent xmlns:v1=\"http://fox.event.ebo/V1.0\" xmlns:v11=\"http://fox.email.ebo/V1.0\" xmlns:add=\"http://schemas.xmlsoap.org/ws/2003/03/addressing\">\r\n    <v1:EventName>?</v1:EventName>\r\n    <v1:EventType>?</v1:EventType>\r\n    <v1:Data>\r\n        <v1:Payload>?</v1:Payload>\r\n        <v1:Email>\r\n            <v11:TOList>\r\n                <v11:TO>?</v11:TO>\r\n            </v11:TOList>\r\n            <v11:CCList>\r\n                <v11:CC>?</v11:CC>\r\n            </v11:CCList>\r\n            <v11:DeliveryType>schedule</v11:DeliveryType>\r\n            <v11:Subject>?</v11:Subject>\r\n            <v11:Message>?</v11:Message>\r\n            <v11:AttachmentList>\r\n                <v11:Attachment>cid:775688722892</v11:Attachment>\r\n            </v11:AttachmentList>\r\n        </v1:Email>\r\n    </v1:Data>\r\n    <v1:CorrelationID>?</v1:CorrelationID>\r\n    <v1:EventPriority>0</v1:EventPriority>\r\n    <v1:ProducerID>?</v1:ProducerID>\r\n    <v1:ReplyTo>\r\n        <add:Address>?</add:Address>\r\n        <add:ReferenceProperties/>\r\n        <add:PortType>?</add:PortType>\r\n        <add:ServiceName PortName=\"?\">?</add:ServiceName>\r\n    </v1:ReplyTo>\r\n    <v1:EventProperties>\r\n        <v1:PropertyName>?</v1:PropertyName>\r\n        <v1:PropertyValue>?</v1:PropertyValue>\r\n    </v1:EventProperties>\r\n</v1:FoxEDFEvent>";
		builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new InputSource(new StringReader(xml)));

		NodeList nodes = (NodeList) xp.evaluate("//@* | //*[not(*)]", doc, XPathConstants.NODESET);
		
		for (int i = 0, len = nodes.getLength(); i < len; i++) {
			System.out.println(nodes.item(i).getPrefix() + " - " + nodes.item(i).getLocalName() + " - " + nodes.item(i).getNamespaceURI() + " - " + getFullXPath(nodes.item(i)));
		}
		
	}
	
	
	public static String getFullXPath(Node n) {
		// abort early
		if (null == n)
		  return null;

		// declarations
		Node parent = null;
		Stack<Node> hierarchy = new Stack<Node>();
		StringBuffer buffer = new StringBuffer();

		// push element on stack
		hierarchy.push(n);

		switch (n.getNodeType()) {
		case Node.ATTRIBUTE_NODE:
		  parent = ((Attr) n).getOwnerElement();
		  break;
		case Node.ELEMENT_NODE:
		  parent = n.getParentNode();
		  break;
		case Node.DOCUMENT_NODE:
		  parent = n.getParentNode();
		  break;
		default:
		  throw new IllegalStateException("Unexpected Node type" + n.getNodeType());
		}

		while (null != parent && parent.getNodeType() != Node.DOCUMENT_NODE) {
		  // push on stack
		  hierarchy.push(parent);

		  // get parent of parent
		  parent = parent.getParentNode();
		}

		// construct xpath
		Object obj = null;
		while (!hierarchy.isEmpty() && null != (obj = hierarchy.pop())) {
		  Node node = (Node) obj;
		  boolean handled = false;

		  if (node.getNodeType() == Node.ELEMENT_NODE) {
		    Element e = (Element) node;

		    // is this the root element?
		    if (buffer.length() == 0) {
		      // root element - simply append element name
		      buffer.append(node.getNodeName());
		    } else {
		      // child element - append slash and element name
		      buffer.append("/");
		      buffer.append(node.getNodeName());

		      if (node.hasAttributes()) {
		        // see if the element has a name or id attribute
		        if (e.hasAttribute("id")) {
		          // id attribute found - use that
		          buffer.append("[@id='" + e.getAttribute("id") + "']");
		          handled = true;
		        } else if (e.hasAttribute("name")) {
		          // name attribute found - use that
		          buffer.append("[@name='" + e.getAttribute("name") + "']");
		          handled = true;
		        }
		      }

		      if (!handled) {
		        // no known attribute we could use - get sibling index
		        int prev_siblings = 1;
		        Node prev_sibling = node.getPreviousSibling();
		        while (null != prev_sibling) {
		          if (prev_sibling.getNodeType() == node.getNodeType()) {
		            if (prev_sibling.getNodeName().equalsIgnoreCase(
		                node.getNodeName())) {
		              prev_siblings++;
		            }
		          }
		          prev_sibling = prev_sibling.getPreviousSibling();
		        }
		        buffer.append("[" + prev_siblings + "]");
		      }
		    }
		  } else if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
		    buffer.append("/@");
		    buffer.append(node.getNodeName());
		  }
		}
		// return buffer
		return buffer.toString();
		}          

}
