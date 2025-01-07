import { INodeOutputs } from "./IBaseNode.js"

export default interface INodeToOutputsMap {
    [nodeId: string]: INodeOutputs
}