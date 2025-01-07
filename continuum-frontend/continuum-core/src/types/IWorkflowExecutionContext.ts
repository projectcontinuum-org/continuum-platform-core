import INodeToOutputsMap from "../types/INodeToOutputsMap.js";

export default interface IWorkflowExecutionContext {
    executionUUID: string;
    nodeToOutputsMap: INodeToOutputsMap;
}