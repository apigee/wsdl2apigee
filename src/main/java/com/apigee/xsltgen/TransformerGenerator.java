package com.apigee.xsltgen;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.ArrayList;

public class TransformerGenerator {
    private static final String optString = "vi:p:s:"; // getopt style
    private Hashtable<String, Object> options = new Hashtable<String, Object> ();

    public TransformerGenerator (String[] args)
        throws java.lang.Exception {
        getOpts(args);
    }

    private void getOpts(String[] args)
        throws java.lang.Exception {
        // Parse command line args for args in the following format:
        //   -a value -b value2 ... ...

        // sanity checks
        if (args == null) return;
        if (args.length == 0) return;
        if (optString == null) return;
        final String argPrefix = "-";
        String patternString = "^" + argPrefix + "([" + optString.replaceAll(":","") + "])";

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(patternString);

        int L = args.length;
        for(int i=0; i < L; i++) {
            String arg = args[i];
            java.util.regex.Matcher m = p.matcher(arg);
            if (!m.matches()) {
                throw new java.lang.Exception("The command line arguments are improperly formed. Use a form like '-a value' or just '-b' .");
            }

            char ch = arg.charAt(1);
            int pos = optString.indexOf(ch);

            if ((pos != optString.length() - 1) && (optString.charAt(pos+1) == ':')) {
                if (i+1 < L) {
                    i++;
                    Object current = this.options.get(m.group(1));
                    ArrayList<String> newList;
                    if (current == null) {
                        // not a previously-seen option
                        this.options.put(m.group(1), args[i]);
                    }
                    else if (current instanceof ArrayList<?>) {
                        // previously seen, and already a list
                        newList = (ArrayList<String>) current;
                        newList.add(args[i]);
                    }
                    else {
                        // we have one value, need to make a list
                        newList = new ArrayList<String>();
                        newList.add((String)current);
                        newList.add(args[i]);
                        this.options.put(m.group(1), newList);
                    }
                }
                else {
                    throw new java.lang.Exception("Incorrect arguments.");
                }
            }
            else {
                // a "no-value" argument, like -v for verbose
                options.put(m.group(1), (Boolean) true);
            }
        }
    }

    private static String optionAsString(Object o) {
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Boolean) {
            return o.toString();
        }
        return null;
    }

    private static ArrayList<String> optionAsList(Object o) {
        if (o instanceof ArrayList<?>) {
            return (ArrayList<String>) o;
        }

        ArrayList<String> list = new ArrayList<String>();

        if (o instanceof String) {
            list.add((String)o);

        }
        return list;
    }

    private void maybeShowOptions() {
        Boolean verbose = (Boolean) this.options.get("v");
        if (verbose != null && verbose) {
            System.out.println("options:");
            Enumeration e = this.options.keys();
            while(e.hasMoreElements()) {
                // iterate through Hashtable keys Enumeration
                String k = (String) e.nextElement();
                Object o = this.options.get(k);
                String v = null;
                v = (o.getClass().equals(Boolean.class)) ?  "true" : (String) o;
                System.out.println("  " + k + ": " + v);
            }

            // enumerate properties here?
        }
    }

    public void run() throws Exception {
        Boolean verbose = (Boolean) options.get("v");
        verbose = verbose != null && verbose;

        String rulesFilename = (String) options.get("i");
        if (rulesFilename==null) {
            usage();
            return;
        }
        RuleSet rs = RuleSet.readFromFile(rulesFilename);
        if (!rs.validate()) {
            throw new Exception("invalid ruleset");
        }
        rs.show();
        String t = rs.getTransform("SOAP11");
        System.out.printf("%s\n", t);
    }

    public static void usage() {
        System.out.println("TransformerGenerator: generate an XSLT according to simple rules.\n");
        System.out.println("Usage:\n  java TransformerGenerator [-v] -i rulesfile");
    }


    public static void main(String[] args) {
        try {
            TransformerGenerator me = new TransformerGenerator(args);
            me.run();
        }
        catch (java.lang.Exception exc1) {
            System.out.println("Exception:" + exc1.toString());
            exc1.printStackTrace();
        }
    }

}
