/**
 * This file can be edited to customize webpack configuration.
 * To reset delete this file and rerun theia build again.
 */
// @ts-check
const configs = require('./gen-webpack.config.js');
const nodeConfig = require('./gen-webpack.node.config.js');
const MonacoPlugin = require('monaco-editor-webpack-plugin');

/**
 * Expose bundled modules on window.theia.moduleName namespace, e.g.
 * window['theia']['@theia/core/lib/common/uri'].
 * Such syntax can be used by external code, for instance, for testing.
 configs[0].module.rules.push({
 test: /\.js$/,
 loader: require.resolve('@theia/application-manager/lib/expose-loader')
 }); */
if (configs.length > 0 && configs[0].plugins) {
    /** @type {any} */
    const pluginsArray = configs[0].plugins;
    pluginsArray.push(
        new MonacoPlugin({
            // Specify languages explicitly to avoid conflicts with Theia's Monaco core
            languages: [
                'json', 'typescript', 'javascript', 'kotlin',
                'python', 'java', 'sql', 'html', 'css', 'xml',
                'yaml', 'markdown', 'shell', 'dockerfile', 'php',
                'ruby', 'go', 'rust', 'cpp', 'csharp', 'freemarker2'
            ],
            customLanguages: [],
            filename: '[name].monaco-worker.js'
        })
    );
}ˀ

module.exports = [
    ...configs,
    nodeConfig.config,
];
