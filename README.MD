# wsdl2apigee
wsdl2apigee is an open source project that extends Apigee Edge functionality to provide the ability to:

* Expose a SOAP Service as a REST API (over JSON or XML) or
* Expose a SOAP Service in a pass through mode for deployment on Apigee Edge

## Support
This is an open-source project of the Apigee Corporation. It is not covered by Apigee support contracts. However, we will support you as best we can. For help, please open an issue in this GitHub project. You are also always welcome to submit a pull request.

## Install
```
mvn install
```

## Usage
```
java -jar wsdl2apigee-1.0.0-jar-with-dependencies.jar -wsdl=<wsdl URL or Path>
```

### Other Options
```
java -jar wsdl2apigee-1.0.0-jar-with-dependencies.jar -wsdl=<wsdl URL or Path> -passthru=<true|false> -desc=<description> -allpost=<true|false> -opsmap=<opsmapping.xml> -service=<service name> -port=<port name> -debug=<true|false> -oauth=<true|false> -vhosts=comma separated values for virtuals hosts> -build=<build folder name> -cors=<true|false> -apikey=<true|false> -quota=<true|false> -basepath=<specify base path>

Defaults:
passthru=false            Do not convert to API/JSON. Treat as SOAP
allpost=false             Treat all wsdls operations as HTTP POST
service=If the wsdl contains > 1 service, specify the name. Otherwise, the first service is selected
port=If the service contains > 1 port, specify the port name. Otherwise, the first port is selected
debug=false               Set to true to enable trace.
vhosts=default
oauth=false               Don't add the oauth policy
apikey=false              Don't add apikey verification
quota=false               Works only if apikey or oauth is set
basepath=Uses the basepath from the wsdl
cors=false                Don't enable CORS; Works only for SOAP to REST
build=specify build folder   default is temp folder; ensure user has access to read/write to temp folder
opsmap=specify operations map
backendurl = Specify the target backend url
backendurlvalidation=false    Specify to disable the backendurl validation
kvm = Specify a KVM from where get the target endpoint url as entry
```
## Output
You should see an API Proxy bundle generated in the same folder you ran the command. The bundle follows the convention of
{soap-servicename}.zip. You can import the bundle via the Edge Admin UI or tools like [apigeetool](https://www.npmjs.com/package/apigeetool).

## Open API Specification
When a proxy bundle for SOAP->REST is generated, an attempt is made to generate an Open API specification.
This document is hosted at http://{proxy-url}/{basePath}/openapi.json

NOTE: The Open API specification is not expected to be accurate for all WSDLs. For ex: OAS does not support xsd:choice.

## WSDL
When a proxy bundle for SOAP PassThru is generated, the WSDL is hosted at http://{proxy-url}/{basePath}?wsdl

## Appendix
OpsMap:
A file that maps WSDL operations to HTTP Verbs. A Sample Ops Mapping file looks like:
```
{
  "proxywriter": {
    "get": {
      "operation": [
        {
          "pattern": "get",
          "location": "beginsWith"
        },
        {
          "pattern": "inq",
          "location": "beginsWith"
        },
        {
          "pattern": "search",
          "location": "beginsWith"
        },
        {
          "pattern": "list",
          "location": "beginsWith"
        },
        {
          "pattern": "retrieve",
          "location": "beginsWith"
        }
      ]
    },
    "post": {
      "operation": [
        {
          "pattern": "create",
          "location": "contains"
        },
        {
          "pattern": "add",
          "location": "beginsWith"
        },
        {
          "pattern": "process",
          "location": "beginsWith"
        }
      ]
    },
    "put": {
      "operation": [
        {
          "pattern": "update",
          "location": "beginsWith"
        },
        {
          "pattern": "change",
          "location": "beginsWith"
        },
        {
          "pattern": "modify",
          "location": "beginsWith"
        },
        {
          "pattern": "set",
          "location": "beginsWith"
        }
      ]
    },
    "delete": {
      "operation": [
        {
          "pattern": "delete",
          "location": "beginsWith"
        },
        {
          "pattern": "remove",
          "location": "beginsWith"
        },
        {
          "pattern": "del",
          "location": "beginsWith"
        }
      ]
    }
  }
}
```

