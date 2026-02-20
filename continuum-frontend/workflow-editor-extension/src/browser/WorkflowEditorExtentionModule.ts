import { ContainerModule } from '@theia/core/shared/inversify';
import { FrontendApplicationContribution, LabelProviderContribution, OpenHandler, WidgetFactory, bindViewContribution } from '@theia/core/lib/browser';
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

export default new ContainerModule((bind) => {
    bind(ContinuumThemeService).toSelf().inSingletonScope();
    bind(MonacoLanguageRegistration).toSelf().inSingletonScope();

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

    // FrontendViewContribution
    bind(ContinuumFrontendApplicationContribution).toSelf().inSingletonScope();
    bind(FrontendApplicationContribution).toService(ContinuumFrontendApplicationContribution);
    bind(CommandContribution).to(ContinuumCommandcontribution);
    bind(MenuContribution).to(ContinuumMenuContribution);
});