// @ts-check

require('reflect-metadata');
require('setimmediate');
const { Container } = require('inversify');
const { FrontendApplicationConfigProvider } = require('@theia/core/lib/browser/frontend-application-config-provider');

FrontendApplicationConfigProvider.set({
    "applicationName": "Continuum",
    "defaultTheme": {
        "light": "light",
        "dark": "dark"
    },
    "defaultIconTheme": "theia-file-icons",
    "electron": {
        "windowOptions": {},
        "showWindowEarly": true,
        "splashScreenOptions": {},
        "uriScheme": "theia"
    },
    "defaultLocale": "",
    "validatePreferencesSchema": true,
    "reloadOnReconnect": false,
    "uriScheme": "theia",
    "preferences": {
        "files.enableTrash": false
    }
});


self.MonacoEnvironment = {
    getWorkerUrl: function (moduleId, label) {
        return './editor.worker.js';
    }
}

function load(container, jsModule) {
    return Promise.resolve(jsModule)
        .then(containerModule => container.load(containerModule.default));
}

async function preload(container) {
    try {
        await load(container, import('@theia/core/lib/browser/preload/preload-module'));
        await load(container, import('@theia/core/lib/browser-only/preload/frontend-only-preload-module'));
        const { Preloader } = require('@theia/core/lib/browser/preload/preloader');
        const preloader = container.get(Preloader);
        await preloader.initialize();
    } catch (reason) {
        console.error('Failed to run preload scripts.');
        if (reason) {
            console.error(reason);
        }
    }
}

module.exports = (async () => {
    const { messagingFrontendModule } = require('@theia/core/lib/browser/messaging/messaging-frontend-module');
    const container = new Container();
    container.load(messagingFrontendModule);
    const { messagingFrontendOnlyModule } = require('@theia/core/lib/browser-only/messaging/messaging-frontend-only-module');
    container.load(messagingFrontendOnlyModule);

    await preload(container);

    
    const { MonacoInit } = require('@theia/monaco/lib/browser/monaco-init');
    ;

    const { FrontendApplication } = require('@theia/core/lib/browser');
    const { frontendApplicationModule } = require('@theia/core/lib/browser/frontend-application-module');    
    const { loggerFrontendModule } = require('@theia/core/lib/browser/logger-frontend-module');

    container.load(frontendApplicationModule);
    const { frontendOnlyApplicationModule } = require('@theia/core/lib/browser-only/frontend-only-application-module');
    container.load(frontendOnlyApplicationModule);
    
    container.load(loggerFrontendModule);
    const { loggerFrontendOnlyModule } = require('@theia/core/lib/browser-only/logger-frontend-only-module');
    container.load(loggerFrontendOnlyModule);

    try {
        await load(container, import('@theia/core/lib/browser-only/i18n/i18n-frontend-only-module'));
        await load(container, import('@theia/core/lib/browser/menu/browser-menu-module'));
        await load(container, import('@theia/core/lib/browser/window/browser-window-module'));
        await load(container, import('@theia/core/lib/browser/keyboard/browser-keyboard-module'));
        await load(container, import('@theia/core/lib/browser/request/browser-request-module'));
        await load(container, import('@theia/filesystem/lib/browser/filesystem-frontend-module'));
        await load(container, import('@theia/filesystem/lib/browser-only/browser-only-filesystem-frontend-module'));
        await load(container, import('@theia/filesystem/lib/browser/download/file-download-frontend-module'));
        await load(container, import('@theia/filesystem/lib/browser/file-dialog/file-dialog-module'));
        await load(container, import('@theia/variable-resolver/lib/browser/variable-resolver-frontend-module'));
        await load(container, import('@theia/workspace/lib/browser/workspace-frontend-module'));
        await load(container, import('@theia/workspace/lib/browser-only/workspace-frontend-only-module'));
        await load(container, import('@theia/markers/lib/browser/problem/problem-frontend-module'));
        await load(container, import('@theia/messages/lib/browser/messages-frontend-module'));
        await load(container, import('@theia/editor/lib/browser/editor-frontend-module'));
        await load(container, import('@theia/outline-view/lib/browser/outline-view-frontend-module'));
        await load(container, import('@theia/monaco/lib/browser/monaco-frontend-module'));
        await load(container, import('@theia/navigator/lib/browser/navigator-frontend-module'));
        await load(container, import('@theia/userstorage/lib/browser/user-storage-frontend-module'));
        await load(container, import('@theia/preferences/lib/browser/preference-frontend-module'));
        await load(container, import('@theia/process/lib/common/process-common-module'));
        await load(container, import('@continuum/workflow-editor-extension/lib/browser/WorkflowEditorExtentionModule'));
        
        MonacoInit.init(container);
        ;
        await start();
    } catch (reason) {
        console.error('Failed to start the frontend application.');
        if (reason) {
            console.error(reason);
        }
    }

    function start() {
        (window['theia'] = window['theia'] || {}).container = container;
        return container.get(FrontendApplication).start();
    }
})();
