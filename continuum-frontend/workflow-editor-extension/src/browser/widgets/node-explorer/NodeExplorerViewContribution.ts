import { AbstractViewContribution } from "@theia/core/lib/browser";
import NodeExplorerWidget from "./NodeExplorerWidget";
import { CommandRegistry } from "@theia/core";
import { inject, injectable } from "@theia/core/shared/inversify";
import { MonacoThemingService } from "@theia/monaco/lib/browser/monaco-theming-service";
import ContinuumThemeService from "../../theme/ContinuumThemeService";

@injectable()
export class NodeExplorerViewContribution extends AbstractViewContribution<NodeExplorerWidget> {

    constructor(
        @inject(MonacoThemingService)
        protected readonly monacoThemeService: MonacoThemingService,
        @inject(ContinuumThemeService)
        protected readonly continuumThemeService: ContinuumThemeService
    ) {
        super({
            widgetId: NodeExplorerWidget.ID,
            widgetName: NodeExplorerWidget.LABEL,
            defaultWidgetOptions: { area: "left" },
            toggleCommandId: NodeExplorerWidget.COMMAND.id,
            toggleKeybinding: `shift+cmd+n`
        });
    }

    registerCommands(commands: CommandRegistry): void {
        commands.registerCommand(NodeExplorerWidget.COMMAND, {
            execute: () => super.toggleView()
        });
    }

}
