import { ColorRegistry } from "@theia/core/lib/browser/color-registry";
import { ThemeService } from "@theia/core/lib/browser/theming"
import { inject, injectable } from "@theia/core/shared/inversify";
import { MonacoThemingService } from "@theia/monaco/lib/browser/monaco-theming-service";
import { useMUIThemeStore } from "../store/MUIThemeStore";
import { createTheme } from "@mui/material";
import { ThemeType } from "@theia/core/lib/common/theme";
import { ContinuumDarkTheme } from "./ContinuumDarkTheme";
import { ContinuumLightTheme } from "./ContinuumLightTheme";

@injectable()
export default class ContinuumThemeService {

    constructor(
        @inject(ThemeService)
        protected readonly themeService: ThemeService,
        @inject(MonacoThemingService)
        protected readonly monacoThemeService: MonacoThemingService,
        @inject(ColorRegistry)
        protected readonly colorRegistry: ColorRegistry
    ) {}

    registerAllThemes() {
        this.monacoThemeService.registerParsedTheme(ContinuumLightTheme);
        this.monacoThemeService.registerParsedTheme(ContinuumDarkTheme);
        this.updateMUITheme(this.themeService);
        this.themeService.onDidColorThemeChange(()=>{
            this.updateMUITheme(this.themeService);
        });
    }

    updateMUITheme(themeService: ThemeService) {
        useMUIThemeStore.setState((state)=>({theme: createTheme({
            ...state.theme,
            palette: {
                mode: this.toMUIMode(themeService.getCurrentTheme().type),
                background: {
                    default: this.colorRegistry.getCurrentColor("panel.background"),
                    paper: this.colorRegistry.getCurrentColor("panel.background")
                }
            }
        })}));
    }

    toMUIMode(themeType: ThemeType): "light" | "dark" {
        switch(themeType) {
            case "dark":
                return "dark";
            case "light":
                return "light";
            case "hc":
                return "dark";
            case "hcLight": 
                return "light";
        }
    }
}