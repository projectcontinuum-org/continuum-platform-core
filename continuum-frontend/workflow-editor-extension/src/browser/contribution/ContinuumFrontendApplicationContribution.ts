import { FrontendApplicationContribution } from "@theia/core/lib/browser";
import { MaybePromise } from "@theia/core";
import { inject, injectable, postConstruct } from "@theia/core/shared/inversify";
import ContinuumThemeService from "../theme/ContinuumThemeService";
import { MonacoLanguageRegistration } from "../language/MonacoLanguageRegistration";

@injectable()
export class ContinuumFrontendApplicationContribution implements FrontendApplicationContribution {

    constructor(
        @inject(ContinuumThemeService)
        protected readonly continuumThemeService: ContinuumThemeService,
        @inject(MonacoLanguageRegistration)
        protected readonly languageRegistration: MonacoLanguageRegistration
    ) {}

    @postConstruct()
    protected init(): void {
        // Register languages as early as possible
        this.languageRegistration.initialize();
    }

    configure(): MaybePromise<void> {
        this.continuumThemeService.registerAllThemes();
    }
}