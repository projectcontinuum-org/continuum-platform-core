/**
 * This file can be edited to customize webpack configuration.
 * To reset delete this file and rerun theia build again.
 */
// @ts-check
const configs = require('./gen-webpack.config.js');
const nodeConfig = require('./gen-webpack.node.config.js');
const path = require("path");

/**
 * Expose bundled modules on window.theia.moduleName namespace, e.g.
 * window['theia']['@theia/core/lib/common/uri'].
 * Such syntax can be used by external code, for instance, for testing.
 configs[0].module.rules.push({
 test: /\.js$/,
 loader: require.resolve('@theia/application-manager/lib/expose-loader')
 }); */

configs.forEach(config => {
    config.output.path = path.resolve(__dirname, 'build', 'frontend')
});

nodeConfig.config.output.path = path.resolve(__dirname, 'build', 'backend')

module.exports = [
    ...configs,
    nodeConfig.config,
];
