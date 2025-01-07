import React from 'react';
import { Command, SelectionService } from "@theia/core";
import { OpenerService, ReactWidget } from "@theia/core/lib/browser";
import { inject, injectable } from '@theia/core/shared/inversify';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import {Experimental_CssVarsProvider as CssVarsProvider, experimental_extendTheme} from "@mui/material"
import AppContextProvider from '../../components/context/AppContextProvider';
import * as MUIcon from "@mui/icons-material";
import WorkflowStatusExplorer from '../../components/workflow-status-explorer/WorkflowStatusExplorer'
import { WorkspaceService } from '@theia/workspace/lib/browser/workspace-service'

@injectable()
export class WorkflowStatusWidget extends ReactWidget {
    static readonly ID = 'continuum-workflow-status:widget';
    static readonly LABEL = 'Workflow Status';
    static readonly COMMAND: Command = { id: 'workflow-status-widget:command' };

    constructor(
        @inject(WorkspaceService)
        protected readonly workspaceService: WorkspaceService,
        @inject(SelectionService)
        protected readonly selectionService: SelectionService,
        @inject(OpenerService)
        protected readonly openService: OpenerService
    ) {
        super()
        this.onDidChangeVisibility(() => {
            this.update();
        });
        this.init();
    }

    init() {
        this.id = WorkflowStatusWidget.ID;
        this.title.label = WorkflowStatusWidget.LABEL;
        this.title.caption = WorkflowStatusWidget.LABEL;
        this.title.closable = false;
        this.title.iconClass = 'continuum continuum-widget workflow-file-icon fa fa-light fa-square-poll-horizontal';
        this.update();
    }
    
    render() {
        return this.isVisible ? <WorkflowStatusWidgetHOC 
            workspaceService={this.workspaceService}
            selectionService={this.selectionService}
            openService={this.openService}/> : <></>;
    }
}

function WorkflowStatusWidgetHOC({
    workspaceService, 
    selectionService,
    openService
}: {
    workspaceService: WorkspaceService, 
    selectionService: SelectionService, 
    openService: OpenerService
}) {
    const [theme] = useMUIThemeStore((state)=>([state.theme]));
    const cssTheme = experimental_extendTheme(theme);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <CssVarsProvider theme={cssTheme}></CssVarsProvider>
            <AppContextProvider iconBank={{ mui: MUIcon }}>
                <WorkflowStatusExplorer
                    workspaceService={workspaceService}
                    selectionService={selectionService}
                    openService={openService}/>
            </AppContextProvider>
        </ThemeProvider>
    );
}