import { MaybePromise } from "@theia/core";
import { Widget, WidgetFactory } from "@theia/core/lib/browser";
import { inject, injectable } from "@theia/core/shared/inversify";
import { WorkflowStatusWidget } from "./WorkflowStatusWidget";

@injectable()
export default class WorkflowStatusWidgetFactory implements WidgetFactory {

    id: string = WorkflowStatusWidget.ID;

    constructor(
        @inject(WorkflowStatusWidget)
        protected readonly workflowStatusWidget: WorkflowStatusWidget
    ){}

    createWidget(): MaybePromise<Widget> {
        return this.workflowStatusWidget
    }

}