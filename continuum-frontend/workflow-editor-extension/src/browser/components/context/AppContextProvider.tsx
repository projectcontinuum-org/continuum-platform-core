import * as React from "react";

export const AppContext = React.createContext<AppContextProviderProps>({
    iconBank: {}
})

export interface AppContextProviderProps {
    iconBank: any
    children?: any
}

export default function AppContextProvider(props: AppContextProviderProps) {
    return(
        <AppContext.Provider value={props}>
            {props.children}
        </AppContext.Provider>
    );
}
