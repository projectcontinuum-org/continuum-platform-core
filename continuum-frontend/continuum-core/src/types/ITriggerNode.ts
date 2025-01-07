import INodeExecutionContext from "../types/INodeExecutionContext.js";
import { INodeOutputs } from "./IBaseNode";

export default interface ITriggerNodeModel {
    activate(dispatch: Function, nodeExecutionContext: INodeExecutionContext, manualMode: boolean): Promise<INodeOutputs>;
    deActivate(): void;
}