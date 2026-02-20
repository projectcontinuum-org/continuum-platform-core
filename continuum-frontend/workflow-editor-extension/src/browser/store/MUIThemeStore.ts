import { Theme, createTheme } from "@mui/material";
import { create } from "zustand";
import type * as monaco from '@theia/monaco-editor-core';

export type SetFunction<T> = (arg: T)=>T

export interface MUIThemeState {
    theme: Theme;
    monacoTheme: string;
    monaco: typeof monaco | null;
    setTheme: (setFunction: SetFunction<Theme>) => void;
    setMonacoTheme: (themeName: string) => void;
    setMonaco: (monacoInstance: typeof monaco) => void;
}

export const useMUIThemeStore = create<MUIThemeState>((set)=>({
    theme: createTheme({
        palette: {
            mode: 'dark'
        },
        components: {
            MuiAlert: {
              styleOverrides: {
                root: ({ ownerState }) => ({
                  ...(ownerState.severity === 'info' && {
                    backgroundColor: '#60a5fa',
                  }),
                }),
              },
            },
        }
    }),
    monacoTheme: 'continuum-dark',
    monaco: null,
    setTheme: (setFunction: SetFunction<Theme>) => {
        set((state)=>({theme: setFunction(state.theme)}))
    },
    setMonacoTheme: (themeName: string) => {
        set({ monacoTheme: themeName })
    },
    setMonaco: (monacoInstance: typeof monaco) => {
        set({ monaco: monacoInstance })
    }
}));