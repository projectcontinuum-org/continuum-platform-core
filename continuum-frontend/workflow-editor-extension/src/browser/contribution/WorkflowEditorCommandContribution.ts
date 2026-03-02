import { inject, injectable } from "@theia/core/shared/inversify";
import { CommandContribution, CommandRegistry } from "@theia/core";
import { WorkflowEditorCommands } from "../command/WorkflowEditorCommands";
import WorkflowEditorWidgetFactory from "../widgets/workflow-editor/WorkflowEditorWidgetFactory";
import WorkflowEditorWidget from "../widgets/workflow-editor/WorkflowEditorWidget";

@injectable()
export class WorkflowEditorCommandContribution implements CommandContribution {

    constructor(
        @inject(WorkflowEditorWidgetFactory)
        protected readonly workflowEditorWidgetFactory: WorkflowEditorWidgetFactory
    ) {}

    registerCommands(registry: CommandRegistry): void {
        // Run Workflow
        registry.registerCommand(WorkflowEditorCommands.RUN_WORKFLOW, {
            execute: () => this.getActiveWorkflowEditor()?.runWorkflow(),
            isEnabled: () => this.hasActiveWorkflowEditor(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Copy Nodes
        registry.registerCommand(WorkflowEditorCommands.COPY_NODES, {
            execute: () => this.getActiveWorkflowEditor()?.copySelectedNodes(),
            isEnabled: () => this.hasSelectedNodes(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Paste Nodes
        registry.registerCommand(WorkflowEditorCommands.PASTE_NODES, {
            execute: () => this.getActiveWorkflowEditor()?.pasteNodes(),
            isEnabled: () => this.hasClipboardContent(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Cut Nodes
        registry.registerCommand(WorkflowEditorCommands.CUT_NODES, {
            execute: () => this.getActiveWorkflowEditor()?.cutSelectedNodes(),
            isEnabled: () => this.hasSelectedNodes(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Delete Nodes
        registry.registerCommand(WorkflowEditorCommands.DELETE_NODES, {
            execute: () => this.getActiveWorkflowEditor()?.deleteSelectedNodes(),
            isEnabled: () => this.hasSelectedNodes(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Select All
        registry.registerCommand(WorkflowEditorCommands.SELECT_ALL_NODES, {
            execute: () => this.getActiveWorkflowEditor()?.selectAllNodes(),
            isEnabled: () => this.hasActiveWorkflowEditor(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Undo
        registry.registerCommand(WorkflowEditorCommands.UNDO, {
            execute: () => this.getActiveWorkflowEditor()?.undo(),
            isEnabled: () => this.canUndo(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });

        // Redo
        registry.registerCommand(WorkflowEditorCommands.REDO, {
            execute: () => this.getActiveWorkflowEditor()?.redo(),
            isEnabled: () => this.canRedo(),
            isVisible: () => this.hasActiveWorkflowEditor()
        });
    }

    protected getActiveWorkflowEditor(): WorkflowEditorWidget | undefined {
        return this.workflowEditorWidgetFactory.activeWidget;
    }

    protected hasActiveWorkflowEditor(): boolean {
        return this.getActiveWorkflowEditor() !== undefined;
    }

    protected hasSelectedNodes(): boolean {
        return this.getActiveWorkflowEditor()?.hasSelectedNodes() ?? false;
    }

    protected hasClipboardContent(): boolean {
        return this.getActiveWorkflowEditor()?.hasClipboardContent() ?? false;
    }

    protected canUndo(): boolean {
        return this.getActiveWorkflowEditor()?.canUndo() ?? false;
    }

    protected canRedo(): boolean {
        return this.getActiveWorkflowEditor()?.canRedo() ?? false;
    }
}
