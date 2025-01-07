import { IBaseNodeData } from "../types/IBaseNode.js"
import AbstractBaseNodeModel from "../nodes/AbstractBaseNodeModel.js"

export default interface INodeModelsMap {
    [key: string]: {new(node: IBaseNodeData | undefined): AbstractBaseNodeModel}
}