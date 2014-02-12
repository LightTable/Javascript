/***
 *   When working to integrate Tern into LightTable, I needed the find context
 * function to implement the jump to definition functionality. Tern returns data
 * from the server in character offsets and CodeMirror only understands line +
 * char pairs. Since the work has already been done but isn't exposed as an api,
 * the work around for now is to use these copied functions.
 *
 * Credit:
 *   These functions have been shamelessly copied from Marijnh's CodeMirror tern
 * addon.
 *
 * original author: Marijn Haverbeke
 * github:          https://github.com/marijnh
 * source:          https://github.com/marijnh/CodeMirror/blob/master/addon/tern/tern.js
 */

var cmtools;
(function(mod) {
  mod(cmtools = cmtools || {}, CodeMirror);
})(function(cmtools, CodeMirror) {
  var Pos = CodeMirror.Pos;

  cmtools.findContext = function(doc, data) {
    var before = data.context.slice(0, data.contextOffset).split("\n");
    var startLine = data.start.line - (before.length - 1);
    var start = Pos(startLine, (before.length == 1 ? data.start.ch : doc.getLine(startLine).length) - before[0].length);

    var text = doc.getLine(startLine).slice(start.ch);
    for (var cur = startLine + 1; cur < doc.lineCount() && text.length < data.context.length; ++cur)
      text += "\n" + doc.getLine(cur);
    if (text.slice(0, data.context.length) == data.context) return data;

    var cursor = doc.getSearchCursor(data.context, 0, false);
    var nearest, nearestDist = Infinity;
    while (cursor.findNext()) {
      var from = cursor.from(), dist = Math.abs(from.line - start.line) * 10000;
      if (!dist) dist = Math.abs(from.ch - start.ch);
      if (dist < nearestDist) { nearest = from; nearestDist = dist; }
    }
    if (!nearest) return null;

    if (before.length == 1)
      nearest.ch += before[0].length;
    else
      nearest = Pos(nearest.line + (before.length - 1), before[before.length - 1].length);
    if (data.start.line == data.end.line)
      var end = Pos(nearest.line, nearest.ch + (data.end.ch - data.start.ch));
    else
      var end = Pos(nearest.line + (data.end.line - data.start.line), data.end.ch);
    return {start: nearest, end: end};
  }
});
