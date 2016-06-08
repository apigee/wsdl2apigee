package com.apigee.xsltgen;

import java.io.InputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Marshaller;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLInputFactory;
import java.io.IOException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class RuleSet {
    private final static QName RuleSet_QNAME = new QName("", "RuleSet");
    private int unspecifiedPrefixCount = 0;

    @XmlElement(name="rule")
    //public Rule[] rules;
    
    private ArrayList<Rule> rules;
    
    public RuleSet() {
		rules = new ArrayList<Rule>();
	}

    public boolean validate() {
        ArrayList<String> knownPrefixes = new ArrayList<String>();
        for (Rule r : rules) {
            if (!r.validate()) return false;
            if (r.nsprefix==null || r.nsprefix.equals("")) {
                r.nsprefix = "ns" + unspecifiedPrefixCount++;
            }
            // ensure uniqueness of prefix
            for(String seen : knownPrefixes) {
                if(seen.equals(r.nsprefix))
                    return false;
            }
            knownPrefixes.add(r.nsprefix);
        }
        return true;
    }
    
    public void addRule(Rule r) {
    	rules.add(r);
    }
    
    public static RuleSet readFromFile(String rulesfile) throws Exception {
        RuleSet ruleset;
        if (rulesfile==null || rulesfile.isEmpty()) {
            throw new IOException("ENOFILE: rules file");
        }

        //java.nio.file.Path currentRelativePath = java.nio.file.Paths.get("");
        //String s = currentRelativePath.toAbsolutePath().toString();
        Path path = Paths.get(rulesfile);
        if (!Files.exists(path)) {
            throw new IOException("rules file not found: " + rulesfile);
        }

        InputStream in = Files.newInputStream(path);
        JAXBContext jaxbContext = JAXBContext.newInstance(RuleSet.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        XMLStreamReader xmlReader = factory.createXMLStreamReader(in);
        JAXBElement<RuleSet> je = (JAXBElement<RuleSet>) jaxbUnmarshaller.unmarshal(xmlReader,RuleSet.class);
        ruleset = je.getValue();
        return ruleset;
    }

    public void show() throws JAXBException {
        RuleSet ruleset = this;
        // for diagnostic purposes
        JAXBContext jaxbC = JAXBContext.newInstance(RuleSet.class);
        Marshaller jaxbM = jaxbC.createMarshaller();

        // output pretty printed
        jaxbM.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        // omit the xml decl
        jaxbM.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        JAXBElement<RuleSet> je = new JAXBElement<RuleSet>(RuleSet_QNAME, RuleSet.class, null, ruleset);
        jaxbM.marshal(je, System.out);
    }

    public String getTransform() throws Exception {
        boolean firstTemplate = true;
        
        String template = new Scanner(RuleSet.class.getClassLoader()
                                      .getResourceAsStream("transform.xsl.tmpl"), "UTF-8")
            .useDelimiter("\\A").next();

        String oneRule = new Scanner(RuleSet.class.getClassLoader()
                                      .getResourceAsStream("one-template.xml"), "UTF-8")
            .useDelimiter("\\A").next();

        HashMap<String,String> namespaces = new HashMap<String,String>();
        String xslTemplatesForRules = "";
        for (Rule r : rules) {
            namespaces.put(r.nsprefix, r.namespace);
        }

        String decls = "\n  " + declsForNs(namespaces);
        for (Rule r : rules) {
            String output = oneRule
                .replace("@@MATCH", matchForRule(r))
                .replace("@@PREFIX", r.nsprefix)
                .replace("@@NAMESPACE-DECL", decls);
            decls = "";
            xslTemplatesForRules += output + "\n\n";
        }
                
        String transform = template.replace("@@RULES-FOR-TEMPLATES", xslTemplatesForRules);        
        return transform;
    }

    private String declsForNs(HashMap<String,String> ns) {
        String result = "";
        for (String prefix : ns.keySet()) {
            String xmlns = ns.get(prefix);
            result += "    <xsl:namespace name='"+ prefix +"' select=\"'"+ xmlns +"'\"/>\n";
        }
        return result;
    }
    
    private String matchForRule(Rule r)  {
        switch(r.mode) {
            case "self" :
                return r.xpath;
            case "descendant" :
                return r.xpath+"//*";
            case "child" :
                return r.xpath+"/*";
            case "self-or-descendant":
            case "descendant-or-self":
                return r.xpath + " | " + r.xpath+"//*";
        }
        return null;
    }
    
}
