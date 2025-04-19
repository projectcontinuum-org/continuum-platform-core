import * as React from 'react';
import DialogTitle from '@mui/material/DialogTitle';
import Dialog from '@mui/material/Dialog';
import { DialogContent, IconButton, MenuItem, Select, SelectChangeEvent, styled } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { INodeOutputs } from '@continuum/core';
import { TableOutputView } from './table-output-view/TableOutputView';

const StyledDialog = styled(Dialog)(({ theme }) => ({
    '& .MuiDialogContent-root': {
      padding: theme.spacing(2),
    },
    '& .MuiDialogActions-root': {
      padding: theme.spacing(1),
    },
}));

export interface NodeOutputViewerProps {
    open: boolean;
    onClose: (value: any) => void;
    initialData?: any;
    nodeOutputs: INodeOutputs;
}

export default function NodeOutputViewer({ onClose, open, nodeOutputs }: NodeOutputViewerProps) {

    const [portId, setPortId] = React.useState<string>(nodeOutputs ? Object.keys(nodeOutputs)[0] : "");
    const [portData, setPortData] = React.useState(nodeOutputs[portId]);

    // const rows: GridRowsProp = [
    //     { id: 1, col1: 'Hello', col2: 'World' },
    //     { id: 2, col1: 'DataGridPro', col2: 'is Awesome' },
    //     { id: 3, col1: 'MUI', col2: 'is Amazing' },
    // ];
      
    // const columns: GridColDef[] = [
    //     { field: 'col1', headerName: 'Column 1', width: 150 },
    //     { field: 'col2', headerName: 'Column 2', width: 150 },
    // ];

    const portIdChange = React.useCallback((event: SelectChangeEvent)=>{
        setPortId(event.target.value);
        setPortData(nodeOutputs[event.target.value]);
    }, [setPortId]);

    return (
        <StyledDialog 
            open={open}
            onClose={onClose}
            maxWidth={false} // Disable default max width
            fullWidth // Allow dialog to adjust to content width
            scroll="paper" // Enable scrolling if content overflows
            sx={{
              '& .MuiDialog-paper': {
                width: 'auto', // Adjust width to fit content
                maxWidth: '90vw', // Optional: Limit max width to 90% of the viewport
                height: 'auto', // Adjust height to fit content
                maxHeight: '90vh', // Optional: Limit max height to 90% of the viewport
              },
            }}>
            <DialogTitle>Node Outputs</DialogTitle>
            <IconButton
                aria-label="close"
                onClick={onClose}
                sx={{
                    position: 'absolute',
                    right: 8,
                    top: 8,
                    color: (theme) => theme.palette.grey[500],
                }}>
                <CloseIcon />
            </IconButton>
            <DialogContent dividers>
                <Select
                    value={portId}
                    label="Port Id"
                    onChange={portIdChange}>
                    {Object.keys(nodeOutputs).map((key) => (
                        <MenuItem value={key} key={key}>{key}</MenuItem>
                    ))}
                </Select>
                {/* <Typography variant="h6" gutterBottom>
                    {JSON.stringify(portData)}
                </Typography> */}
                <TableOutputView outputData={portData}/>
            </DialogContent>
        </StyledDialog>
    );
}