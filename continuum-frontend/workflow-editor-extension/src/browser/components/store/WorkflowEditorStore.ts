import { create } from "zustand"
import { Node, Edge } from "reactflow"

export type SetFunction<T> = (nodes: T)=>T

export interface WorkflowEditorState {
    flowNodes: Node[];
    flwoEdges: Edge[];
    setFlowNodes: (setFunction : SetFunction<Node[]>) => void;
    setFlowEdges: (setFunction : SetFunction<Edge[]>) => void;
}

export const useWorkflowEditorStore = create<WorkflowEditorState>()((set) => ({
    flowNodes: [],
    flwoEdges: [],
    setFlowNodes: (setFunction : SetFunction<Node[]>) => {
        set((state)=>({flowNodes: setFunction(state.flowNodes)}));
    },
    setFlowEdges: (setFunction : SetFunction<Edge[]>) => {
        set((state)=>({flwoEdges: setFunction(state.flwoEdges)}));
    },
}));