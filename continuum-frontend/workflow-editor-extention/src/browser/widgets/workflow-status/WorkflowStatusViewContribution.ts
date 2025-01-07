import { AbstractViewContribution } from "@theia/core/lib/browser"
import { WorkflowStatusWidget } from "./WorkflowStatusWidget";
import { injectable } from "@theia/core/shared/inversify";
import { CommandRegistry } from "@theia/core";

@injectable()
export default class WorkflowStatusViewContribution extends AbstractViewContribution<WorkflowStatusWidget> {

    constructor() {
        super({
            widgetId: WorkflowStatusWidget.ID,
            widgetName: WorkflowStatusWidget.LABEL,
            defaultWidgetOptions: { area: "right" },
            toggleCommandId: WorkflowStatusWidget.COMMAND.id,
            toggleKeybinding: `shift+cmd+s`
        });
    }

    registerCommands(commands: CommandRegistry): void {
        commands.registerCommand(WorkflowStatusWidget.COMMAND, {
            execute: () => super.toggleView()
        });
    }

}