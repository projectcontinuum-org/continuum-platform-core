import IWorkflow from "./IWorkflow.js"
import INodeToOutputsMap from "./INodeToOutputsMap.js"

export default interface IJobUpdate {
    executionUUID: string;
    progressPercentage?: number;
    status: "PENDING" | "RUNNING" | "FINISHED" | "FAILED" | "WARNING" | "CANCELLED" | "PAUSED" | "UPLOADING_RESULTS" | "DOWNLOADING_RESULTS";
    nodeToOutputsMap: INodeToOutputsMap;
    createdAtTimestampUtc: number;
    updatesAtTimestampUtc: number;
    workflow: IWorkflow;
}