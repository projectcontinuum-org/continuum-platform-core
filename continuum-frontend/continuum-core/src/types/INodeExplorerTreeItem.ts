import { IBaseNodeData } from "..";

export type NodeExplorerItemType = "CATEGORY" | "NODE";

export default interface INodeExplorerTreeItem {
    id: string;
    name: string;
    nodeInfo?: IBaseNodeData;
    hasChildren: boolean;
    type: NodeExplorerItemType;
}
