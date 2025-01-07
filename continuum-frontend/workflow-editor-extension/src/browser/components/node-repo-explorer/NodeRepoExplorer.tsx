import * as React from 'react';
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Accordion, AccordionDetails, AccordionSummary, Box, CircularProgress, Typography, useTheme } from '@mui/material';
import StyledTreeItem from '../tree/StyledTreeItem';
import { TreeView } from '@mui/x-tree-view';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import FolderIcon from '@mui/icons-material/Folder';
import { DraggableCore, DraggableData, DraggableEvent } from 'react-draggable';
import INodeRepoTreeItem from "@continuum/core/dist/types/INodeRepoTreeItem"
import NodeRepoService from '../../service/NodeRepoService';
import DynamicIcon from '../utils/DynamicIcon';

const nodeRepoService = new NodeRepoService();

const { useCallback, useEffect, useRef, useState } = React;

function NodeRepoTreeItem({ onStart, onStop, onDrag, node, children }: {
    onStart?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void,
    onStop?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void,
    onDrag?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void,
    node: INodeRepoTreeItem,
    children: React.ReactNode | null
}) {
    const theme = useTheme();
    const nodeRef = useRef(null);

    const onDragStart = useCallback((e: DraggableEvent, data: DraggableData) => {
        onStart?.(e, data, node);
        // console.log("Drag start", node.name);
    }, [onStart, node]);

    const onDragStop = useCallback((e: DraggableEvent, data: DraggableData) => {
        onStop?.(e, data, node);
        // console.log("Drag stop");
        // setDragState("STOP");
    }, [onStop, node]);

    const onDragDelta = useCallback((e: DraggableEvent, data: DraggableData) => {
        onDrag?.(e, data, node);
    }, [onDrag, node]);

    return (
        Array.isArray(node.children) ? <StyledTreeItem
            draggable={true}
            nodeId={node.id}
            labelText={node.name}
            labelIcon={<FolderIcon fontSize='small' />}
            color={theme.palette.text.primary}
            colorForDarkMode={theme.palette.text.primary}
            bgColor={theme.palette.background.default}
            bgColorForDarkMode={theme.palette.background.default}>
            {children}
        </StyledTreeItem> :
            <DraggableCore nodeRef={nodeRef} key={node.id} onStart={onDragStart} onStop={onDragStop} onDrag={onDragDelta}>
                <StyledTreeItem
                    ref={nodeRef}
                    draggable={true}
                    nodeId={node.id}
                    labelText={node.name}
                    labelIcon={<DynamicIcon icon={node.nodeInfo?.icon} fontSize='small'/>}
                    color={theme.palette.text.primary}
                    colorForDarkMode={theme.palette.text.primary}
                    bgColor={theme.palette.background.default}
                    bgColorForDarkMode={theme.palette.background.default}>
                    {children}
                </StyledTreeItem>
            </DraggableCore>
    );
}

function NodeRepositoryExplorer({ onDragStart, onDragStop, onDragDelta }: {
    onDragStart?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void,
    onDragStop?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void,
    onDragDelta?: (e: DraggableEvent, data: DraggableData, node: INodeRepoTreeItem) => false | void
}) {
    const [nodeRepoTree, setNodeRepoTree] = useState<INodeRepoTreeItem[] | null>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        setTimeout(()=>{
            nodeRepoService.getNodeRepoTree().then((data) => {
                setNodeRepoTree(data);
                console.log(data);
                setIsLoading(false);
            });
        },3000);
    }, [setIsLoading]);

    const renderTree = (nodes: INodeRepoTreeItem[]) => (
        nodes.map((node) => (
            <NodeRepoTreeItem onStart={onDragStart} onStop={onDragStop} onDrag={onDragDelta} key={node.id} node={node}>
                {Array.isArray(node.children)
                    ? renderTree(node.children)
                    : null}
            </NodeRepoTreeItem>
        ))
    );

    return (
        <>
            <Accordion>
                <AccordionSummary
                    expandIcon={<ExpandMoreIcon />}
                    aria-controls="panel1-content"
                    id="panel1-header">
                    <Box sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        width: "100%"}}>
                        <Typography>Nodes Repository</Typography>
                        {isLoading && <CircularProgress size={20} color="secondary"/>}
                    </Box>
                </AccordionSummary>
                <AccordionDetails>
                    <TreeView
                        aria-label="customized"
                        defaultExpanded={["root"]}
                        defaultCollapseIcon={<KeyboardArrowDownIcon />}
                        defaultExpandIcon={<KeyboardArrowRightIcon />}
                        sx={{ overflowX: "scroll" }}>
                        {nodeRepoTree && renderTree(nodeRepoTree)}
                    </TreeView>
                </AccordionDetails>
            </Accordion>
        </>
    );
}

export default NodeRepositoryExplorer;
