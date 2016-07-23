# wsdl2apigee
wsdl2apigee is an open source project that extends Apigee Edge functionality to provide the ability to:

* Expose a SOAP Service as a REST API (over JSON or XML) or 
* Expose a SOAP Service in a pass through mode for deployment on Apigee Edge

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
``` 
## Output
You should see an API Proxy bundle generated in the same folder you ran the command. The bundle follows the convention of
{soap-servicename}.zip. You can import the bundle via the Edge Admin UI or tools like [apigeetool](https://www.npmjs.com/package/apigeetool).

## Support
This is an in-progress project of Apigee Corporation. No support is expressed or implied. Use at your own risk.

## Appendix
OpsMap:
A file that maps WSDL operations to HTTP Verbs. A Sample Ops Mapping file looks like:
```
<proxywriter>
	<get>
		<operation>
			<pattern>get</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>inq</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>search</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>list</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>retrieve</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>fetch</pattern>
			<location>beginsWith</location>
		</operation>
	</get>
	<post>
		<operation>
			<pattern>create</pattern>
			<location>contains</location>
		</operation>
		<operation>
			<pattern>add</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>process</pattern>
			<location>beginsWith</location>
		</operation>
	</post>
	<put>
		<operation>
			<pattern>update</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>change</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>modify</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>set</pattern>
			<location>beginsWith</location>
		</operation>
	</put>
	<delete>
		<operation>
			<pattern>delete</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>remove</pattern>
			<location>beginsWith</location>
		</operation>
		<operation>
			<pattern>del</pattern>
			<location>beginsWith</location>
		</operation>
	</delete>
</proxywriter>
```
