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

var config = {
  async: false,
  defs: loadDefs(defs_dir),
  plugins: loadPlugins(plugin_dir),
  getFile: fs.readFileSync,
};

var server = new tern.Server(config);

process.on('message', function(msg) {
  clearTimeout(shutdown);
  shutdown = setTimeout(doShutdown, maxIdleTime);
  switch(msg.command) {
    case 'request':
      server.request(msg.data, function(e, out) {
        msg.data = out;
        process.send(msg);
      });
      break;
    case 'addfiles':
      msg.data.forEach(function(x) {
        server.addFile(x);
      });
      msg.data = 'success';
      process.send(msg);
    break;
  }
});

process.on("SIGINT", function() { process.exit(); });
process.on("SIGTERM", function() { process.exit(); });
