import { WidgetOpenerOptions, WidgetOpenHandler } from "@theia/core/lib/browser";
import WorkflowViewerWidget from "../widgets/workflow-viewer/WorkflowViewerWidget";
import { MaybePromise, URI } from "@theia/core";

export default class WorkflowViewerOpenHandler extends WidgetOpenHandler<WorkflowViewerWidget> {
    id: string = WorkflowViewerWidget.ID;

    label?: string | undefined = "Continuum Workflow Viewer";

    iconClass?: string | undefined = "fa fa-cube";

    canHandle(uri: URI, options?: WidgetOpenerOptions | undefined): MaybePromise<number> {
        console.log("canHandle", uri.scheme, options)
        return uri.scheme == "continuum-execution-watch" ? 1000 : -1;
    }

    protected createWidgetOptions(uri: URI, options?: WidgetOpenerOptions | undefined) {
        return {
            uri,
            ...options
        }
    }
}