import { ContainerModule, interfaces } from '@theia/core/shared/inversify';
import { FrontendApplicationContribution, KeybindingContribution, LabelProviderContribution, OpenHandler, WidgetFactory, bindViewContribution } from '@theia/core/lib/browser';
import { Tree } from '@theia/core/lib/browser/tree';
import WorkflowEditorOpenHandler from './handlers/WorkflowEditorOpenHandler';
import WorkflowEditorWidgetFactory from './widgets/workflow-editor/WorkflowEditorWidgetFactory';
import NodeRepoWidgetFactory from './widgets/node-repo/NodeRepoWidgetFactory';
import ContinuumThemeService from './theme/ContinuumThemeService';
import { ContinuumFrontendApplicationContribution } from './contribution/ContinuumFrontendApplicationContribution';
import ContinuumFileTreeLabelProviderContribution from './contribution/ContinuumLabelProviderContribution';
import WorkflowStatusWidgetFactory from './widgets/workflow-status/WorkflowStatusWidgetFactory';
import WorkflowStatusViewContribution from './widgets/workflow-status/WorkflowStatusViewContribution';
import { NodeRepoViewContribution } from './widgets/node-repo/NodeRepoViewContribution';
import { WorkflowStatusWidget } from './widgets/workflow-status/WorkflowStatusWidget';
import NodeRepoWidget from './widgets/node-repo/NodeRepoWidget';
import CreateNewWorkflowCommand from './command/CreateNewWorkflowCommand';
import { CommandContribution, MenuContribution } from '@theia/core';
import ContinuumCommandcontribution from './contribution/ContinuumCommandContribution';
import ContinuumMenuContribution from './contribution/ContinuumMenuContribution';
import ContinuumNodeDialog, { ContinuumNodeDialogProps } from './dialog/node-dialog/ContinuumNodeDialog';
import WorkflowViewerWidgetFactory from './widgets/workflow-viewer/WorkflowViewerWidgetFactory';
import WorkflowViewerOpenHandler from './handlers/WorkflowViewerOpenHandler';
import { MonacoLanguageRegistration } from './language/MonacoLanguageRegistration';
import { WorkflowEditorCommandContribution } from './contribution/WorkflowEditorCommandContribution';
import { WorkflowEditorMenuContribution } from './contribution/WorkflowEditorMenuContribution';
import { WorkflowEditorKeybindingContribution } from './contribution/WorkflowEditorKeybindingContribution';
import { WorkflowClipboardService } from './service/WorkflowClipboardService';
import NodeExplorerWidget from './widgets/node-explorer/NodeExplorerWidget';
import NodeExplorerWidgetFactory from './widgets/node-explorer/NodeExplorerWidgetFactory';
import { NodeExplorerViewContribution } from './widgets/node-explorer/NodeExplorerViewContribution';
import { createNodeExplorerTreeContainer } from './tree/node-explorer/NodeExplorerTreeContainer';
import { NodeExplorerTree } from './tree/node-explorer/NodeExplorerTree';
import { NodeExplorerTreeWidget } from './tree/node-explorer/NodeExplorerTreeWidget';
import NodeExplorerService from './service/NodeExplorerService';

export default new ContainerModule((bind) => {
    bind(ContinuumThemeService).toSelf().inSingletonScope();
    bind(MonacoLanguageRegistration).toSelf().inSingletonScope();
    bind(WorkflowClipboardService).toSelf().inSingletonScope();

    // Commands
    bind(CreateNewWorkflowCommand).toSelf().inSingletonScope();

    // OpenHandlers
    bind(OpenHandler).to(WorkflowEditorOpenHandler).inSingletonScope();
    bind(OpenHandler).to(WorkflowViewerOpenHandler).inSingletonScope();

    // Dialogs
    bind(ContinuumNodeDialog).toSelf().inSingletonScope();
    bind(ContinuumNodeDialogProps).toConstantValue({title: "Node Dialog"});

    // LabelProvider
    bind(LabelProviderContribution).to(ContinuumFileTreeLabelProviderContribution);

    // WorkflowEditorWidget
    bind(WorkflowEditorWidgetFactory).toSelf().inSingletonScope();
    bind(WidgetFactory).toService(WorkflowEditorWidgetFactory);

    // WorkflowViewerWidget
    bind(WorkflowViewerWidgetFactory).toSelf().inSingletonScope();
    bind(WidgetFactory).toService(WorkflowViewerWidgetFactory);

    // NodeRepo widget
    bind(NodeRepoWidget).toSelf().inSingletonScope();
    bind(NodeRepoWidgetFactory).toSelf().inSingletonScope();
    bind(WidgetFactory).toService(NodeRepoWidgetFactory);
    bindViewContribution(bind, NodeRepoViewContribution);

    // WorkflowStatus widget
    bind(WorkflowStatusWidget).toSelf().inSingletonScope();
    bind(WorkflowStatusWidgetFactory).toSelf().inSingletonScope();
    bind(WidgetFactory).toService(WorkflowStatusWidgetFactory);
    bindViewContribution(bind, WorkflowStatusViewContribution);

    // NodeExplorer widget - using Theia TreeWidget
    bind(NodeExplorerService).toSelf().inSingletonScope();

    // Create a SINGLE child container for all tree-related bindings
    // This ensures Tree, Model, and Widget all share the same state
    const NodeExplorerTreeContainer = Symbol('NodeExplorerTreeContainer');
    bind(NodeExplorerTreeContainer).toDynamicValue((ctx: interfaces.Context) => {
        return createNodeExplorerTreeContainer(ctx.container);
    }).inSingletonScope();

    bind(NodeExplorerTree).toDynamicValue((ctx: interfaces.Context) => {
        const container = ctx.container.get<interfaces.Container>(NodeExplorerTreeContainer);
        return container.get<NodeExplorerTree>(Tree);
    }).inSingletonScope();

    bind(NodeExplorerTreeWidget).toDynamicValue((ctx: interfaces.Context) => {
        const container = ctx.container.get<interfaces.Container>(NodeExplorerTreeContainer);
        return container.get<NodeExplorerTreeWidget>(NodeExplorerTreeWidget);
    }).inSingletonScope();

    bind(NodeExplorerWidget).toSelf().inSingletonScope();
    bind(NodeExplorerWidgetFactory).toSelf().inSingletonScope();
    bind(WidgetFactory).toService(NodeExplorerWidgetFactory);
    bindViewContribution(bind, NodeExplorerViewContribution);

    // FrontendViewContribution
    bind(ContinuumFrontendApplicationContribution).toSelf().inSingletonScope();
    bind(FrontendApplicationContribution).toService(ContinuumFrontendApplicationContribution);
    bind(CommandContribution).to(ContinuumCommandcontribution);
    bind(MenuContribution).to(ContinuumMenuContribution);

    // WorkflowEditor context menu contributions
    bind(CommandContribution).to(WorkflowEditorCommandContribution);
    bind(MenuContribution).to(WorkflowEditorMenuContribution);
    bind(KeybindingContribution).to(WorkflowEditorKeybindingContribution);
});
