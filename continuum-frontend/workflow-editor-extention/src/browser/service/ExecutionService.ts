import { IJobUpdate } from "@continuum/core";
import mqtt from 'mqtt/dist/mqtt.esm'

export interface IExecutionMessage {
    jobId: string; 
    data: IJobUpdate;
}

export interface WatchEventHandler {
    onmessage?: (event: IExecutionMessage) => void;
    onclose?: () => void;
    close: () => void;
}

export default class ExecutionService {
    
    private readonly apiBaseUrl: string = 'http://localhost:8080/api/v1/execution';
    private readonly mqttBaseUrl: string = 'ws://localhost:31884/mqtt';
    private readonly MQTT_TOPIC_PREFIX = "continuum/workflow/execution"

    async getActiveExecutionIds(filterRegex: RegExp | string): Promise<string[]> {
        const response = await fetch(`${this.apiBaseUrl}/active?filterRegex=${filterRegex}`);
        return response.json();
    }

    watch(executionId: string): WatchEventHandler {
        // Create a proxy for Websocket
        console.log("mqtt=", mqtt);
        let client = mqtt.connect(this.mqttBaseUrl);
        client.subscribe(`${this.MQTT_TOPIC_PREFIX}/${executionId}/update`);
        let handler: WatchEventHandler = {
            close: () => {
                client.end();
            }
        }
        client.on('message', (topic, message) => {
            if (topic.startsWith(`${this.MQTT_TOPIC_PREFIX}/${executionId}/update`)) {
                handler.onmessage && handler.onmessage(JSON.parse(message.toString()) as IExecutionMessage);
            }
        });
        client.on('close', () => {
            handler.onclose && handler.onclose();
        });
        return handler;
    }
}