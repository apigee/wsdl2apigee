# wsdl2apigee
This application  can 1) Expose a SOAP Service as a REST API (over JSON) or 2) Exxpose a SOAP Service in a passthrough mode
for deployment on Apigee Edge

## Usage
```
java -jar wsdl2apigee.jar com.apigee.proxywriter.GenerateProxy -wsdl <wsdl URL or Path>
```

### Other Options
```
java -jar wsdl2apigee.jar com.apigee.proxywriter.GenerateProxy -wsdl <wsdl URL or Path> -passthru=<true|false> -desc=<description>

Defaults:
passthru=false
``` 
