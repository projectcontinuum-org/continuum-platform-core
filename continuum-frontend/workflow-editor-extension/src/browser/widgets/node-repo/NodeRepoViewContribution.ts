import { AbstractViewContribution } from "@theia/core/lib/browser";
import NodeRepoWidget from "./NodeRepoWidget";
import { CommandRegistry } from "@theia/core";
import { inject, injectable } from "@theia/core/shared/inversify";
import { MonacoThemingService } from "@theia/monaco/lib/browser/monaco-theming-service";
import ContinuumThemeService from "../../theme/ContinuumThemeService";

@injectable()
export class NodeRepoViewContribution extends AbstractViewContribution<NodeRepoWidget> {
    
    constructor(
        @inject(MonacoThemingService)
        protected readonly monacoThemeService: MonacoThemingService,
        @inject(ContinuumThemeService)
        protected readonly continuumThemeService: ContinuumThemeService
    ) {
        super({
            widgetId: NodeRepoWidget.ID,
            widgetName: NodeRepoWidget.LABEL,
            defaultWidgetOptions: { area: "right" },
            toggleCommandId: NodeRepoWidget.COMMAND.id,
            toggleKeybinding: `shift+cmd+r`
        });
    }

    registerCommands(commands: CommandRegistry): void {
        commands.registerCommand(NodeRepoWidget.COMMAND, {
            execute: () => super.toggleView()
        });
    }

}