package com.apigee.proxywriter;
 
import com.predic8.schema.*;
import com.predic8.wsdl.*;
 
public class FullWSDLParser {
 
    public static void main(String[] args) {
        WSDLParser parser = new WSDLParser();
 
        Definitions defs = 
        		parser.parse("/Users/srinandansridhar/Documents/Accounts/Prudential/annuities-wsdl/ContractInformation_V1.3.wsdl");
        		//parser.parse("/Users/srinandansridhar/Downloads/FoxEDFEmailEBO/FoxEDFEventProducer_BPEL_Client_ep.wsdl");
        		//parser.parse("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
 
        out("-------------- WSDL Details --------------");
        out("TargenNamespace: \t" + defs.getTargetNamespace());
        //out("Style: \t\t\t" + defs.getStyle());
        if (defs.getDocumentation() != null) {
            out("Documentation: \t\t" + defs.getDocumentation());
        }
        out("\n");
 
        /* For detailed schema information see the FullSchemaParser.java sample.*/
        out("Schemas: ");
        for (Schema schema : defs.getSchemas()) {
            out("  TargetNamespace: \t" + schema.getTargetNamespace());
        }
        out("\n");
         
        out("Messages: ");
        for (Message msg : defs.getMessages()) {
            out("  Message Name: " + msg.getName());
            out("  Message Parts: ");
            for (Part part : msg.getParts()) {
                out("    Part Name: " + part.getName());
                out("    Part Element: " + ((part.getElement() != null) ? part.getElement() : "not available!"));
                out("    Part Type: " + ((part.getType() != null) ? part.getType() : "not available!" ));
                out("");
            }
        }
        out("");
 
        out("PortTypes: ");
        for (PortType pt : defs.getPortTypes()) {
            out("  PortType Name: " + pt.getName());
            out("  PortType Operations: ");
            for (Operation op : pt.getOperations()) {
                /*out("    Operation Name: " + op.getName());
                out("    NamespaceUri: " + op.getNamespaceUri());
                out("    Prefix: " + op.getPrefix());*/
                
                out("    Operation Input Name: "
                    + ((op.getInput().getName() != null) ? op.getInput().getName() : "not available!"));
                
                //out("    Operation Input Message: "
                //    + op.getInput().getMessage().getQname());
                
                Message msg = op.getInput().getMessage();
                for (Part p : msg.getParts()){
                	Element e = p.getElement();
                	out(e.getName() + " " + e.getType().getNamespaceURI());
                }
                
                /*out("    Operation Output Name: "
                    + ((op.getOutput().getName() != null) ? op.getOutput().getName() : "not available!"));
                out("    Operation Output Message: "
                    + op.getOutput().getMessage().getQname());
                out("    Operation Faults: ");
                if (op.getFaults().size() > 0) {
                    for (Fault fault : op.getFaults()) {
                        out("      Fault Name: " + fault.getName());
                        out("      Fault Message: " + fault.getMessage().getQname());
                    }
                } else out("      There are no faults available!");*/
                 
            }
            out("");
        }
        out("");
 
/*        out("Bindings: ");
        for (Binding bnd : defs.getBindings()) {
            out("  Binding Name: " + bnd.getName());
            out("  Binding Type: " + bnd.getPortType().getName());
            out("  Binding Protocol: " + bnd.getBinding().getProtocol());
            if(bnd.getBinding() instanceof AbstractSOAPBinding) out("  Style: " + (((AbstractSOAPBinding)bnd.getBinding()).getStyle()));
            out("  Binding Operations: ");
            for (BindingOperation bop : bnd.getOperations()) {
                out("    Operation Name: " + bop.getName());
                if(bnd.getBinding() instanceof AbstractSOAPBinding) {
                    out("    Operation SoapAction: " + bop.getOperation().getSoapAction());
                    out("    SOAP Body Use: " + bop.getInput().getBindingElements().get(0).getUse());
                }
            }
            out("");
        }
        out("");
 
        out("Services: ");
        for (Service service : defs.getServices()) {
            out("  Service Name: " + service.getName());
            out("  Service Potrs: ");
            for (Port port : service.getPorts()) {
                out("    Port Name: " + port.getName());
                out("    Port Binding: " + port.getBinding().getName());
                out("    Port Address Location: " + port.getAddress().getLocation()
                    + "\n");
            }
        }
        out("");*/
    }
 
    private static void out(String str) {
        System.out.println(str);
    }
}