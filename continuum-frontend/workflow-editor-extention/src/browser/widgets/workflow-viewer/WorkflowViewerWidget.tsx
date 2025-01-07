import React, { ReactNode, useEffect } from "react";
import { Message, ReactWidget } from "@theia/core/lib/browser";
import { URI } from "@theia/core";
import { IExecution, IJobUpdate, INodeToOutputsMap, IWorkflow } from "@continuum/core";
import { CssBaseline, Experimental_CssVarsProvider as CssVarsProvider, experimental_extendTheme, ThemeProvider } from "@mui/material";
import WorkflowViewer from "../../components/workflow-viewer/WorkflowViewer";
import { ReactFlowInstance, ReactFlowProvider, useReactFlow } from "reactflow";
import { useMUIThemeStore } from "../../store/MUIThemeStore";
import { ColorRegistry } from "@theia/core/lib/browser/color-registry";
import ExecutionService, { IExecutionMessage, WatchEventHandler } from "../../service/ExecutionService";
import { Disposable } from "@theia/core/shared/vscode-languageserver-protocol";

export const WorkflowViewerWidgetOptions = Symbol('WorkflowViewerWidgetOptions');
export interface WorkflowViewerWidgetOptions {
    uri: URI,
    execution: IExecution
}

export default class WorkflowViewerWidget extends ReactWidget {
    static readonly ID = 'continuum-workflow-viewer:widget';
    protected workflow?: IWorkflow;
    protected executionStatus?: IJobUpdate["status"];
    protected nodeToOutputsMap: INodeToOutputsMap;
    protected reactFlow: ReactFlowInstance;
    protected ws?: WatchEventHandler;
    protected executionService = new ExecutionService();
    protected mounted: boolean = false;

    constructor(
        private readonly options: WorkflowViewerWidgetOptions,
        private readonly colorRegistry: ColorRegistry
    ) {
        super();
        this.title.closable = true;
        this.workflow = this.options.execution.workflow_snapshot;
        this.executionStatus = this.options.execution.status as IJobUpdate["status"];
        this.nodeToOutputsMap = this.options.execution.nodeToOutputsMap;
    }

    async initLabel(): Promise<void> {
        this.id = `${WorkflowViewerWidget.ID}:${this.options.uri}`;
        this.title.label = new Date(this.options.execution.createdAtTimestampUtc).toLocaleString();
        this.title.caption = `${this.options.execution.workflow_snapshot?.name}`;
        this.title.iconClass = 'fa fa-eye';
        this.ws = this.executionService.watch(this.options.execution.id);
        this.mounted = true;
        this.ws.onmessage = (event: IExecutionMessage) => {
            this.workflow = event.data.workflow;
            this.executionStatus = event.data.status;
            this.nodeToOutputsMap = event.data.nodeToOutputsMap;
            if (this.mounted) {
                try {
                    this.update();
                } catch(e) {
                    // this.ws?.close();
                }
            }
        };
        this.ws.onclose = () => {
            console.log(`ws closed ${this.options.execution.id}`);
        };
        this.toDispose.push(Disposable.create(()=>{
            if (this.ws) {
                this.ws.close();
            }
            this.mounted = false;
        }));
        this.update();
    }

    protected onAfterAttach(msg: Message): void {
        console.log(`onAfterAttach WorkflowViewerWidget ${this.options.execution.id}`);
        this.initLabel();
        super.onAfterAttach(msg);
    }

    protected render(): ReactNode {
        return (
            <ReactFlowProvider>
                <WorkflowViewerWidgetHOC
                    workflow={this.workflow}
                    executionStatus={this.executionStatus}
                    nodeToOutputsMap={this.nodeToOutputsMap}
                    setReactflow={(rFlow)=>{this.reactFlow = rFlow}}
                    colorRegistry={this.colorRegistry}/>
            </ReactFlowProvider>
        );
    }
}

interface WorkflowViewerWidgetHOCProps {
    workflow?: IWorkflow;
    setReactflow: (reactflow: ReactFlowInstance)=>void;
    colorRegistry: ColorRegistry;
    executionStatus?: IJobUpdate["status"];
    nodeToOutputsMap: INodeToOutputsMap;
}

function WorkflowViewerWidgetHOC({
    workflow, 
    setReactflow,
    colorRegistry,
    executionStatus,
    nodeToOutputsMap
}: WorkflowViewerWidgetHOCProps) {

    const [theme] = useMUIThemeStore((state)=>([state.theme]))
    const reactFlow = useReactFlow();
    const cssTheme = experimental_extendTheme(theme);

    useEffect(() => {
        setReactflow(reactFlow);
        theme.palette.background.default = colorRegistry.getCurrentColor("editor.background")!!;
        return () => {};
    }, [reactFlow, setReactflow]);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <CssVarsProvider theme={cssTheme}></CssVarsProvider>
            {workflow && <WorkflowViewer
                workflowSnapshot={workflow}
                executionStatus={executionStatus}
                nodeToOutputsMap={nodeToOutputsMap}/>}
        </ThemeProvider>
    );
}