 if ( ! properties.rootElement) {
   throw new Error('You must specify a rootElement');
 }
 else {
   var request = JSON.parse(context.getVariable("request.content"));
   if (!request[properties.rootElement]) {
     var newrequest = {};
     newrequest[properties.rootElement] = request;
     context.setVariable("request.content", JSON.stringify(newrequest));
   }
 }