import { ColorRegistry } from "@theia/core/lib/browser/color-registry";
import { ThemeService } from "@theia/core/lib/browser/theming"
import { inject, injectable } from "@theia/core/shared/inversify";
import { MonacoThemingService } from "@theia/monaco/lib/browser/monaco-theming-service";
import { useMUIThemeStore } from "../store/MUIThemeStore";
import { createTheme } from "@mui/material";
import { ThemeType } from "@theia/core/lib/common/theme";
import { ContinuumDarkTheme } from "./ContinuumDarkTheme";
import { ContinuumLightTheme } from "./ContinuumLightTheme";
import * as monaco from '@theia/monaco-editor-core';

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

        // Initialize Monaco instance in the store so React components can use Theia's Monaco
        useMUIThemeStore.setState({ monaco: monaco });

        this.updateMUITheme(this.themeService);
        this.themeService.onDidColorThemeChange(()=>{
            this.updateMUITheme(this.themeService);
        });
    }

    updateMUITheme(themeService: ThemeService) {
        const currentTheme = themeService.getCurrentTheme();
        const themeType = currentTheme.type;
        const monacoThemeName = currentTheme.editorTheme || this.toMonacoThemeName(themeType);

        useMUIThemeStore.setState((state)=>({
            theme: createTheme({
                ...state.theme,
                palette: {
                    mode: this.toMUIMode(themeType),
                    background: {
                        default: this.colorRegistry.getCurrentColor("panel.background"),
                        paper: this.colorRegistry.getCurrentColor("panel.background")
                    }
                }
            }),
            monacoTheme: monacoThemeName
        }));
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

    toMonacoThemeName(themeType: ThemeType): string {
        switch(themeType) {
            case "dark":
            case "hc":
                return "continuum-dark";
            case "light":
            case "hcLight":
                return "continuum-light";
        }
    }
}