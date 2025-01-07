import IWorkflow from "./IWorkflow";

export default interface IExecutionStatus {
    progressPercentage: number; 
    workflow: IWorkflow;
}