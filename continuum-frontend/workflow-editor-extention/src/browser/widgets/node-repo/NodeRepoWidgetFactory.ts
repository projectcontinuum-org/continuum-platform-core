import { MaybePromise, URI } from "@theia/core";
import { Widget, WidgetFactory } from "@theia/core/lib/browser";
import { inject, injectable } from "@theia/core/shared/inversify";
import NodeRepoWidget from "./NodeRepoWidget";
import WorkflowEditorWidgetFactory from "../workflow-editor/WorkflowEditorWidgetFactory";

@injectable()
export default class NodeRepoWidgetFactory implements WidgetFactory {

    static createID(uri: URI, counter?: number): string {
        return NodeRepoWidget.ID
            + `:${uri.toString()}`
            + (counter !== undefined ? `:${counter}` : '');
    }

    id: string = NodeRepoWidget.ID;

    constructor(
        @inject(WorkflowEditorWidgetFactory)
        protected readonly workfloweditorWidgetFactory: WorkflowEditorWidgetFactory,
        @inject(NodeRepoWidget)
        protected readonly nodeRepoWidget: NodeRepoWidget
    ) {}

    createWidget(): MaybePromise<Widget> {
        return this.nodeRepoWidget;
    }

}