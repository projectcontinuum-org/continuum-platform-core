import * as monaco from 'monaco-editor';

let freemarkerRegistered = false;

/**
 * Register FreeMarker template language support in Monaco Editor
 * Provides syntax highlighting for FreeMarker directives, interpolations, and comments
 */
export const registerFreemarkerLanguage = () => {
  if (freemarkerRegistered) return;
  
  try {
    // Register the language
    monaco.languages.register({ id: 'freemarker' });

    // Define syntax highlighting rules
    monaco.languages.setMonarchTokensProvider('freemarker', {
      defaultToken: '',
      tokenPostfix: '.ftl',

      keywords: [
        'if', 'else', 'elseif', 'list', 'break', 'return', 'switch', 'case', 'default',
        'macro', 'function', 'nested', 'assign', 'local', 'global', 'include', 'import',
        'noparse', 'compress', 'escape', 'noescape', 'attempt', 'recover', 'visit', 'recurse',
        'fallback', 'setting', 'stop', 'flush', 'lt', 'nt', 'rt', 't'
      ],

      builtins: [
        'true', 'false', 'as', 'in', 'using'
      ],

      operators: [
        '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=',
        '&&', '||', '++', '--', '+', '-', '*', '/', '&', '|', '^', '%',
        '<<', '>>', '>>>', '+=', '-=', '*=', '/=', '&=', '|=', '^=',
        '%=', '<<=', '>>=', '>>>='
      ],

      symbols: /[=><!~?:&|+\-*\/\^%]+/,
      escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

      tokenizer: {
        root: [
          // FreeMarker directives <#...>
          [/<#--/, 'comment', '@comment'],
          [/<#/, 'delimiter.directive', '@directive'],
          [/<\/#/, 'delimiter.directive', '@closingDirective'],
          
          // FreeMarker interpolations ${...}
          [/\$\{/, 'delimiter.interpolation', '@interpolation'],
          
          // Regular content
          [/[^<$]+/, 'text'],
          [/</, 'text'],
          [/\$/, 'text'],
        ],

        comment: [
          [/-->/, 'comment', '@pop'],
          [/[^-]+/, 'comment'],
          [/./, 'comment']
        ],

        directive: [
          [/>/, 'delimiter.directive', '@pop'],
          [/@[a-zA-Z_]\w*/, 'keyword'],
          [/#[a-zA-Z_]\w*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
          [/[a-zA-Z_]\w*/, 'identifier'],
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/'([^'\\]|\\.)*$/, 'string.invalid'],
          [/"/, 'string', '@string_double'],
          [/'/, 'string', '@string_single'],
          [/[0-9]+(\.[0-9]+)?/, 'number'],
          [/@symbols/, { cases: { '@operators': 'operator', '@default': '' } }],
          [/\s+/, ''],
        ],

        closingDirective: [
          [/>/, 'delimiter.directive', '@pop'],
          [/#[a-zA-Z_]\w*/, 'keyword'],
          [/\s+/, ''],
        ],

        interpolation: [
          [/\}/, 'delimiter.interpolation', '@pop'],
          [/[a-zA-Z_]\w*/, 'identifier'],
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/'([^'\\]|\\.)*$/, 'string.invalid'],
          [/"/, 'string', '@string_double'],
          [/'/, 'string', '@string_single'],
          [/[0-9]+(\.[0-9]+)?/, 'number'],
          [/@symbols/, { cases: { '@operators': 'operator', '@default': '' } }],
          [/[?!.]/, 'operator'],
          [/\s+/, ''],
        ],

        string_double: [
          [/[^\\"]+/, 'string'],
          [/@escapes/, 'string.escape'],
          [/\\./, 'string.escape.invalid'],
          [/"/, 'string', '@pop']
        ],

        string_single: [
          [/[^\\']+/, 'string'],
          [/@escapes/, 'string.escape'],
          [/\\./, 'string.escape.invalid'],
          [/'/, 'string', '@pop']
        ],
      },
    });

    // Define language configuration
    monaco.languages.setLanguageConfiguration('freemarker', {
      comments: {
        blockComment: ['<#--', '-->']
      },
      brackets: [
        ['<#', '>'],
        ['</#', '>'],
        ['${', '}'],
        ['{', '}'],
        ['[', ']'],
        ['(', ')']
      ],
      autoClosingPairs: [
        { open: '<#', close: '>' },
        { open: '${', close: '}' },
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '(', close: ')' },
        { open: '"', close: '"' },
        { open: "'", close: "'" }
      ],
      surroundingPairs: [
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '(', close: ')' },
        { open: '"', close: '"' },
        { open: "'", close: "'" }
      ]
    });

    freemarkerRegistered = true;
  } catch (error) {
    console.error('Failed to register FreeMarker language:', error);
  }
};
