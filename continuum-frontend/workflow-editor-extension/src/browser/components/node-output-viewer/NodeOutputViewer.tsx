import * as React from 'react';
import DialogTitle from '@mui/material/DialogTitle';
import Dialog from '@mui/material/Dialog';
import {DialogContent, IconButton, MenuItem, Select, SelectChangeEvent, styled, Typography} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import MaximizeIcon from '@mui/icons-material/Fullscreen';
import RestoreIcon from '@mui/icons-material/FullscreenExit';
import { INodeOutputs } from '@continuum/core';
import { TableOutputView } from './table-output-view/TableOutputView';

interface StyledDialogProps {
    customWidth?: number;
    customHeight?: number;
}

const StyledDialog = styled(Dialog, {
    shouldForwardProp: (prop) => prop !== 'customWidth' && prop !== 'customHeight',
})<StyledDialogProps>(({ theme, customWidth, customHeight }) => ({
    '& .MuiPaper-root': {
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
      backgroundImage: 'none',
      opacity: 1,
      width: customWidth ? `${customWidth}px` : 'auto',
      height: customHeight ? `${customHeight}px` : 'auto',
      maxWidth: 'none',
      maxHeight: 'none',
      position: 'relative',
      overflow: 'visible',
      display: 'flex',
      flexDirection: 'column',
    },
    '& .MuiDialogContent-root': {
      padding: theme.spacing(2),
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
      flex: 1,
      overflow: 'auto',
      minHeight: 0,
    },
    '& .MuiDialogActions-root': {
      padding: theme.spacing(1),
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
      flexShrink: 0,
    },
    '& .MuiDialogTitle-root': {
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
      flexShrink: 0,
    },
}));

const ResizeHandle = styled('div')(({ theme }) => ({
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: '20px',
    height: '20px',
    cursor: 'nwse-resize',
    zIndex: 9999,
    '&::after': {
        content: '""',
        position: 'absolute',
        bottom: '2px',
        right: '2px',
        width: '0',
        height: '0',
        borderStyle: 'solid',
        borderWidth: '0 0 12px 12px',
        borderColor: `transparent transparent ${theme.palette.grey[500]} transparent`,
    },
}));

const MIN_DIALOG_WIDTH = 600;
const MIN_DIALOG_HEIGHT = 400;

export interface NodeOutputViewerProps {
    open: boolean;
    onClose: (value: any) => void;
    initialData?: any;
    nodeOutputs: INodeOutputs;
}

export default function NodeOutputViewer({ onClose, open, nodeOutputs }: NodeOutputViewerProps) {

    const [portId, setPortId] = React.useState<string>(nodeOutputs ? Object.keys(nodeOutputs)[0] : "");
    const [portData, setPortData] = React.useState(nodeOutputs[portId]);
    const [dialogSize, setDialogSize] = React.useState({ width: 800, height: 600 });
    const [isResizing, setIsResizing] = React.useState(false);
    const [isMaximized, setIsMaximized] = React.useState(false);
    const resizeStartPos = React.useRef({ x: 0, y: 0, width: 0, height: 0 });
    const previousSize = React.useRef({ width: 800, height: 600 });

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

    const handleMaximize = React.useCallback(() => {
        if (isMaximized) {
            // Restore to previous size
            setDialogSize(previousSize.current);
            setIsMaximized(false);
        } else {
            // Save current size and maximize
            previousSize.current = dialogSize;
            setDialogSize({
                width: window.innerWidth,
                height: window.innerHeight
            });
            setIsMaximized(true);
        }
    }, [isMaximized, dialogSize]);

    const handleResizeStart = React.useCallback((e: React.MouseEvent) => {
        if (isMaximized) return; // Don't allow resize when maximized
        e.preventDefault();
        e.stopPropagation();
        setIsResizing(true);
        resizeStartPos.current = {
            x: e.clientX,
            y: e.clientY,
            width: dialogSize.width,
            height: dialogSize.height,
        };
    }, [dialogSize, isMaximized]);

    React.useEffect(() => {
        if (!isResizing) return;

        const handleMouseMove = (e: MouseEvent) => {
            const deltaX = e.clientX - resizeStartPos.current.x;
            const deltaY = e.clientY - resizeStartPos.current.y;

            const newWidth = Math.max(MIN_DIALOG_WIDTH, Math.min(window.innerWidth, resizeStartPos.current.width + deltaX));
            const newHeight = Math.max(MIN_DIALOG_HEIGHT, Math.min(window.innerHeight, resizeStartPos.current.height + deltaY));

            setDialogSize({ width: newWidth, height: newHeight });
        };

        const handleMouseUp = () => {
            setIsResizing(false);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isResizing]);

    return (
        <StyledDialog
            open={open}
            onClose={onClose}
            customWidth={dialogSize.width}
            customHeight={dialogSize.height}>
            <DialogTitle>Node Outputs</DialogTitle>
            <IconButton
                aria-label="maximize"
                onClick={handleMaximize}
                sx={{
                    position: 'absolute',
                    right: 48,
                    top: 8,
                    color: (theme) => theme.palette.grey[500],
                }}>
                {isMaximized ? <RestoreIcon /> : <MaximizeIcon />}
            </IconButton>
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
            <DialogContent dividers sx={{
                display: 'flex',
                flexDirection: 'column',
                minHeight: 0,
                minWidth: 0,
                gap: 2
            }}>
                <Select
                    value={portId}
                    label="Port Id"
                    onChange={portIdChange}
                    sx={{
                        flexShrink: 0,
                        width: '200px',
                        minWidth: '200px',
                        maxWidth: '200px'
                    }}>
                    {Object.keys(nodeOutputs).map((key) => (
                        <MenuItem value={key} key={key}>{key}</MenuItem>
                    ))}
                </Select>
              {portData.status == "SUCCESS" && <TableOutputView outputData={portData}/>}
              {portData.status == "FAILED" && <Typography
                  variant="h6"
                  color="error"
                  gutterBottom>
                {portData.data + "\n ERROR"}
              </Typography>}
            </DialogContent>
            {!isMaximized && <ResizeHandle onMouseDown={handleResizeStart} />}
        </StyledDialog>
    );
}