package com.apigee.proxywriter;

public class GenerateProxyOptions {
    private final String wsdl;
    private final String port;
    private final boolean passthrough;
    private final String description;
    private final String basepath;
    private final String vHosts;
    private final boolean cors;
    private final boolean apiKey;
    private final boolean oauth;
    private final boolean quota;

    public GenerateProxyOptions(String wsdl, String port, boolean passthrough, String description, String basepath, String vHosts, boolean cors, boolean apiKey, boolean oauth, boolean quota) {
        this.wsdl = wsdl;
        this.port = port;
        this.passthrough = passthrough;
        this.description = description;
        this.basepath = basepath;
        this.vHosts = vHosts;
        this.cors = cors;
        this.apiKey = apiKey;
        this.oauth = oauth;
        this.quota = quota;
    }

    public String getWsdl() {
        return wsdl;
    }

    public String getPort() {
        return port;
    }

    public boolean isPassthrough() {
        return passthrough;
    }

    public String getDescription() {
        return description;
    }

    public String getBasepath() {
        return basepath;
    }

    public String getvHosts() {
        return vHosts;
    }

    public boolean isCors() {
        return cors;
    }

    public boolean isApiKey() {
        return apiKey;
    }

    public boolean isOauth() {
        return oauth;
    }

    public boolean isQuota() {
        return quota;
    }
}
