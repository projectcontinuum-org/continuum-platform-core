import { Edge, Node } from "reactflow";

export default interface IWorkflow {
    id: string;
    name: string;
    active: boolean;
    nodes: Node[];
    edges: Edge[];
    settings?: object;
};