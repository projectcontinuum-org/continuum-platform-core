import { injectable } from "@theia/core/shared/inversify";
import { MenuContribution, MenuModelRegistry } from "@theia/core";
import { WorkflowEditorCommands } from "../command/WorkflowEditorCommands";
import { WorkflowEditorContextMenu } from "../menu/WorkflowEditorContextMenu";

@injectable()
export class WorkflowEditorMenuContribution implements MenuContribution {

    registerMenus(registry: MenuModelRegistry): void {
        // Undo/Redo group
        registry.registerMenuAction(WorkflowEditorContextMenu.UNDO_REDO, {
            commandId: WorkflowEditorCommands.UNDO.id,
            order: '1'
        });
        registry.registerMenuAction(WorkflowEditorContextMenu.UNDO_REDO, {
            commandId: WorkflowEditorCommands.REDO.id,
            order: '2'
        });

        // Cut/Copy/Paste group
        registry.registerMenuAction(WorkflowEditorContextMenu.CUT_COPY_PASTE, {
            commandId: WorkflowEditorCommands.CUT_NODES.id,
            order: '1'
        });
        registry.registerMenuAction(WorkflowEditorContextMenu.CUT_COPY_PASTE, {
            commandId: WorkflowEditorCommands.COPY_NODES.id,
            order: '2'
        });
        registry.registerMenuAction(WorkflowEditorContextMenu.CUT_COPY_PASTE, {
            commandId: WorkflowEditorCommands.PASTE_NODES.id,
            order: '3'
        });

        // Selection group
        registry.registerMenuAction(WorkflowEditorContextMenu.SELECTION, {
            commandId: WorkflowEditorCommands.DELETE_NODES.id,
            order: '1'
        });
        registry.registerMenuAction(WorkflowEditorContextMenu.SELECTION, {
            commandId: WorkflowEditorCommands.SELECT_ALL_NODES.id,
            order: '2'
        });

        // Execution group
        registry.registerMenuAction(WorkflowEditorContextMenu.EXECUTION, {
            commandId: WorkflowEditorCommands.RUN_WORKFLOW.id,
            order: '1'
        });

        // Configuration group
        registry.registerMenuAction(WorkflowEditorContextMenu.CONFIGURATION, {
            commandId: WorkflowEditorCommands.SETTINGS.id,
            order: '1'
        });
    }
}
