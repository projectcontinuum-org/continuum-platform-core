import { CommandContribution, CommandRegistry } from "@theia/core";
import CreateNewWorkflowCommand from "../command/CreateNewWorkflowCommand";
import { inject, injectable } from "@theia/core/shared/inversify";

@injectable()
export default class ContinuumCommandcontribution implements CommandContribution {

    constructor(
        @inject(CreateNewWorkflowCommand)
        protected readonly createNewWorkflowCommand: CreateNewWorkflowCommand
    ){}

    registerCommands(commandRegistry: CommandRegistry): void {
        commandRegistry.registerCommand(this.createNewWorkflowCommand, {
            execute: (...args: any[]) => this.createNewWorkflowCommand.execute(args)
        });
    }
}