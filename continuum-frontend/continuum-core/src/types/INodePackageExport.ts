import IBaseNodeModel, { IBaseNodeData } from "../types/IBaseNode.js"

export interface INodeModelExport {
    model: { new(node: IBaseNodeData | undefined): IBaseNodeModel },
    categories: string[]
}

export default interface INodePackageExport {
    nodes: INodeModelExport[]
}