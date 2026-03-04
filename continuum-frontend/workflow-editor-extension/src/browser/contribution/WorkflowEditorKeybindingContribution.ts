import { injectable } from "@theia/core/shared/inversify";
import { KeybindingContribution, KeybindingRegistry } from "@theia/core/lib/browser";
import { WorkflowEditorCommands } from "../command/WorkflowEditorCommands";

@injectable()
export class WorkflowEditorKeybindingContribution implements KeybindingContribution {

    registerKeybindings(registry: KeybindingRegistry): void {
        registry.registerKeybinding({
            command: WorkflowEditorCommands.RUN_WORKFLOW.id,
            keybinding: 'ctrlcmd+shift+r',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.COPY_NODES.id,
            keybinding: 'ctrlcmd+c',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.PASTE_NODES.id,
            keybinding: 'ctrlcmd+v',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.CUT_NODES.id,
            keybinding: 'ctrlcmd+x',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.DELETE_NODES.id,
            keybinding: 'delete',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.DELETE_NODES.id,
            keybinding: 'backspace',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.SELECT_ALL_NODES.id,
            keybinding: 'ctrlcmd+a',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.UNDO.id,
            keybinding: 'ctrlcmd+z',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.REDO.id,
            keybinding: 'ctrlcmd+shift+z',
            when: 'workflowEditorFocus'
        });

        registry.registerKeybinding({
            command: WorkflowEditorCommands.REDO.id,
            keybinding: 'ctrlcmd+y',
            when: 'workflowEditorFocus'
        });
    }
}
