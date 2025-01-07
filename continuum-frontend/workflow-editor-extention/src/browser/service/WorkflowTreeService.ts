import { ITreeItem, IExecution } from "@continuum/core";

export default class WorkflowTreeService {

    private readonly apiBaseUrl: string = "http://localhost:8080/api/v1/workflow";

    async getWorflows(baseDir: string, query: string): Promise<ITreeItem<IExecution>[]> {
        try {
            const encodedQuery = encodeURIComponent(query);
            let response = await fetch(`${this.apiBaseUrl}/tree?baseDir=${baseDir}${encodedQuery.length > 0  ? `&query=${encodedQuery}` : ""}`);
            return response.json();
        } catch (error) {
            console.error(error);
            return [];
        }
    }

    async getWorkflowcount(baseDir: string): Promise<{count: number}> {
        try {
            let response = await fetch(`${this.apiBaseUrl}/count?baseDir=${baseDir}`);
            return response.json();
        } catch (error) {
            console.error(error);
            return {count: 0};
        }
    }
}