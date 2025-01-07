import { INodeOutputs } from "../types/IBaseNode";
import AbstractBaseNodeModel from "../nodes/AbstractBaseNodeModel.js";
import ITriggerNodeModel from "../types/ITriggerNode";
import INodeExecutionContext from "../types/INodeExecutionContext.js";

export default abstract class AbstractTriggerNodeModel extends AbstractBaseNodeModel implements ITriggerNodeModel {

    abstract activate(dispatch: Function, nodeExecutionContext: INodeExecutionContext, manualMode: boolean): Promise<INodeOutputs> ;

    abstract deActivate(): void;

}