import { MenuContribution, MenuModelRegistry } from "@theia/core";
import { CommonMenus } from "@theia/core/lib/browser";
import { nls } from "@theia/core/lib/common/nls";
import { inject, injectable } from "@theia/core/shared/inversify";
import CreateNewWorkflowCommand from "../command/CreateNewWorkflowCommand";

@injectable()
export default class ContinuumMenuContribution implements MenuContribution {

    constructor(
        @inject(CreateNewWorkflowCommand)
        protected readonly createNewWorkflowCommand: CreateNewWorkflowCommand
    ){}

    registerMenus(menuModelRegistry: MenuModelRegistry) {
        menuModelRegistry.registerMenuAction([...CommonMenus.FILE, "1_new_workflow"], {
            commandId: this.createNewWorkflowCommand.id,
            label: nls.localizeByDefault(this.createNewWorkflowCommand.label)
        });
    }
}