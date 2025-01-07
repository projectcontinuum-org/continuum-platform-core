import INodeToOutputsMap from './INodeToOutputsMap';
import IWorkflow from './IWorkflow';

export default interface IExecution {
    id: string;
    status: string;
    workflowId: string;
    nodeToOutputsMap: INodeToOutputsMap;
    workflow_snapshot: IWorkflow;
    createdAtTimestampUtc: number;
    updatesAtTimestampUtc: number;
    workflow?: IWorkflow;
}