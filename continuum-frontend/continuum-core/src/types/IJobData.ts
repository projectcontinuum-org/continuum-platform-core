import IWorkflow from "./IWorkflow.js"
import INodeToOutputsMap from "./INodeToOutputsMap.js"

export default interface IJobData {
    executionUUID: string,
    nodeToOutputsMap: INodeToOutputsMap,
    createdAtTimestampUtc: number,
    workflow: IWorkflow
}