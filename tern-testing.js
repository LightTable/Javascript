var child = require('child_process').fork('node/ternserver.js');
var fs = require('fs');

function sendRequest(proc, end) {
  var data = {
    command: 'request',
    data: {
      query: {
        type: 'definition',
        file: 'blergs.js',
        end: end,
      },
      files: [{
        type: 'full',
        text: fs.readFileSync('blergs.js', 'utf8'),
        name: 'blergs.js'
      }]
    }
  };
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
sendRequest(child, {ch: 10, line: 6});
