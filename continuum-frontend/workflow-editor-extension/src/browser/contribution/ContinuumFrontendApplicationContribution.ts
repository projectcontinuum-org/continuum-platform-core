import { FrontendApplication, FrontendApplicationContribution } from "@theia/core/lib/browser";
import { MaybePromise } from "@theia/core";
import { inject, injectable } from "@theia/core/shared/inversify";
import ContinuumThemeService from "../theme/ContinuumThemeService";

@injectable()
export class ContinuumFrontendApplicationContribution implements FrontendApplicationContribution {
    
    constructor(
        @inject(ContinuumThemeService)
        protected readonly continuumThemeService: ContinuumThemeService
    ) {}

    configure(app: FrontendApplication): MaybePromise<void> {
        this.continuumThemeService.registerAllThemes();
    }
}