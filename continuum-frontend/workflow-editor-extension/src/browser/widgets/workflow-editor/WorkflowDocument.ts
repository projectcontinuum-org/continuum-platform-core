import { IWorkflow } from "@continuum/core";
import { DisposableCollection, Emitter, Event, MaybePromise, URI } from "@theia/core";
import { SaveOptions, Saveable } from "@theia/core/lib/browser";
import { RemoteFileSystemProvider } from "@theia/filesystem/lib/common/remote-file-system-provider";

export default class WorkflowDocument implements Saveable {
    protected originalWorkflow?: IWorkflow;
    protected unsavedWorkflow?: IWorkflow;
    protected readonly toDispose = new DisposableCollection();
    protected readonly onChangeEventEmitter = new Emitter<void>();
    public readonly onContentChanged = new Emitter<void>().event;
    _dirty: boolean = false;
    onDirtyChanged: Event<void> = this.onChangeEventEmitter.event;
    autoSave: "off" | "afterDelay" | "onFocusChange" | "onWindowChange" = 'off';

    constructor(
        protected readonly remoteFileSystemProvider: RemoteFileSystemProvider,
        protected readonly resourceUri: URI
    ) {}

    setOriginalWorkflow(wf?: IWorkflow) {
        this.originalWorkflow = wf;
    }

    setUnsavedWorkflow(wf?: IWorkflow) {
        if(JSON.stringify(this.originalWorkflow) != JSON.stringify(wf)) {
            this.unsavedWorkflow = wf;
            this.setDirty(true);
        }
    }

    get dirty(): boolean {
        return this._dirty;
    }

    setDirty(value: boolean) {
        if(this._dirty != value) {
            this._dirty = value;
            this.onChangeEventEmitter.fire();
        }
    }

    save(options?: SaveOptions | undefined): MaybePromise<void> {
        return new Promise((resolve)=>{
            if(this.unsavedWorkflow != undefined) {
                console.log("Saving ", options, this.unsavedWorkflow);
                this.originalWorkflow = this.unsavedWorkflow;
                const encoder = new TextEncoder();
                this.remoteFileSystemProvider.writeFile(this.resourceUri, encoder.encode(JSON.stringify(this.originalWorkflow, null, 4)), {create: true, overwrite: true});
                this.setDirty(false);
            }
            resolve();
        });
    }
    
    async revert(options?: Saveable.RevertOptions | undefined): Promise<void> {
        console.log("revert", options);
        return
    }
}