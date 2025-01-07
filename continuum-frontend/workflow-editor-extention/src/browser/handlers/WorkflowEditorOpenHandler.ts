import { WidgetOpenHandler, WidgetOpenerOptions } from "@theia/core/lib/browser";
import { injectable } from "@theia/core/shared/inversify";
import WorkflowEditorWidget from "../widgets/workflow-editor/WorkflowEditorWidget";
import { URI, MaybePromise } from "@theia/core";

@injectable()
export default class WorkflowEditorOpenHandler extends WidgetOpenHandler<WorkflowEditorWidget> {
    id: string = WorkflowEditorWidget.ID;

    label?: string | undefined = "Continuum Workflow Editor";

    iconClass?: string | undefined = "fa fa-cube";

    canHandle(uri: URI, options?: WidgetOpenerOptions | undefined): MaybePromise<number> {
        console.log("canHandle", uri, options)
        return uri.path.ext.endsWith('.cwf') ? 1000 : -1;
    }

    protected createWidgetOptions(uri: URI, options?: WidgetOpenerOptions | undefined) {
        return {
            uri: uri.toString(),
            ...options
        }
    }

}