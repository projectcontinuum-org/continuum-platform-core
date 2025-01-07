import { MimeTypes } from "../model/MimeTypes";
import INodeExecutionContext from "./INodeExecutionContext.js";
import { JsonSchema } from '@jsonforms/core';

export default interface IBaseNodeModel {
    nodeInfo: IBaseNodeData;
    execute(nodeInputs: INodeInputs, context: INodeExecutionContext): Promise<INodeOutputs>;
    run(nodeInputs: INodeInputs, context: INodeExecutionContext, sendProgress: ()=>Promise<boolean | undefined>): Promise<INodeOutputs>;
}

export interface IBaseNodeData {
    id: string;
    description: string;
    title: string;
    icon?: string;
    subTitle?: string;
    inputs?: IPorts;
    outputs?: IPorts;
    properties?: {[key: string]: any};
    propertiesSchema?: JsonSchema;
    propertiesUISchema?: any;
    credentials?: {[key: string]: any};
    nodeModel: string;
    status?: "ACTIVE" | "CONFIGURED" | "BUSY" | "SUCCESS" | "FAILED" | "WARNING" | "PRE-PROCESSING" | "POST-PROCESSING";
    progressPercentage?: number;
}

export interface IPortProps {
    name:string;
    contentType: MimeTypes;
}

export interface IPorts {
    [key: string]: IPortProps
}

export interface IData {
    status: "SUCCESS" | "FAILED" | "WARNING"
    contentType: MimeTypes,
    data: any
}

export interface INodeInputs {
    [id: string]: IData
}

export interface INodeOutputs {
    [id: string]: IData
}
