import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';

import { Message, Navigatable, ReactWidget, SaveableSource, StatefulWidget, Widget } from '@theia/core/lib/browser';
import { ReactNode } from "react";
import React, { useEffect } from "react";
import { MessageService, UNTITLED_SCHEME, URI } from "@theia/core";
import { RemoteFileSystemProvider } from '@theia/filesystem/lib/common/remote-file-system-provider';
import { INodeRepoTreeItem, IWorkflow, Workflow } from '@continuum/core';
import WorkflowEditor from '../../components/workflow-editor/WorkflowEditor';
import { ThemeProvider } from '@mui/material/styles';
import { Node, ReactFlowInstance, ReactFlowProvider, useReactFlow } from 'reactflow';
import { DraggableEvent } from 'react-draggable';
import { CssBaseline } from '@mui/material';
import {Experimental_CssVarsProvider as CssVarsProvider, experimental_extendTheme} from "@mui/material"
import { ColorRegistry } from '@theia/core/lib/browser/color-registry';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import WorkflowDocument from './WorkflowDocument';
import { FileDialogService } from '@theia/filesystem/lib/browser';
import ContinuumNodeDialog from '../../dialog/node-dialog/ContinuumNodeDialog';

export const WorkflowEditorWidgetOptions = Symbol('WorkflowEditorWidgetOptions');
export interface WorkflowEditorWidgetOptions {
    uri: string
}

export default class WorkflowEditorWidget extends ReactWidget implements Navigatable, StatefulWidget, SaveableSource {
    static readonly ID = 'continuum-workflow-editor:widget';
    protected _uri: URI;
    protected workflow?: IWorkflow;
    protected reactFlow: ReactFlowInstance;
    protected maxNodeId = 1;
    protected containerRef = React.createRef<HTMLElement>();
    protected workflowDocument: WorkflowDocument;

    constructor(
        private readonly options: Required<WorkflowEditorWidgetOptions> & Widget.IOptions,
        protected readonly remoteFileSystemProvider: RemoteFileSystemProvider,
        protected readonly colorRegistry: ColorRegistry,
        protected readonly fileDialogService: FileDialogService,
        protected readonly messageService: MessageService,
        protected readonly continuumNodeDialog: ContinuumNodeDialog
    ) {
        super(options);
        this._uri = new URI(options.uri);
        console.log("WorkflowEditorWidget Constructing ", options);
        this.workflowDocument = new WorkflowDocument(remoteFileSystemProvider, this._uri);
        this.loadFileContent();
    }

    async createHash(input: string) {
        const msgUint8 = new TextEncoder().encode(input);                                  
        const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);                
        const hashArray = Array.from(new Uint8Array(hashBuffer));                     
        const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join(''); 
        return hashHex;
    }

    async loadFileContent() {
        if(this._uri.scheme != UNTITLED_SCHEME) {
            this.remoteFileSystemProvider.readFile(this._uri).then(async (bytesArray)=>{
                if (bytesArray.length > 0) {
                    try {
                        this.workflow = new Workflow(JSON.parse(new TextDecoder().decode(bytesArray)));
                        this.workflowDocument.setOriginalWorkflow(this.workflow);
                        let pathHash = await this.createHash(this._uri.path.toString())
                        // As sometimes the file name is changed, we need to update the id and name of the workflow
                        if(pathHash != this.workflow.id || this.workflow.name != this._uri.path.toString()) {
                            let updatedWorkflow = JSON.parse(JSON.stringify(this.workflow));
                            updatedWorkflow.id = await this.createHash(this._uri.path.toString());
                            updatedWorkflow.name = this._uri.path.toString();
                            this.workflowDocument.setUnsavedWorkflow(updatedWorkflow);
                            await this.workflowDocument.save();
                            this.workflow = updatedWorkflow;
                        }
                    } catch(ex) {
                        this.messageService.error("Unable to open workflow");
                        console.error("Unable to open workflow", ex);
                    }
                } else {
                    this.messageService.info("New workflow created!");
                    console.log(`Create new workflow "${this._uri.path.name}"`);
                    this.workflow = await this.createNewWorkflow(this._uri, this._uri.path.name);
                    this.workflowDocument.setUnsavedWorkflow(this.workflow);
                }
                console.log("Received file", this.workflow);
                this.workflow!!.nodes.forEach((node: Node) => {
                    if (this.maxNodeId < parseInt(node.id)) {
                        this.maxNodeId = parseInt(node.id);
                    }
                });
                this.update();
            });
        } else {
            this.workflow = await this.createNewWorkflow(this._uri, UNTITLED_SCHEME);
            this.workflowDocument.setUnsavedWorkflow(this.workflow);
            this.update();
        }
    }

    async createNewWorkflow(uri: URI, name: string): Promise<IWorkflow> {
        return new Workflow({
            id: await this.createHash(name),
            name: name,
            active: false,
            nodes:[],
            edges:[]
        });
    }
    
    async initLabel(): Promise<void> {
        this.id = `${WorkflowEditorWidget.ID}:${this.options.uri}`;
        this.title.label = this._uri.path.base;
        this.title.caption = this._uri.path.toString();
        this.title.iconClass = 'continuum continuum-widget workflow-file-icon fa fa-solid fa-diagram-project';
        this.title.closable = true;
        console.log("file Content", this.workflow);
        this.update();
    }

    protected onActivateRequest(message: Message): void {
        super.onActivateRequest(message)
        this.node.focus();
    }

    protected onResize(msg: Widget.ResizeMessage): void {
        super.onResize(msg);
        console.log("resize", msg);
        this.update();
    }

    protected async onCloseRequest(msg: Message): Promise<void> {
        console.log("onCloseRequest");
        // await this.fileDialogService.showSaveDialog({
        //     title: CommonCommands.SAVE_AS.label!,
        //     filters: {}
        // });
        super.onCloseRequest(msg);
    }

    addNewNode(e: DraggableEvent, treeNode: INodeRepoTreeItem) {
        console.log("Adding node !", this.options, treeNode);
        const boundingRect = this.node.getBoundingClientRect();
        console.log(boundingRect.left, boundingRect.width);
        if((e as MouseEvent).clientX < boundingRect.left ||
            (e as MouseEvent).clientX > boundingRect.right ||
            (e as MouseEvent).clientY < boundingRect.top ||
            (e as MouseEvent).clientY > boundingRect.bottom) {
                console.log("Ignoring drop!!");
                return;
        }
        const position = this.reactFlow.screenToFlowPosition({
            x: (e as MouseEvent).clientX - 100,
            y: (e as MouseEvent).clientY - 50,
        });

        this.maxNodeId = this.maxNodeId + 1;

        this.reactFlow.addNodes([{
            id: this.maxNodeId.toString(),
            type: "BaseNode",
            position: position,
            data: treeNode.nodeInfo
        }]);
    }

    // Navigatable
    getResourceUri(): URI | undefined {
        return this._uri;
    }

    createMoveToUri(resourceUri: URI): URI | undefined {
        return resourceUri;
    }

    // StatefulWidget
    storeState(): object | undefined {
        console.log("storeState");
        return this._uri.scheme === UNTITLED_SCHEME ? undefined : this.workflow;
    }

    restoreState(oldState: object): void {
        console.log("restoreState", oldState);
        this.workflow = oldState as IWorkflow;
        this.update();
    }

    // SavableSource
    get saveable() {
        return this.workflowDocument;
    }

    onChange = (wf: IWorkflow) => {
        this.workflowDocument.setUnsavedWorkflow(wf);
        if(this.workflow && this.workflow.active != wf.active) {
            this.workflow.active = wf.active;
            this.workflowDocument.save();
        }
        this.workflow = wf;
    }

    protected render(): ReactNode {
        return (
            <ReactFlowProvider>
                <WorkflowEditorWidgetHOC
                    workflow={this.workflow}
                    onChange={this.onChange}
                    setReactflow={(rFlow)=>{this.reactFlow = rFlow}}
                    colorRegistry={this.colorRegistry}/>
            </ReactFlowProvider>
        );
    }
}

interface WorkflowEditorWidgetHOCProps {
    workflow?: IWorkflow;
    setReactflow: (reactflow: ReactFlowInstance)=>void;
    colorRegistry: ColorRegistry;
    onChange: (workflow: IWorkflow)=>void;
}

function WorkflowEditorWidgetHOC({
        workflow, 
        setReactflow,
        colorRegistry,
        onChange
    }: WorkflowEditorWidgetHOCProps
) {
    const [theme] = useMUIThemeStore((state)=>([state.theme]))
    const reactFlow = useReactFlow();
    const cssTheme = experimental_extendTheme(theme);

    useEffect(() => {
        setReactflow(reactFlow);
        theme.palette.background.default = colorRegistry.getCurrentColor("editor.background")!!;
        return () => {};
    }, [reactFlow]);

    return(
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <CssVarsProvider theme={cssTheme}></CssVarsProvider>
            {workflow && <WorkflowEditor onChange={onChange} workflow={workflow}/>}
        </ThemeProvider>
    );
}