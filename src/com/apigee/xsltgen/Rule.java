package com.apigee.xsltgen;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlAccessType;
import java.util.Arrays;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Rule {

    @XmlTransient
    public final static String[] validModes = {"descendant", "self", "descendant-or-self", "self-or-descendant","child"};

    @XmlAttribute()
    public String namespace;

    @XmlAttribute()
    public String nsprefix;

    @XmlAttribute()
    public String xpath;

    @XmlAttribute()
    public String mode;

    public boolean validate() {
        return (Arrays.asList(validModes).contains(mode));
    }
    
    public Rule(String xp, String nsp, String ns) {
    	mode = "self";
    	xpath = xp;
    	nsprefix = nsp;
    	namespace = ns;
    }
}