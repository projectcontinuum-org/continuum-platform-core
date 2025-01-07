import { Command } from "@theia/core";

export default abstract class IContinuumCommand implements Command {
    abstract id: string;
    abstract label?: string | undefined;
    abstract originalLabel?: string | undefined;
    abstract iconClass?: string | undefined;
    abstract category?: string | undefined;
    abstract originalCategory?: string | undefined;
    abstract keyBinding?: string;

    abstract execute(): Promise<void>
}