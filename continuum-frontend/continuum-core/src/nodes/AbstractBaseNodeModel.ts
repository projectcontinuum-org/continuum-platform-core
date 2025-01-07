import { IBaseNodeData, INodeInputs, INodeOutputs } from "../types/IBaseNode.js";
import IBaseNodeModel from "../types/IBaseNode.js";
import { MimeTypes } from "../model/MimeTypes.js";
import INodeExecutionContext from "../types/INodeExecutionContext.js";

export default abstract class AbstractBaseNodeModel implements IBaseNodeModel {

    nodeInfo: IBaseNodeData = {
        id: "",
        description: "An abstract node",
        title: "Abstract node",
        subTitle: "Abstract node",
        inputs: {},
        outputs: {},
        nodeModel: AbstractBaseNodeModel.name
    };

    constructor(nodeInfo: IBaseNodeData | undefined){
        if(nodeInfo)
            this.nodeInfo = nodeInfo;
    }

    run(nodeInputs: INodeInputs, nodeExecutionContext: INodeExecutionContext, sendProgress: ()=>Promise<boolean | undefined>): Promise<INodeOutputs> {
        return new Promise(async (resolve, reject)=>{
            try{
                console.log("Executing node... ", this.nodeInfo.id);
                this.nodeInfo.status = "BUSY";
                await sendProgress();
                let nodeOutputs = await this.execute(nodeInputs, nodeExecutionContext);
                this.nodeInfo.status = "SUCCESS";
                console.log("Executing finished! ", this.nodeInfo.id);
                await sendProgress();
                resolve(nodeOutputs);
            } catch(ex) {
                this.nodeInfo.status = "FAILED";
                let errorOutput = await nodeExecutionContext.createPortOutput("error", {contentType: MimeTypes[".json"], name: "error"});
                errorOutput.status = "FAILED";
                let error = ex as Error;
                nodeExecutionContext.writeRow("error", {
                    name: error.name,
                    message: error.message,
                    stack: error.stack
                });
                nodeExecutionContext.finish()
                let nodeOutouts = {
                    "error": errorOutput
                }
                console.log(JSON.stringify(ex));
                await sendProgress();
                reject(nodeOutouts);
            }
        });
    }

    abstract execute(nodeInputs: INodeInputs, nodeEdecutionContext: INodeExecutionContext): Promise<INodeOutputs>;
}