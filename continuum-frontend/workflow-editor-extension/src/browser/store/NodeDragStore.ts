import { IBaseNodeData } from "@continuum/core";
import { create } from "zustand";

export interface NodeDragState {
    isDragging: boolean;
    draggedNodeData: IBaseNodeData | null;
    position: { x: number; y: number };
    startDrag: (nodeData: IBaseNodeData) => void;
    updatePosition: (x: number, y: number) => void;
    endDrag: () => void;
}

export const useNodeDragStore = create<NodeDragState>((set) => ({
    isDragging: false,
    draggedNodeData: null,
    position: { x: 0, y: 0 },
    startDrag: (nodeData: IBaseNodeData) => {
        set({ isDragging: true, draggedNodeData: nodeData });
    },
    updatePosition: (x: number, y: number) => {
        set({ position: { x, y } });
    },
    endDrag: () => {
        set({ isDragging: false, draggedNodeData: null });
    }
}));
