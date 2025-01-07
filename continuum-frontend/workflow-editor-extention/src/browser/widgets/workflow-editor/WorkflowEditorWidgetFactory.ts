import { MaybePromise, MessageService, URI } from "@theia/core";
import { RemoteFileSystemProvider } from "@theia/filesystem/lib/common/remote-file-system-provider"
import WorkflowEditorWidget, { WorkflowEditorWidgetOptions } from "./WorkflowEditorWidget";
import { LabelProvider, Widget, WidgetFactory } from "@theia/core/lib/browser";
import { inject, injectable } from "@theia/core/shared/inversify";
import { ColorRegistry } from "@theia/core/lib/browser/color-registry";
import { FileDialogService } from "@theia/filesystem/lib/browser";
import ContinuumNodeDialog from "../../dialog/node-dialog/ContinuumNodeDialog";

@injectable()
export default class WorkflowEditorWidgetFactory implements WidgetFactory {

    static createID(uri: URI, counter?: number): string {
        return WorkflowEditorWidget.ID
            + `:${uri.toString()}`
            + (counter !== undefined ? `:${counter}` : '');
    }

    id: string = WorkflowEditorWidget.ID;

    opennedWidgets: {[id:string]: WorkflowEditorWidget | undefined} = {};

    activeWidget?: WorkflowEditorWidget

    constructor(
        @inject(LabelProvider)
        protected readonly labelProvider: LabelProvider,
        @inject(RemoteFileSystemProvider)
        protected readonly remoteFileSystemProvider: RemoteFileSystemProvider,
        @inject(ColorRegistry)
        protected readonly colorRegistry: ColorRegistry,
        @inject(FileDialogService)
        protected readonly fileDialogService: FileDialogService,
        @inject(MessageService)
        protected readonly messageService: MessageService,
        @inject(ContinuumNodeDialog)
        protected readonly continuumNodeDialog: ContinuumNodeDialog
    ) {}

    createWidget(options: WorkflowEditorWidgetOptions): MaybePromise<Widget> {
        return new Promise(async (resolve, reject)=>{
            if(this.opennedWidgets[options?.uri!!] == undefined) {
                console.log("create widgett", options);
            
                const widget = this.contructEditor(options);
                widget.onDidDispose(()=>{
                    console.log("editor disposed!!");
                    this.opennedWidgets[options?.uri!!] = undefined;
                });
                widget.onDidChangeVisibility((isVisible)=>{
                    if(isVisible) {
                        this.activeWidget = this.opennedWidgets[options?.uri!!];
                        console.log("activating !!", options);
                    } else {
                        this.activeWidget = undefined;
                    }
                });
                this.opennedWidgets[options?.uri!!] = widget;
                resolve(widget);
            } else {
                resolve(this.opennedWidgets[options?.uri!!]!!);
            }
            console.log("activating ", options);
        });
    }

    contructEditor(options: WorkflowEditorWidgetOptions): WorkflowEditorWidget {
        const editor = new WorkflowEditorWidget(options, 
            this.remoteFileSystemProvider, 
            this.colorRegistry, 
            this.fileDialogService,
            this.messageService,
            this.continuumNodeDialog);
        editor.initLabel();
        return editor;
    }

}