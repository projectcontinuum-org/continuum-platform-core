import { Stream } from "node:stream";
import { IData, IPortProps } from "../types/IBaseNode.js";

export default interface INodeExecutionContext {
    createPortOutput(portId: string, portProps: IPortProps): Promise<IData>

    writeRow(portId: string, row: object): boolean

    finish(): void

    getJsonReadStream(nodeInputData: IData, pattern: object[] | string): Promise<[Stream, Record<string, any>]>;
}