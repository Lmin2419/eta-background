'use strict';
// Project rule: a component slot must retain the supplying component's receiver.
// ArkUI @BuilderParam invokes a bare `this.builder` with the receiving child as `this`.
// Use `() => { this.builder(); }` for both local builders and forwarded slots.
// SDK AST only: this is not a renderer or a substitute for a device launch.
const fs = require('node:fs');
const path = require('node:path');

function loadSdk(loader) {
  require(path.join(loader, 'main.js'));
  return { ts: require(path.join(loader, 'node_modules/typescript')),
    components: require(path.join(loader, 'lib/component_map.js')) };
}

function readSources(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(root, entry.name);
    return entry.isDirectory() ? readSources(file) : entry.isFile() && file.endsWith('.ets') ?
      [{ file, text: fs.readFileSync(file, 'utf8') }] : [];
  }).sort((a, b) => a.file.localeCompare(b.file));
}

function analyzeSources(sdk, sources) {
  const { ts, components } = sdk;
  const names = [...new Set([...components.INNER_COMPONENT_NAMES,
    ...sources.flatMap(source => [...source.text.matchAll(/\bstruct\s+(\w+)/g)].map(match => match[1]))])];
  const asts = sources.map(source => ts.createSourceFile(source.file, source.text, ts.ScriptTarget.Latest,
    true, ts.ScriptKind.ETS, { ets: { components: names } }));
  const declarations = new Map();
  const errors = [], slots = [];
  const decorated = (node, name) => (ts.getAllDecorators(node) || []).some(decorator =>
    ts.isIdentifier(decorator.expression) && decorator.expression.text === name);
  for (const ast of asts) {
    for (const diagnostic of ast.parseDiagnostics) {
      const position = ast.getLineAndCharacterOfPosition(diagnostic.start || 0);
      errors.push({ file: ast.fileName, line: position.line + 1, column: position.character + 1,
        code: String(diagnostic.code), message: ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n') });
    }
    for (const node of ast.statements) {
      if (!ts.isStructDeclaration(node) || !node.name) continue;
      const builderParams = new Set(node.members.filter(member => decorated(member, 'BuilderParam'))
        .map(member => member.name?.text).filter(Boolean));
      declarations.set(path.resolve(ast.fileName) + '#' + node.name.text, builderParams);
    }
  }
  for (const ast of asts) {
    const aliases = new Map();
    for (const node of ast.statements) {
      if (!ts.isImportDeclaration(node) || !ts.isStringLiteral(node.moduleSpecifier)) continue;
      const bindings = node.importClause?.namedBindings;
      if (!bindings || !ts.isNamedImports(bindings)) continue;
      const moduleFile = path.resolve(path.dirname(ast.fileName), node.moduleSpecifier.text.replace(/\.ets$/, '') + '.ets');
      for (const item of bindings.elements) aliases.set(item.name.text,
        moduleFile + '#' + (item.propertyName?.text || item.name.text));
    }
    function visit(node) {
      // Also inspect call expressions: a custom component inside a dialog initializer is not
      // necessarily an EtsComponentExpression. Ordinary object literals are not slot calls.
      if ((ts.isEtsComponentExpression(node) || ts.isCallExpression(node)) && ts.isIdentifier(node.expression)) {
        const name = node.expression.text;
        const params = declarations.get(path.resolve(ast.fileName) + '#' + name) || declarations.get(aliases.get(name));
        if (params) for (const arg of node.arguments || []) {
          if (!ts.isObjectLiteralExpression(arg)) continue;
          for (const property of arg.properties) {
            if (!ts.isPropertyAssignment(property) || !params.has(property.name?.text)) continue;
            let value = property.initializer;
            while (ts.isParenthesizedExpression(value)) value = value.expression;
            const position = ast.getLineAndCharacterOfPosition(value.getStart(ast));
            const entry = { file: ast.fileName, component: name, parameter: property.name.text,
              line: position.line + 1, column: position.character + 1,
              start: value.getStart(ast), end: value.getEnd(), expression: value.getText(ast) };
            slots.push(entry);
            if (ts.isPropertyAccessExpression(value) && value.expression.kind === ts.SyntaxKind.ThisKeyword) {
              errors.push({ ...entry, code: 'HA_BUILDER_RECEIVER', message: name + '.' + property.name.text +
                ' receives an unbound ' + value.getText(ast) + '; preserve the owner with () => { ' + value.getText(ast) + '(); }.' });
            }
          }
        }
      }
      ts.forEachChild(node, visit);
    }
    visit(ast);
  }
  return { files: sources.length, slots, errors };
}

function checkProject(loader, root) {
  const result = analyzeSources(loadSdk(loader), readSources(root));
  return { files: result.files, slots: result.slots.length, errors: result.errors.map(error =>
    'ArkUI:ERROR File: ' + error.file + ':' + error.line + ':' + error.column + '\n ' + error.code + ': ' + error.message) };
}

module.exports = { loadSdk, readSources, analyzeSources, checkProject };
