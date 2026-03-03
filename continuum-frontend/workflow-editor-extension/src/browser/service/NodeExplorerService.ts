import { injectable } from "@theia/core/shared/inversify";
import { INodeExplorerTreeItem } from "@continuum/core";

@injectable()
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

    async search(query: string): Promise<INodeExplorerTreeItem[]> {
        if (!query.trim()) return [];
        const url = new URL(`${this.apiBaseUrl}/search`);
        url.searchParams.append('query', query);
        const response = await fetch(url.toString());
        if (!response.ok) {
            throw new Error(`Search failed: ${response.status} ${response.statusText}`);
        }
        return response.json();
    }
}
