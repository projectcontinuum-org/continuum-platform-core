import * as React from 'react';
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Accordion, AccordionActions, AccordionDetails, AccordionSummary, Box, Checkbox, Chip, FormControl, IconButton, InputLabel, ListItemText, MenuItem, OutlinedInput, Select, SelectChangeEvent, Typography, useTheme } from '@mui/material';
import CircularProgress from '@mui/material/CircularProgress';
import StyledTreeItem from '../tree/StyledTreeItem';
import { TreeView } from '@mui/x-tree-view';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import { ITreeItem, IExecution } from '@continuum/core';
import { WorkspaceService } from '@theia/workspace/lib/browser/workspace-service';
import AccountTreeSharpIcon from '@mui/icons-material/AccountTreeSharp';
import WorkflowTreeService from '../../service/WorkflowTreeService';
import ChecklistRtlSharpIcon from '@mui/icons-material/ChecklistRtlSharp';
import ClearSharpIcon from '@mui/icons-material/ClearSharp';
import QuestionMarkSharpIcon from '@mui/icons-material/QuestionMarkSharp';
import { SelectionService, URI } from '@theia/core';
import { OpenerService } from '@theia/core/lib/browser';
import RefreshIcon from '@mui/icons-material/Refresh';
import ClearIcon from '@mui/icons-material/Clear';
import BlockIcon from '@mui/icons-material/Block';

const { useCallback, useEffect, useRef, useState } = React;
const workflowTreeSerfice = new WorkflowTreeService();

function WorkflowStatusTreeItem({ node, children, onDoubleClick }: {
    node: ITreeItem<IExecution>,
    children: React.ReactNode | null,
    onDoubleClick?: (node: ITreeItem<IExecution>) => void;
}) {
    const theme = useTheme();
    const nodeRef = useRef(null);

    return (
        Array.isArray(node.children) ? <StyledTreeItem
            draggable={true}
            nodeId={node.id}
            labelText={node.name}
            labelIcon={<AccountTreeSharpIcon fontSize='small' />}
            color={theme.palette.text.primary}
            colorForDarkMode={theme.palette.text.primary}
            bgColor={theme.palette.background.default}
            bgColorForDarkMode={theme.palette.background.default}>
            {children}
        </StyledTreeItem> : <StyledTreeItem
            ref={nodeRef}
            nodeId={node.id}
            labelText={node.name}
            labelIcon={
                node.itemInfo?.status == "WORKFLOW_EXECUTION_STARTED" ? <CircularProgress size={20} color="info"
                    variant={Object.keys(node.itemInfo.nodeToOutputsMap).length > 0 ? "determinate" : "indeterminate"}
                    value={Object.keys(node.itemInfo.nodeToOutputsMap).length/node.itemInfo.workflow_snapshot.nodes.length * 100}/> : 
                node.itemInfo?.status == "WORKFLOW_EXECUTION_COMPLETED" ? <ChecklistRtlSharpIcon fontSize='small' color="success"/> :
                node.itemInfo?.status == "WORKFLOW_EXECUTION_FAILED" ? <ClearSharpIcon fontSize='small' color="error"/> :
                node.itemInfo?.status == "WORKFLOW_EXECUTION_CANCELED" ? <ClearIcon fontSize='small' color="error"/> :
                node.itemInfo?.status == "WORKFLOW_EXECUTION_TERMINATED" ? <BlockIcon fontSize='small' color="error"/> :
                <QuestionMarkSharpIcon fontSize='small' color="warning"/>
            }
            color={theme.palette.text.primary}
            colorForDarkMode={theme.palette.text.primary}
            bgColor={theme.palette.background.default}
            bgColorForDarkMode={theme.palette.background.default}
            onDoubleClick={() => {
                onDoubleClick && onDoubleClick(node);
            }}>
            {children}
        </StyledTreeItem>
    );
}

function WorkflowStatusExplorer({ workspaceService, selectionService, openService }: {
    workspaceService: WorkspaceService,
    selectionService: SelectionService,
    openService: OpenerService
}) {
    const theme = useTheme();

    const [finishedExecutionTree, setFinishedExecutionTree] = useState<ITreeItem<IExecution>[] | null>([]);
    const [loadedWorkflowCount, setLoadedWorkflowCount] = useState(0);
    const [workflowCount, setWorkflowCount] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [filters, setFilters] = React.useState<string[]>([]);
    const statusFilterOptions = [
        {name: "RUNNING", value: "Running", color: theme.palette.info.main},
        {name: "TIMED_OUT", value: "TimedOut", color: theme.palette.warning.main},
        {name: "COMPLETED", value: "Completed", color: theme.palette.success.main},
        {name: "FAILED", value: "Failed", color: theme.palette.error.main},
        {name: "CONTINUED_AS_NEW", value: "ContinuedAsNew", color: theme.palette.secondary.main},
        {name: "CANCELED", value: "Canceled", color: theme.palette.error.main},
        {name: "TERMINATED", value: "Terminated", color: theme.palette.error.main}
    ];

    const ITEM_HEIGHT = 48;
    const ITEM_PADDING_TOP = 8;
    const MenuProps = {
        PaperProps: {
            style: {
            maxHeight: ITEM_HEIGHT * 4.5 + ITEM_PADDING_TOP,
            width: 250,
            },
        },
    };

    useEffect(() => {
        loadAllWorkflow();
        let interval = setInterval(()=>{
            const baseDir = workspaceService.workspace?.resource?.path.toString() || "/";
            workflowTreeSerfice.getWorkflowcount(baseDir).then((data: any) => {
                setWorkflowCount(data.count);
            });
        },3000);
        return () => {
            console.log("Clearing Interval");
            clearInterval(interval);
        };
    }, [setIsLoading]);

    const handleDoubleClick = useCallback((node: ITreeItem<IExecution>) => {
        console.log("Node Double Clicked", node);
        let uri = new URI(`continuum-execution-watch://${node.itemInfo?.id}`);
        openService.getOpener(uri).then((opener) => {
            opener.open(uri, {
                execution: node.itemInfo
            });
        });
    }, []);

    const loadAllWorkflow = useCallback(() => {
        setIsLoading(true);
        const baseDir = workspaceService.workspace?.resource?.path.toString() || "/";
        let query = filters.length == 0 ? "" : filters
        .map((item) => `\`ExecutionStatus\`="${item}"`)
        .join(" OR ");
        workflowTreeSerfice.getWorflows(baseDir, query).then((data: ITreeItem<IExecution>[]) => {
            setFinishedExecutionTree(data);
            setIsLoading(false);
        });
        workflowTreeSerfice.getWorkflowcount(baseDir).then((data: any) => {
            setLoadedWorkflowCount(data.count);
        });
    }, [filters]);

    const handleFilterChange = (event: SelectChangeEvent<typeof filters>) => {
        const {
          target: { value },
        } = event;
        setFilters(
          // On autofill we get a stringified value.
          typeof value === 'string' ? value.split(',') : value,
        );
    };

    const renderTree = (nodes: ITreeItem<IExecution>[]) => (
        nodes.map((node) => (
            <WorkflowStatusTreeItem key={node.id} node={node} onDoubleClick={handleDoubleClick}>
                {Array.isArray(node.children)
                    ? renderTree(node.children)
                    : null}
            </WorkflowStatusTreeItem>
        ))
    );

    return (
        <>
            {finishedExecutionTree && <Accordion>
                <AccordionSummary
                    expandIcon={<ExpandMoreIcon />}
                    aria-controls="panel1-content"
                    id="panel1-header">
                    <Box sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        width: "100%"}}>
                        <Typography variant='subtitle2'>Workflows Status</Typography>
                        {isLoading ? <CircularProgress size={20} color="secondary"/> : 
                            <Box sx={{ display: "flex", alignItems: "center" }}>
                                <IconButton color="secondary"
                                    onClick={(event) => {
                                        loadAllWorkflow();
                                        event.stopPropagation();
                                    }}>
                                    <RefreshIcon fontSize="medium"/>
                                </IconButton>
                                <Typography variant='subtitle2' color="yellowgreen">
                                    {workflowCount-loadedWorkflowCount > 0 ? `+${workflowCount-loadedWorkflowCount}` : "" }
                                </Typography>
                            </Box>
                        }
                    </Box>
                </AccordionSummary>
                <AccordionActions>
                    <Box sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        width: "100%"}}>
                        <FormControl sx={{ mt: 1, width: "100%" }}>
                            <InputLabel id="workflow-status-multiple-chip-label">Workflow Status Filter</InputLabel>
                            <Select
                                labelId="workflow-status-multiple-chip-label"
                                id="workflow-status-multiple-chip"
                                multiple
                                value={filters}
                                onChange={handleFilterChange}
                                input={<OutlinedInput id="select-multiple-chip" label="Workflow Status Filter" />}
                                renderValue={(selected) => (
                                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                                    {selected.map((value) => (
                                        <Chip key={value} label={statusFilterOptions.filter((item)=>item.value == value)[0].name} sx={{
                                            color: statusFilterOptions.filter((item)=>item.value == value)[0].color,
                                        }}/>
                                    ))}
                                    </Box>
                                )}
                                MenuProps={MenuProps}>
                                {statusFilterOptions.map((option) => 
                                    <MenuItem 
                                        key={option.name} 
                                        value={option.value}>
                                            <Checkbox checked={filters.includes(option.value)} />
                                            <ListItemText primary={<Typography variant='subtitle2' color={option.color}>
                                                {option.name}
                                            </Typography>}/>
                                            
                                    </MenuItem>
                                )}
                            </Select>
                        </FormControl>
                    </Box>
                </AccordionActions>
                <AccordionDetails>
                    <TreeView
                        aria-label="customized"
                        defaultExpanded={["root"]}
                        defaultCollapseIcon={<KeyboardArrowDownIcon />}
                        defaultExpandIcon={<KeyboardArrowRightIcon />}
                        sx={{ overflowX: "scroll" }}>
                        {finishedExecutionTree && renderTree(finishedExecutionTree)}
                    </TreeView>
                </AccordionDetails>
            </Accordion>}
        </>
    );
}

export default WorkflowStatusExplorer;
