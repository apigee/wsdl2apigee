// js file used to traverse response content , find and replace nil:true 
// ------------------------------------------------------------------
var what = Object.prototype.toString;

function walkObj(obj, fn) {
  var wo = what.call(obj);
  if (wo == "[object Object]") {
    Object.keys(obj).forEach(function(key){
      fn(obj, key);
      var item = obj[key], w = what.call(item);
      if (w == "[object Object]" || w == "[object Array]") {
        walkObj(item, fn);
      }
    });
  }
  else if (wo == "[object Array]") {
    obj.forEach(function(item, ix) {
      fn(obj, ix);
    });
    obj.forEach(function(item, ix) {
      var w = what.call(item);
      if (w == "[object Object]" || w == "[object Array]") {
        walkObj(item, fn);
      }
    });
  }
}

function checkAndFixNull(parent, key) {
  var value = parent[key], w = what.call(value);
  if ((w == "[object Object]") && (value.TEXT === null) && (value['@nil'] === true)) {
    parent[key] = null;
  }
}

var source = JSON.parse(context.getVariable('response.content'));
walkObj(source, checkAndFixNull);

// source now contains the transformed JSON hash
context.setVariable('response.content', JSON.stringify(source, null, 2));