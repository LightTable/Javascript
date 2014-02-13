var fs = require('fs'),
    path = require('path'),
    module_dir = path.join(__dirname, '../node_modules/tern'),
    plugin_dir = path.join(module_dir, 'plugin'),
    defs_dir = path.join(module_dir, 'defs'),
    maxIdleTime = 6e4 * 5, // Shut down after five minutes of inactivity
    shutdown = setTimeout(doShutdown, maxIdleTime),
    tern = require(module_dir);

function doShutdown() {
  console.log("Was idle for " + Math.floor(maxIdleTime / 6e4) + " minutes. Shutting down.");
  process.exit();
}

function loadDefs(dir) {
  return fs.readdirSync(dir)
    .map(function(x) {
      var fullPath = path.join(dir, x);
      return JSON.parse(fs.readFileSync(fullPath));
    });
}

function loadPlugins(dir) {
  var plugins = {};
  fs.readdirSync(plugin_dir)
    .forEach(function(x) {
       var name = path.basename(x).split('.js')[0];
       require(path.join(dir, x));
       plugins[name] = true;
    });
  return plugins;
}

function send(err, data, msg) {
  var result = {cb: -1};
  if (msg) {
    result.cb = msg.cb;
    result.command = msg.command;
  }
  if (err) {
    result.err = err;
    result.stack = err.stack;
    result.command  = 'error';
  }
  if (data) {
    result.data = data
  }
  process.send(result);
}

var config = {
  async: true,
  defs: loadDefs(defs_dir),
  plugins: loadPlugins(plugin_dir),
  getFile: function(x, cb) {
    fs.readFile(x, {encoding: 'utf8'}, cb)
  },
};

var server = new tern.Server(config);
var currentmsg;

process.on('message', function(msg) {
  clearTimeout(shutdown);
  shutdown = setTimeout(doShutdown, maxIdleTime);
  currentmsg = msg;
  switch(msg.command) {
    case 'request':
      server.request(msg.data, function(e, out) {
        send(e, out, msg);
      });
      break;
    case 'addfiles':
      msg.data.forEach(function(x) {
          server.addFile(x);
      });
      send(null, {}, msg);
    break;
  }
});

process.on("SIGINT", function() { process.exit(); });
process.on("SIGTERM", function() { process.exit(); });
process.on('uncaughtException', function (err) {
  send(err, {}, currentmsg);
});
