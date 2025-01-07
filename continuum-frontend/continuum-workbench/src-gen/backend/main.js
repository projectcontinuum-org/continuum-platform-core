// @ts-check
const { BackendApplicationConfigProvider } = require('@theia/core/lib/node/backend-application-config-provider');
const main = require('@theia/core/lib/node/main');

BackendApplicationConfigProvider.set({
    "singleInstance": true,
    "frontendConnectionTimeout": 0,
    "configurationFolder": ".theia"
});

globalThis.extensionInfo = [
    {
        "name": "@theia/core",
        "version": "1.57.1"
    },
    {
        "name": "@theia/filesystem",
        "version": "1.57.1"
    },
    {
        "name": "@theia/variable-resolver",
        "version": "1.57.1"
    },
    {
        "name": "@theia/workspace",
        "version": "1.57.1"
    },
    {
        "name": "@theia/markers",
        "version": "1.57.1"
    },
    {
        "name": "@theia/messages",
        "version": "1.57.1"
    },
    {
        "name": "@theia/editor",
        "version": "1.57.1"
    },
    {
        "name": "@theia/outline-view",
        "version": "1.57.1"
    },
    {
        "name": "@theia/monaco",
        "version": "1.57.1"
    },
    {
        "name": "@theia/navigator",
        "version": "1.57.1"
    },
    {
        "name": "@theia/userstorage",
        "version": "1.57.1"
    },
    {
        "name": "@theia/preferences",
        "version": "1.57.1"
    },
    {
        "name": "@theia/process",
        "version": "1.57.1"
    },
    {
        "name": "@continuum/workflow-editor-extension",
        "version": "0.0.0"
    }
];

const serverModule = require('./server');
const serverAddress = main.start(serverModule());

serverAddress.then((addressInfo) => {
    if (process && process.send && addressInfo) {
        process.send(addressInfo);
    }
});

globalThis.serverAddress = serverAddress;
