package com.apigee.utils;

import java.util.List;

public class WsdlDefinitions {

    private final List<Service> services;

    public List<Service> getServices() {
        return services;
    }

    public WsdlDefinitions(List<Service> services) {
        this.services = services;
    }

    public static class Service {

        private final List<Port> ports;
        private final String name;

        public Service(String name, List<Port> ports) {
            this.name = name;
            this.ports = ports;
        }

        public List<Port> getPorts() {
            return ports;
        }

        public String getName() {
            return name;
        }
    }

    public static class Port {
        private final String name;
        private final List<Operation> operations;

        public Port(String name, List<Operation> operations) {
            this.name = name;
            this.operations = operations;
        }
        public String getName() {
            return name;
        }

        public List<Operation> getOperations() {
            return operations;
        }

    }

    public static class Operation {

        private final String name;
        private final String description;
        private final String method;
        private final String path;
        private final List<String> parameters;

        public Operation(String name, String description, String method, String path, List<String> parameters) {
            this.name = name;
            this.description = description;
            this.method = method;
            this.path = path;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public List<String> getParameters() {
            return parameters;
        }
    }
}
