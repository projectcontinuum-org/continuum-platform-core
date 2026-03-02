import { injectable } from "@theia/core/shared/inversify";
import { Node, Edge } from "reactflow";

export interface ClipboardContent {
    nodes: Node[];
    edges: Edge[];
}

@injectable()
export class WorkflowClipboardService {
    protected clipboard: ClipboardContent | undefined;

    copy(nodes: Node[], edges: Edge[]): void {
        // Deep clone to avoid reference issues
        this.clipboard = {
            nodes: JSON.parse(JSON.stringify(nodes)),
            edges: JSON.parse(JSON.stringify(edges))
        };
    }

    paste(): ClipboardContent | undefined {
        if (!this.clipboard) return undefined;
        // Return a deep clone so each paste gets fresh objects
        return {
            nodes: JSON.parse(JSON.stringify(this.clipboard.nodes)),
            edges: JSON.parse(JSON.stringify(this.clipboard.edges))
        };
    }

    hasContent(): boolean {
        return this.clipboard !== undefined && this.clipboard.nodes.length > 0;
    }

    clear(): void {
        this.clipboard = undefined;
    }
}
