import { MaybePromise, URI } from "@theia/core";
import { Widget, WidgetFactory } from "@theia/core/lib/browser";
import { inject, injectable } from "@theia/core/shared/inversify";
import NodeExplorerWidget from "./NodeExplorerWidget";
import WorkflowEditorWidgetFactory from "../workflow-editor/WorkflowEditorWidgetFactory";

@injectable()
export default class NodeExplorerWidgetFactory implements WidgetFactory {

    static createID(uri: URI, counter?: number): string {
        return NodeExplorerWidget.ID
            + `:${uri.toString()}`
            + (counter !== undefined ? `:${counter}` : '');
    }

    id: string = NodeExplorerWidget.ID;

    constructor(
        @inject(WorkflowEditorWidgetFactory)
        protected readonly workflowEditorWidgetFactory: WorkflowEditorWidgetFactory,
        @inject(NodeExplorerWidget)
        protected readonly nodeExplorerWidget: NodeExplorerWidget
    ) {}

    createWidget(): MaybePromise<Widget> {
        return this.nodeExplorerWidget;
    }

}
