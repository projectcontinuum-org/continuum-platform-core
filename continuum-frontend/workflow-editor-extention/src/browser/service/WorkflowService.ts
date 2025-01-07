import { IWorkflow } from "@continuum/core";

export default class WorkflowService {

    private readonly apiBaseUrl: string = 'http://localhost:8080/api/v1/workflow';

    async activateWorkflow(workflow: IWorkflow) {
        const response = await fetch(this.apiBaseUrl, {
            method: 'POST',
            body: JSON.stringify(workflow),
            headers: {
                'Content-type': 'application/json; charset=UTF-8',
            }
        });
        return response;
    }

    async getActiveWorkflows(): Promise<string[]> {
        const response = await fetch(`${this.apiBaseUrl}/active`);
        return response.json();
    }
}