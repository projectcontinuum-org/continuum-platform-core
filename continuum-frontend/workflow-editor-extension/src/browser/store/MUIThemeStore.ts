import { Theme, createTheme } from "@mui/material";
import { create } from "zustand";

export type SetFunction<T> = (arg: T)=>T

export interface MUIThemeState {
    theme: Theme;
    setTheme: (setFunction: SetFunction<Theme>) => void;
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
    setTheme: (setFunction: SetFunction<Theme>) => {
        set((state)=>({theme: setFunction(state.theme)}))
    }
}));