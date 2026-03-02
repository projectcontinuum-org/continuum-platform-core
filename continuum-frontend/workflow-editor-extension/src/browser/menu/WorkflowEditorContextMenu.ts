import { MenuPath } from "@theia/core";

export const WORKFLOW_EDITOR_CONTEXT_MENU: MenuPath = ['workflow_editor_context_menu'];

export namespace WorkflowEditorContextMenu {
    export const UNDO_REDO = [...WORKFLOW_EDITOR_CONTEXT_MENU, '0_undo_redo'];
    export const CUT_COPY_PASTE = [...WORKFLOW_EDITOR_CONTEXT_MENU, '1_cut_copy_paste'];
    export const SELECTION = [...WORKFLOW_EDITOR_CONTEXT_MENU, '2_selection'];
    export const EXECUTION = [...WORKFLOW_EDITOR_CONTEXT_MENU, '3_execution'];
}
