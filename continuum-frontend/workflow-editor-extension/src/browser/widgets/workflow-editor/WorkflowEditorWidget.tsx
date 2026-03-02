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
import WorkflowEditor, { WorkflowEditorRef } from '../../components/workflow-editor/WorkflowEditor';
import { ThemeProvider } from '@mui/material/styles';
import { Node, Edge, ReactFlowInstance, ReactFlowProvider, useReactFlow } from 'reactflow';
import { DraggableEvent } from 'react-draggable';
import { CssBaseline } from '@mui/material';
import {Experimental_CssVarsProvider as CssVarsProvider, experimental_extendTheme} from "@mui/material"
import { ColorRegistry } from '@theia/core/lib/browser/color-registry';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import WorkflowDocument from './WorkflowDocument';
import { FileDialogService } from '@theia/filesystem/lib/browser';
import ContinuumNodeDialog from '../../dialog/node-dialog/ContinuumNodeDialog';
import { ContextMenuRenderer } from '@theia/core/lib/browser/context-menu-renderer';
import { ContextKeyService, ContextKey } from '@theia/core/lib/browser/context-key-service';
import { WORKFLOW_EDITOR_CONTEXT_MENU } from '../../menu/WorkflowEditorContextMenu';
import { WorkflowClipboardService } from '../../service/WorkflowClipboardService';

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
    protected workflowEditorFocusKey: ContextKey<boolean>;
    protected workflowEditorRef = React.createRef<WorkflowEditorRef>();
    protected pendingSelectedNodeId?: string;
    protected history: { nodes: Node[], edges: Edge[] }[] = [];
    protected historyIndex = -1;
    protected isUndoRedoAction = false;
    protected static readonly MAX_HISTORY_SIZE = 50;

    constructor(
        private readonly options: Required<WorkflowEditorWidgetOptions> & Widget.IOptions,
        protected readonly remoteFileSystemProvider: RemoteFileSystemProvider,
        protected readonly colorRegistry: ColorRegistry,
        protected readonly fileDialogService: FileDialogService,
        protected readonly messageService: MessageService,
        protected readonly continuumNodeDialog: ContinuumNodeDialog,
        protected readonly contextMenuRenderer: ContextMenuRenderer,
        protected readonly contextKeyService: ContextKeyService,
        protected readonly clipboardService: WorkflowClipboardService
    ) {
        super(options);
        this._uri = new URI(options.uri);
        console.log("WorkflowEditorWidget Constructing ", options);
        this.workflowDocument = new WorkflowDocument(remoteFileSystemProvider, this._uri);
        this.workflowEditorFocusKey = this.contextKeyService.createKey('workflowEditorFocus', false);

        // Add focus/blur event listeners to properly track focus state
        this.node.tabIndex = 0; // Make the widget focusable
        this.node.addEventListener('focusin', this.handleFocusIn);
        this.node.addEventListener('focusout', this.handleFocusOut);

        this.loadFileContent();
    }

    protected handleFocusIn = (): void => {
        this.workflowEditorFocusKey.set(true);
    }

    protected handleFocusOut = (event: FocusEvent): void => {
        // Only set to false if focus is leaving the widget entirely
        if (!this.node.contains(event.relatedTarget as HTMLElement)) {
            this.workflowEditorFocusKey.set(false);
        }
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
        this.workflowEditorFocusKey.set(true);
    }

    protected onAfterShow(msg: Message): void {
        super.onAfterShow(msg);
        this.workflowEditorFocusKey.set(true);
    }

    protected onBeforeHide(msg: Message): void {
        super.onBeforeHide(msg);
        this.workflowEditorFocusKey.set(false);
    }

    protected onAfterDetach(msg: Message): void {
        super.onAfterDetach(msg);
        this.workflowEditorFocusKey.set(false);
        // Clean up event listeners
        this.node.removeEventListener('focusin', this.handleFocusIn);
        this.node.removeEventListener('focusout', this.handleFocusOut);
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

    // Called when a significant change occurs (drag end, connect, delete)
    onHistoryChange = () => {
        if (!this.workflow || this.isUndoRedoAction) return;

        const currentState = { nodes: [...this.workflow.nodes], edges: [...this.workflow.edges] };
        const prevState = this.history[this.historyIndex];

        // Only add to history if there's an actual change
        if (!prevState ||
            JSON.stringify(prevState.nodes) !== JSON.stringify(currentState.nodes) ||
            JSON.stringify(prevState.edges) !== JSON.stringify(currentState.edges)) {

            // Remove any future history if we're not at the end
            this.history = this.history.slice(0, this.historyIndex + 1);

            // Add current state
            this.history.push(currentState);

            // Limit history size
            if (this.history.length > WorkflowEditorWidget.MAX_HISTORY_SIZE) {
                this.history.shift();
            } else {
                this.historyIndex++;
            }
        }
    }

    handleContextMenu = (event: React.MouseEvent, selectedNodeId?: string): void => {
        event.preventDefault();
        event.stopPropagation();
        // Store the selected node ID for use in hasSelectedNodes
        this.pendingSelectedNodeId = selectedNodeId;
        this.contextMenuRenderer.render({
            menuPath: WORKFLOW_EDITOR_CONTEXT_MENU,
            anchor: { x: event.clientX, y: event.clientY },
            args: [this],
            onHide: () => { this.pendingSelectedNodeId = undefined; }
        });
    }

    // Command execution methods
    runWorkflow(): void {
        this.workflowEditorRef.current?.runWorkflow();
    }

    openNodeSettings(): void {
        this.workflowEditorRef.current?.openNodeSettings();
    }

    hasSelectedNodes(): boolean {
        // Check pending selection first (for right-click selection)
        if (this.pendingSelectedNodeId !== undefined) {
            return true;
        }
        const nodes = this.reactFlow?.getNodes() ?? [];
        return nodes.some(n => n.selected);
    }

    copySelectedNodes(): void {
        if (!this.reactFlow) return;
        const selectedNodes = this.reactFlow.getNodes().filter(n => n.selected);
        const selectedNodeIds = selectedNodes.map(n => n.id);
        const selectedEdges = this.reactFlow.getEdges().filter(
            e => selectedNodeIds.includes(e.source) && selectedNodeIds.includes(e.target)
        );
        this.clipboardService.copy(selectedNodes, selectedEdges);
    }

    cutSelectedNodes(): void {
        this.copySelectedNodes();
        this.deleteSelectedNodes();
    }

    pasteNodes(): void {
        const clipboard = this.clipboardService.paste();
        if (!clipboard || !this.reactFlow) return;

        const offset = 50;
        const idMap = new Map<string, string>();

        const newNodes = clipboard.nodes.map(node => {
            const newId = (++this.maxNodeId).toString();
            idMap.set(node.id, newId);
            return {
                ...node,
                id: newId,
                position: {
                    x: node.position.x + offset,
                    y: node.position.y + offset
                },
                selected: true
            };
        });

        const newEdges = clipboard.edges.map(edge => ({
            ...edge,
            id: `${idMap.get(edge.source)}-${idMap.get(edge.target)}`,
            source: idMap.get(edge.source)!,
            target: idMap.get(edge.target)!
        }));

        // Deselect existing nodes and add new ones
        this.reactFlow.setNodes(nodes =>
            nodes.map(n => ({ ...n, selected: false })).concat(newNodes)
        );
        this.reactFlow.addEdges(newEdges);
    }

    hasClipboardContent(): boolean {
        return this.clipboardService.hasContent();
    }

    deleteSelectedNodes(): void {
        if (!this.reactFlow) return;
        const selectedNodeIds = this.reactFlow.getNodes()
            .filter(n => n.selected)
            .map(n => n.id);

        this.reactFlow.setNodes(nodes => nodes.filter(n => !selectedNodeIds.includes(n.id)));
        this.reactFlow.setEdges(edges => edges.filter(
            e => !selectedNodeIds.includes(e.source) && !selectedNodeIds.includes(e.target)
        ));
    }

    selectAllNodes(): void {
        if (!this.reactFlow) return;
        this.reactFlow.setNodes(nodes => nodes.map(n => ({ ...n, selected: true })));
    }

    // Undo/Redo methods
    canUndo(): boolean {
        return this.historyIndex > 0;
    }

    canRedo(): boolean {
        return this.historyIndex < this.history.length - 1;
    }

    undo(): void {
        if (!this.canUndo() || !this.reactFlow) return;

        this.isUndoRedoAction = true;
        this.historyIndex--;
        const prevState = this.history[this.historyIndex];

        this.reactFlow.setNodes(prevState.nodes);
        this.reactFlow.setEdges(prevState.edges);

        this.isUndoRedoAction = false;
    }

    redo(): void {
        if (!this.canRedo() || !this.reactFlow) return;

        this.isUndoRedoAction = true;
        this.historyIndex++;
        const nextState = this.history[this.historyIndex];

        this.reactFlow.setNodes(nextState.nodes);
        this.reactFlow.setEdges(nextState.edges);

        this.isUndoRedoAction = false;
    }

    protected render(): ReactNode {
        return (
            <ReactFlowProvider>
                <WorkflowEditorWidgetHOC
                    workflow={this.workflow}
                    onChange={this.onChange}
                    onContextMenu={this.handleContextMenu}
                    onHistoryChange={this.onHistoryChange}
                    setReactflow={(rFlow)=>{this.reactFlow = rFlow}}
                    colorRegistry={this.colorRegistry}
                    workflowEditorRef={this.workflowEditorRef}/>
            </ReactFlowProvider>
        );
    }
}

interface WorkflowEditorWidgetHOCProps {
    workflow?: IWorkflow;
    setReactflow: (reactflow: ReactFlowInstance)=>void;
    colorRegistry: ColorRegistry;
    onChange: (workflow: IWorkflow)=>void;
    onContextMenu: (event: React.MouseEvent, selectedNodeId?: string)=>void;
    onHistoryChange: ()=>void;
    workflowEditorRef: React.RefObject<WorkflowEditorRef>;
}

function WorkflowEditorWidgetHOC({
        workflow,
        setReactflow,
        colorRegistry,
        onChange,
        onContextMenu,
        onHistoryChange,
        workflowEditorRef
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
            {workflow && <WorkflowEditor
                ref={workflowEditorRef}
                onChange={onChange}
                workflow={workflow}
                onContextMenu={onContextMenu}
                onHistoryChange={onHistoryChange}/>}
        </ThemeProvider>
    );
}