import IWorkflow from "../types/IWorkflow";
import { Edge, Node } from "reactflow";

export default class Workflow implements IWorkflow{
    id: string;
    name: string;
    active: boolean;
    nodes: Node[];
    edges: Edge[];
    settings?: object | undefined;

    constructor(obj: any) {
        this.id = obj.id;
        this.name = obj.name;
        this.active = obj.active;
        this.nodes = obj.nodes;
        this.edges = obj.edges;
        this.settings = obj.settings;
    }
}