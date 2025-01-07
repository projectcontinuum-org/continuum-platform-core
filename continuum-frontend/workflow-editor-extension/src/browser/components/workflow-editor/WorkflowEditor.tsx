import "reactflow/dist/base.css";
import './WorkflowEditor.css';

import React from 'react';
import { useRef, useCallback } from 'react';
import ReactFlow, { Connection, Controls, EdgeChange, Node, NodeChange, Panel, addEdge, applyEdgeChanges, applyNodeChanges, getOutgoers } from 'reactflow';
import BaseNode from '../node/BaseNode';
import BaseEdge from '../node/BaseEdge';
import { Box, Button, IconButton } from '@mui/material';
import { IBaseNodeData, IWorkflow } from "@continuum/core";
import NodeDialog, { NodeDialogProps } from "../node-dialog/NodeDialog";
import WorkflowService from "../../service/WorkflowService";
import LockClockIcon from '@mui/icons-material/LockClock';

const workflowService = new WorkflowService();

const nodeTypes = {
    BaseNode,
};
const edgeTypes = {
    BaseEdge
};
const defaultEdgeOptions = {
    type: "BaseEdge"
};

export interface WorkflowEditorProps {
    workflow: IWorkflow,
    onChange: (workflow: IWorkflow)=>void
}

function WorkflowEditor({ workflow, onChange }: WorkflowEditorProps)  {
    const ref = useRef<HTMLDivElement | null>(null);
    const [flowEdges, setFlowEdges] = React.useState(workflow.edges);
    const [flowNodes, setFlowNodes] = React.useState(workflow.nodes);
    const [isActive, setIsActive] = React.useState(workflow.active);
    const [nodeDialogProps, setNodeDialogProps] = React.useState<NodeDialogProps | null>(null);
    const [selectedNode, setSelectedNode] = React.useState<Node<IBaseNodeData> | null>(null);

    React.useEffect(()=>{
        if(workflow) {
            onChange({
                ...workflow,
                nodes: flowNodes,
                edges: flowEdges,
                active: isActive
            });
        }
    },[flowNodes, flowEdges, isActive])

    const onNodesChange = useCallback((changes: NodeChange[]) => {
        // console.log("onNodesChange");
        setFlowNodes((nodes) => applyNodeChanges(changes, nodes));
    },[setFlowNodes]);

    const onEdgesChange = useCallback((changes: EdgeChange[]) => {
        // console.log("onEdgesChange");
        setFlowEdges((edges) => applyEdgeChanges(changes, edges));
    },[setFlowEdges]);

    const onNodeConnect = useCallback((connection: Connection) => {
        // console.log("onNodeConnect");
        setFlowEdges((edges) => addEdge(connection, edges));
    },[setFlowEdges]);

    const hasCycle = React.useCallback((connection: Connection, node: Node, visited = new Set()): boolean => {
        if (visited.has(node.id)) return false;

        visited.add(node.id);

        for (const outgoer of getOutgoers(node, flowNodes, flowEdges)) {
            if (outgoer.id === connection.source) return true;
            if (hasCycle(connection, outgoer, visited)) return true;
        }
        return false;
    }, [flowEdges, flowNodes]);

    const isValidConnection = React.useCallback((connection: Connection): boolean => {
        if (connection.source === connection.target) return false;
        if (flowEdges.filter((edge) => edge.target === connection.target && edge.targetHandle === connection.targetHandle).length > 0)
            return false;
        const targetNode: Node = flowNodes.find(
            (node) => node.id === connection.target
        )!;
        if (hasCycle(connection, targetNode)) return false;
        return true;
    }, [flowEdges, flowNodes, hasCycle]);

    const onActivate = React.useCallback(async () => {
        console.log({ flowNodes, flowEdges });
        try {
            await workflowService.activateWorkflow({
                id: workflow.id,
                name: workflow.name,
                active: true,
                edges: flowEdges,
                nodes: flowNodes,
            });
            setIsActive(true);
        } catch (error) {
            console.error(error);
        }
    }, [flowEdges, flowNodes, setIsActive]);

    const onNodeDialogClose = React.useCallback(()=>{
        setNodeDialogProps(null);
    }, [setNodeDialogProps]);

    const onNodeDialogSaved = React.useCallback((properties: any)=>{
        setNodeDialogProps(null);
        console.log("selectedNode ", selectedNode);
        if(selectedNode) {
            console.log("Saving ", properties);
            selectedNode.data.properties = properties;
            setFlowNodes(flowNodes);
        }
    }, [setNodeDialogProps, selectedNode, flowNodes, setFlowNodes]);

    const onNodeDoubleClick = React.useCallback((event: React.MouseEvent, clickedNode: Node<IBaseNodeData>) => {
        console.log("onNodeDoubleClick", event, clickedNode);
        setNodeDialogProps({
            open: true,
            onClose: onNodeDialogClose,
            onSave: onNodeDialogSaved,
            initialData: clickedNode.data.properties || {} ,
            dataSchema: clickedNode.data.propertiesSchema || {},
            uiSchema: clickedNode.data.propertiesUISchema || {}
        });
        setSelectedNode(clickedNode);
    }, [setNodeDialogProps, onNodeDialogSaved, onNodeDialogClose, setSelectedNode]);

    const onDeactivate = React.useCallback(async () => {
        console.log({ flowNodes, flowEdges });
        try {
            await workflowService.activateWorkflow({
                id: workflow.id,
                name: workflow.name,
                active: false,
                edges: flowEdges,
                nodes: flowNodes,
            });
            setIsActive(false);
        } catch (error) {
            console.error(error);
        }
    }, [flowEdges, flowNodes, setIsActive]);


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
                nodes={flowNodes}
                edges={flowEdges}
                onNodesChange={!isActive ? onNodesChange : undefined}
                onNodeDoubleClick={onNodeDoubleClick}
                onEdgesChange={!isActive ? onEdgesChange : undefined}
                onConnect={!isActive ? onNodeConnect: undefined}
                isValidConnection={isValidConnection}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                defaultEdgeOptions={defaultEdgeOptions}
                className="workflow-editor"
                fitView>
                <Controls />
                <Panel position="bottom-center">
                    {!isActive ? <Button variant="contained" onClick={onActivate}>Activate</Button> :
                     <Button variant="contained" onClick={onDeactivate}>Deactivate</Button>}
                </Panel>
                {isActive && <Panel position="top-right">
                    <IconButton aria-label="delete">
                        <LockClockIcon />
                    </IconButton>
                </Panel>}
            </ReactFlow>
            {nodeDialogProps && <NodeDialog
                {...nodeDialogProps!!}
                onSave={onNodeDialogSaved}
                onClose={onNodeDialogClose}
                readOnly={isActive}/>}
        </Box>
    );
}

export default WorkflowEditor;
