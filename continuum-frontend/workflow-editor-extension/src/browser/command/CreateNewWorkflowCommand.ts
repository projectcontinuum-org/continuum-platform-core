import { inject, injectable } from "@theia/core/shared/inversify";
import IContinuumCommand from "./IContinuumCommand";
import { CommonCommands, OpenerService, open } from "@theia/core/lib/browser";
import { UntitledResourceResolver } from "@theia/core";
import { UserWorkingDirectoryProvider } from "@theia/core/lib/browser/user-working-directory-provider";

@injectable()
export default class CreateNewWorkflowCommand implements IContinuumCommand {
    id = "continuum.create-new-workflow:command";
    label = "New Workflow...";
    iconClass = "continuum continuum-file workflow-file-icon fa fa-solid fa-diagram-project";
    category = CommonCommands.FILE_CATEGORY;

    constructor(
        @inject(UntitledResourceResolver)
        protected readonly untitledResourceResolver: UntitledResourceResolver,
        @inject(UserWorkingDirectoryProvider)
        protected readonly workingDirProvider: UserWorkingDirectoryProvider,
        @inject(OpenerService) 
        protected readonly openerService: OpenerService
    ) {

    }

    async execute(...args: any[]) {
        const untitledUri = this.untitledResourceResolver.createUntitledURI('.cwf', await this.workingDirProvider.getUserWorkingDir());
        this.untitledResourceResolver.resolve(untitledUri);
        open(this.openerService, untitledUri);
        console.log("CreateNewWorkflowCommand executed");
    }
}