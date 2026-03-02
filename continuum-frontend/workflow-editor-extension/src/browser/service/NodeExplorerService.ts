import { INodeExplorerTreeItem } from "@continuum/core";

export default class NodeExplorerService {

    private readonly apiBaseUrl: string = 'http://localhost:8080/api/v1/node-explorer';

    async getChildren(parentId: string = ""): Promise<INodeExplorerTreeItem[]> {
        const url = new URL(`${this.apiBaseUrl}/children`);
        if (parentId) {
            url.searchParams.append('parentId', parentId);
        }
        const response = await fetch(url.toString());
        return response.json();
    }
}
