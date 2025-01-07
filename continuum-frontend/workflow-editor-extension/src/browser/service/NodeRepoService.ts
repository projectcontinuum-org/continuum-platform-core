import { INodeRepoTreeItem } from "@continuum/core";

export default class NodeRepoService {

    private readonly apiBaseUrl: string = 'http://localhost:8081/api/v1/node-repo';

    async getNodeRepoTree(): Promise<INodeRepoTreeItem[]> {
        const response = await fetch(this.apiBaseUrl);
        return response.json();
    }
}