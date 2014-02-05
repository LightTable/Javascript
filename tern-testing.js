var child = require('child_process').fork('ternserver.js');

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

var result;
function listen(proc) {
  proc.on('message', function(data) {
    console.log(data);
    result = data;
  });
}

listen(child);
sendRequest(child);
