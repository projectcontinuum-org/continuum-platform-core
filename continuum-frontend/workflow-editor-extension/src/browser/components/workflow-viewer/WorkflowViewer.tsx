import "reactflow/dist/base.css";
import './WorkflowViewer.css';

import React, { useRef } from 'react';
import { Box, CircularProgress } from "@mui/material";
import ReactFlow, { Controls, Node, Panel } from "reactflow";
import BaseNode from '../node/BaseNode';
import BaseEdge from '../node/BaseEdge';
import NodeDialog, { NodeDialogProps } from '../node-dialog/NodeDialog';
import { IBaseNodeData, IJobUpdate, INodeOutputs, INodeToOutputsMap, IWorkflow } from '@continuum/core';
import LockClockIcon from '@mui/icons-material/LockClock';
import HourglassTopSharpIcon from '@mui/icons-material/HourglassTopSharp';
import ChecklistRtlSharpIcon from '@mui/icons-material/ChecklistRtlSharp';
import ClearSharpIcon from '@mui/icons-material/ClearSharp';
import QuestionMarkSharpIcon from '@mui/icons-material/QuestionMarkSharp';
import { hourglass } from 'ldrs'
import { useMUIThemeStore } from "../../store/MUIThemeStore";
import NodeOutputViewer from "../node-output-viewer/NodeOutputViewer";

hourglass.register();

const nodeTypes = {
    BaseNode
};
const edgeTypes = {
    BaseEdge
};
const defaultEdgeOptions = {
    type: "BaseEdge"
};
export interface WorkflowViewerProps {
    workflowSnapshot: IWorkflow;
    executionStatus?: IJobUpdate["status"];
    nodeToOutputsMap: INodeToOutputsMap
}

export default function WorkflowViewer({ workflowSnapshot, executionStatus, nodeToOutputsMap }: WorkflowViewerProps) {
    const ref = useRef<HTMLDivElement | null>(null);
    const [nodeDialogProps, setNodeDialogProps] = React.useState<NodeDialogProps | null>(null);
    const [theme] = useMUIThemeStore((state)=>([state.theme]));
    const [nodeOutputs, setNodeOutputs] = React.useState<INodeOutputs | null>(null);

    const onNodeDialogClose = React.useCallback(()=>{
        setNodeDialogProps(null);
    }, [setNodeDialogProps]);

    const onNodeOutputClose = React.useCallback(()=>{
        setNodeOutputs(null);
    }, [setNodeOutputs]);

    const onNodeDoubleClick = React.useCallback((event: React.MouseEvent, clickedNode: Node<IBaseNodeData>) => {
        console.log("onNodeDoubleClick", event, clickedNode);
        // setNodeDialogProps({
        //     open: true,
        //     onClose: onNodeDialogClose,
        //     onSave: ()=>{},
        //     initialData: clickedNode.data.properties || {} ,
        //     dataSchema: clickedNode.data.propertiesSchema || {},
        //     uiSchema: clickedNode.data.propertiesUISchema || {}
        // });
        setNodeOutputs(nodeToOutputsMap[clickedNode.id]);
    }, [setNodeDialogProps, onNodeDialogClose, setNodeOutputs]);

    return (
        <Box
            sx={{
                display: "flex",
                flexGrow: 1,
                bgcolor: "transparent",
                p: 0,
                position: "absolute",
                m: 1,
                bottom: 0,
                left: 0,
                right: 0,
                top: 0
            }}>
            <ReactFlow
                ref={ref}
                nodes={workflowSnapshot.nodes}
                edges={workflowSnapshot.edges}
                onNodeDoubleClick={onNodeDoubleClick}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                defaultEdgeOptions={defaultEdgeOptions}
                className="workflow-editor"
                fitView>
                <Controls />
                <Panel position="top-right">
                    {executionStatus ? (executionStatus == "PENDING" ? <HourglassTopSharpIcon fontSize='small' color="warning"/> : 
                    executionStatus == "RUNNING" ? <CircularProgress size={20} color="info"/> : 
                    executionStatus == "FINISHED" ? <ChecklistRtlSharpIcon fontSize='small' color="success"/> : 
                    executionStatus == "FAILED" ? <ClearSharpIcon fontSize='small' color="error"/> : 
                    executionStatus == "UPLOADING_RESULTS" ? <l-hourglass color={theme.palette.primary.main}/> :
                    <QuestionMarkSharpIcon fontSize='small' color="warning"/>): <LockClockIcon/>}
                </Panel>
            </ReactFlow>
            {nodeDialogProps && <NodeDialog
                {...nodeDialogProps!!}
                readOnly={true}/>}
            {nodeOutputs && <NodeOutputViewer
                open={true}
                onClose={onNodeOutputClose}
                nodeOutputs={nodeOutputs}/>}
        </Box>
    );

}