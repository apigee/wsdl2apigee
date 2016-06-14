# wsdl2apigee
This application  can 1) Expose a SOAP Service as a REST API (over JSON) or 2) Expose a SOAP Service in a passthrough mode
for deployment on Apigee Edge

## Install
```
mvn install
```

## Usage
```
java -jar wsdl2apigee.jar com.apigee.proxywriter.GenerateProxy -wsdl <wsdl URL or Path>
```

### Other Options
```
java -jar wsdl2apigee.jar com.apigee.proxywriter.GenerateProxy -wsdl <wsdl URL or Path> -passthru=<true|false> -desc=<description> -allpost=<true|false> -opsmap=<opsmapping.xml> -service=<service name> -port=<port name> -debug=<true|false>

Defaults:
passthru=false; Do not convert to API/JSON. Treat as SOAP
allpost=false; Treat all wsdls operations as HTTP POST
service=If the wsdl contains > 1 service, specify the name. Otherwise, the first service is selected
port=If the service contains > 1 port, specify the port name. Otherwise, the first port is selected
debug=false; Set to true to enable trace.
``` 
OpsMap:
A file that maps WSDL operations to HTTP Verbs. A Sample Ops Mapping file looks like:
```
<proxywriter>
	<get>
		<name location="beginsWith">get</name>
		<name location="beginsWith">list</name>
		<name location="beginsWith">inq</name>
		<name location="beginsWith">search</name>
		<name location="beginsWith">retrieve</name>
	</get>
	<post>
		<name location="contains">create</name>
		<name location="beginsWith">add</name>
		<name location="beginsWith">process</name>
	</post>
	<put>
		<name location="contains">update</name>
		<name location="contains">change</name>
		<name location="beginsWith">modify</name>
		<name location="beginsWith">set</name>
	</put>
	<delete>
		<name location="beginsWith">delete</name>
		<name location="beginsWith">remove</name>
		<name location="beginsWith">del</name>
	</delete>
</proxywriter>
```