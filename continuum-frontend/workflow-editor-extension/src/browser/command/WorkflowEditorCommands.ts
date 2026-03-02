import { Command } from "@theia/core";

export namespace WorkflowEditorCommands {
    const WORKFLOW_EDITOR_CATEGORY = "Workflow Editor";

    export const RUN_WORKFLOW: Command = {
        id: 'workflow-editor.run',
        label: 'Run Workflow',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-play'
    };

    export const COPY_NODES: Command = {
        id: 'workflow-editor.copy',
        label: 'Copy',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-copy'
    };

    export const PASTE_NODES: Command = {
        id: 'workflow-editor.paste',
        label: 'Paste',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-paste'
    };

    export const CUT_NODES: Command = {
        id: 'workflow-editor.cut',
        label: 'Cut',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-cut'
    };

    export const DELETE_NODES: Command = {
        id: 'workflow-editor.delete',
        label: 'Delete',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-trash'
    };

    export const SELECT_ALL_NODES: Command = {
        id: 'workflow-editor.selectAll',
        label: 'Select All',
        category: WORKFLOW_EDITOR_CATEGORY
    };

    export const UNDO: Command = {
        id: 'workflow-editor.undo',
        label: 'Undo',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-undo'
    };

    export const REDO: Command = {
        id: 'workflow-editor.redo',
        label: 'Redo',
        category: WORKFLOW_EDITOR_CATEGORY,
        iconClass: 'fa fa-redo'
    };
}
