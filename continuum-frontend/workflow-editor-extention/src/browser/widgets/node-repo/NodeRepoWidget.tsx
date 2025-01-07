import React from 'react';
import { ReactWidget } from '@theia/core/lib/browser';
import { inject, injectable } from '@theia/core/shared/inversify';
import AppContextProvider from "../../components/context/AppContextProvider";
import * as MUIcon from "@mui/icons-material";
import NodeRepositoryExplorer from '../../components/node-repo-explorer/NodeRepoExplorer';
import { ThemeProvider } from '@mui/material/styles';
import { IBaseNodeData, INodeRepoTreeItem } from '@continuum/core';
import { Box, CssBaseline } from '@mui/material';
import DummyNode from '../../components/node/DummyNode';
import { DraggableData, DraggableEvent } from 'react-draggable';
import WorkflowEditorWidgetFactory from '../workflow-editor/WorkflowEditorWidgetFactory';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import {Experimental_CssVarsProvider as CssVarsProvider, experimental_extendTheme} from "@mui/material"
import { Command } from '@theia/core';

@injectable()
export default class NodeRepoWidget extends ReactWidget {
    static readonly ID = 'continuum-node-repo:widget';
    static readonly LABEL = 'Node Repository';
    static readonly COMMAND: Command = { id: 'node-repo-widget:command' };

    constructor(
        @inject(WorkflowEditorWidgetFactory)
        protected readonly workfloweditorWidgetFactory: WorkflowEditorWidgetFactory
    ) {
        super()
        this.initLabel();
    }

    async initLabel(): Promise<void> {
        this.id = NodeRepoWidget.ID;
        this.title.label = NodeRepoWidget.LABEL;
        this.title.caption = NodeRepoWidget.LABEL;
        this.title.closable = false;
        this.title.iconClass = 'continuum continuum-widget workflow-file-icon fa fa-solid fa-compass-drafting'; // example widget icon.
        this.update();
    }

    render() {
        return <NodeRepoWidgetHOC workfloweditorWidgetFactory={this.workfloweditorWidgetFactory}/>;
    }
}

function NodeRepoWidgetHOC({ workfloweditorWidgetFactory }: { workfloweditorWidgetFactory: WorkflowEditorWidgetFactory }) {
    const [theme] = useMUIThemeStore((state)=>([state.theme]));
    const [dragState, setDragState] = React.useState(false);
    const [position, setPosition] = React.useState({ top: 0, left: 0 });
    const [dragNodeMetadata, setdragNodeMetadata] = React.useState<IBaseNodeData | null>(null);
    const cssTheme = experimental_extendTheme(theme);

    const onDragStart = React.useCallback((e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => {
        node.nodeInfo && setdragNodeMetadata(node.nodeInfo)
    }, [setdragNodeMetadata]);

    const onDragStop = React.useCallback((e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => {
        console.log("Drag stop");
        setDragState(false);
        workfloweditorWidgetFactory.activeWidget?.addNewNode(e, node);
    }, [setDragState]);

    const onDragDelta = React.useCallback((e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => {
        setDragState(true);
        setPosition({
            left: (e as MouseEvent).clientX,
            top: (e as MouseEvent).clientY
        })
    }, [setDragState, setPosition]);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <CssVarsProvider theme={cssTheme}></CssVarsProvider>
            {dragState && <Box
                sx={{
                    zIndex: 520,
                    position: "fixed",
                    top: position.top - (100 / 2),
                    left: position.left - (200 / 2)
                }}>
                {dragNodeMetadata && <DummyNode {...dragNodeMetadata} />}
            </Box>}
            <AppContextProvider iconBank={{ mui: MUIcon }}>
                <NodeRepositoryExplorer 
                    onDragStart={onDragStart}
                    onDragStop={onDragStop}
                    onDragDelta={onDragDelta}/>
            </AppContextProvider>
        </ThemeProvider>
    );
}

