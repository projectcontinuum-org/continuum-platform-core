export interface DataPage {
    rows: any[];
    metadata: {
        page: number;
        limit: number;
        total: number;
        nextPage: number;
    }
}

export default class DataService {
    private readonly apiBaseUrl: string = 'http://localhost:8080/api/v1/data';

    async getNodeData(
        filePath: string, 
        page: number, 
        limit: number): Promise<DataPage> {
        filePath = filePath.replace("{remote}/", "");
        const response = await fetch(`${this.apiBaseUrl}/?page=${page}&limit=${limit}&filePath=${filePath}`);
        return response.json();
    }
}