var child = require('child_process').fork('ternserver.js');

var files = ["/home/eric/.config/LightTable/plugins/Javascript/tern-testing.js",
             "/home/eric/.config/LightTable/plugins/Javascript/node/ternserver.js"]

function sendRequest(proc) {
  var data = {
    type: 'request',
    msg: {
      query: {
        type: 'completions',
        file: 'blergs.js',
        end: {ch: 2, line: 1},
        types: true,
        docs: true
      },
      files: [{
        type: 'full',
        text: '\r\nre\r\n',
        name: 'blergs.js'
      }]
    }
  }
  proc.send(data);
}

require('fs').rea
var result;
function listen(proc) {
  proc.on('message', function(data) {
    console.log(data);
    result = data;
  });
}

listen(child);
child.send({command: 'addfiles', data: files })
