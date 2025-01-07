import { MaybePromise, MessageService, URI } from "@theia/core";
import { RemoteFileSystemProvider } from "@theia/filesystem/lib/common/remote-file-system-provider"
import { LabelProvider, Widget, WidgetFactory } from "@theia/core/lib/browser";
import { inject, injectable } from "@theia/core/shared/inversify";
import { ColorRegistry } from "@theia/core/lib/browser/color-registry";
import { FileDialogService } from "@theia/filesystem/lib/browser";
import ContinuumNodeDialog from "../../dialog/node-dialog/ContinuumNodeDialog";
import WorkflowViewerWidget, { WorkflowViewerWidgetOptions } from "./WorkflowViewerWidget";

@injectable()
export default class WorkflowViewerWidgetFactory implements WidgetFactory {

    static createID(uri: URI, counter?: number): string {
        return WorkflowViewerWidget.ID
            + `:${uri.toString()}`
            + (counter !== undefined ? `:${counter}` : '');
    }

    id: string = WorkflowViewerWidget.ID;

    opennedWidgets: {[id:string]: WorkflowViewerWidget | undefined} = {};

    activeWidget?: WorkflowViewerWidget

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

    createWidget(options: WorkflowViewerWidgetOptions): MaybePromise<Widget> {
        return new Promise(async (resolve, reject)=>{
            if(this.opennedWidgets[options?.uri.toString()!!] == undefined) {
                console.log("create widgett", options);
            
                const widget = this.contructEditor(options);
                widget.onDidDispose(()=>{
                    console.log("viewer disposed!!");
                    this.opennedWidgets[options?.uri.toString()!!] = undefined;
                });
                widget.onDidChangeVisibility((isVisible)=>{
                    if(isVisible) {
                        this.activeWidget = this.opennedWidgets[options?.uri.toString()!!];
                        console.log("activating !!", options);
                    } else {
                        this.activeWidget = undefined;
                    }
                });
                this.opennedWidgets[options?.uri.toString()!!] = widget;
                resolve(widget);
            } else {
                resolve(this.opennedWidgets[options?.uri.toString()!!]!!);
            }
            console.log("activating ", options);
        });
    }

    contructEditor(options: WorkflowViewerWidgetOptions): WorkflowViewerWidget {
        const editor = new WorkflowViewerWidget(options, this.colorRegistry);
        editor.initLabel();
        return editor;
    }
}