'use strict';
// Use the SDK's ArkTS parser and parent restrictions. This supplements etsStandaloneChecker;
// it does not run the complete ArkUI transform, emit bytecode, or build a HAP.
const fs = require('node:fs');
const path = require('node:path');

function loadSdk(loader) {
  require(path.join(loader, 'main.js'));
  const ts = require(path.join(loader, 'node_modules/typescript'));
  const components = require(path.join(loader, 'lib/component_map.js'));
  if (!ts.isEtsComponentExpression || !components.SPECIFIC_PARENT_COMPONENT?.has('Blank')) {
    throw new Error('SDK ArkUI parser/parent metadata unavailable; refusing an incomplete check.');
  }
  return { ts, components };
}

function checkSources(sdk, sources) {
  const { ts, components } = sdk;
  // Custom component names must be included so the SDK parses their trailing UI blocks.
  const names = [...new Set([...components.INNER_COMPONENT_NAMES,
    ...sources.flatMap(source => [...source.text.matchAll(/\bstruct\s+(\w+)/g)].map(match => match[1]))])];
  const errors = [];
  for (const source of sources) {
    const ast = ts.createSourceFile(source.file, source.text, ts.ScriptTarget.Latest, true,
      ts.ScriptKind.ETS, { ets: { components: names } });
    for (const diagnostic of ast.parseDiagnostics) {
      const position = ast.getLineAndCharacterOfPosition(diagnostic.start || 0);
      errors.push({ file: source.file, line: position.line + 1, column: position.character + 1,
        code: String(diagnostic.code), message: ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n') });
    }
    function visit(node) {
      if (ts.isEtsComponentExpression(node) && ts.isIdentifier(node.expression)) {
        const name = node.expression.text;
        const allowed = components.SPECIFIC_PARENT_COMPONENT.get(name);
        if (allowed) {
          let parent = node.parent;
          while (parent && !ts.isEtsComponentExpression(parent)) parent = parent.parent;
          // An unknown custom-component/builder parent is not assumed to be a native container.
          // This matches ets-loader's checkNonspecificParents: native parents only.
          if (parent && ts.isIdentifier(parent.expression) &&
              components.INNER_COMPONENT_NAMES.has(parent.expression.text) && !allowed.has(parent.expression.text)) {
            const position = ast.getLineAndCharacterOfPosition(node.getStart(ast));
            errors.push({ file: source.file, line: position.line + 1, column: position.character + 1,
              code: '10905201', message: "The '" + name + "' component can only be nested in the '" +
                [...allowed].join(',') + "' parent component." });
          }
        }
      }
      ts.forEachChild(node, visit);
    }
    visit(ast);
  }
  return errors;
}

function readSources(root) {
  const result = [];
  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(file);
      else if (entry.isFile() && file.endsWith('.ets')) result.push({ file, text: fs.readFileSync(file, 'utf8') });
    }
  }
  visit(root);
  return result.sort((a, b) => a.file.localeCompare(b.file));
}

function checkProject(loader, root) {
  const sources = readSources(root);
  const errors = checkSources(loadSdk(loader), sources);
  return { files: sources.length, errors: errors.map(error => 'ArkUI:ERROR File: ' + error.file + ':' +
    error.line + ':' + error.column + '\n ' + error.code + ': ' + error.message) };
}

module.exports = { loadSdk, checkSources, checkProject };
