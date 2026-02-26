import * as React from 'react';
import { Dialog, DialogContent, DialogTitle, IconButton, styled, Tooltip } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import MaximizeIcon from '@mui/icons-material/Fullscreen';
import RestoreIcon from '@mui/icons-material/FullscreenExit';
import { MonacoEditorWrapper } from '../../monaco/MonacoEditorWrapper';
import { useMUIThemeStore } from '../../../store/MUIThemeStore';

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

export interface JsonViewerDialogProps {
    open: boolean;
    value: any;
    onClose: () => void;
}

export function JsonViewerDialog({ open, value, onClose }: JsonViewerDialogProps) {
    const [dialogSize, setDialogSize] = React.useState({ width: 800, height: 600 });
    const [isResizing, setIsResizing] = React.useState(false);
    const [isMaximized, setIsMaximized] = React.useState(false);
    const [copySuccess, setCopySuccess] = React.useState(false);
    const resizeStartPos = React.useRef({ x: 0, y: 0, width: 0, height: 0 });
    const previousSize = React.useRef({ width: 800, height: 600 });
    const monacoTheme = useMUIThemeStore((state) => state.monacoTheme);

    const jsonString = React.useMemo(() => {
        try {
            return JSON.stringify(value, null, 2);
        } catch (e) {
            return String(value);
        }
    }, [value]);

    const handleMaximize = React.useCallback(() => {
        if (isMaximized) {
            setDialogSize(previousSize.current);
            setIsMaximized(false);
        } else {
            previousSize.current = dialogSize;
            setDialogSize({
                width: window.innerWidth,
                height: window.innerHeight
            });
            setIsMaximized(true);
        }
    }, [isMaximized, dialogSize]);

    const handleCopy = React.useCallback(async () => {
        try {
            await navigator.clipboard.writeText(jsonString);
            setCopySuccess(true);
            setTimeout(() => setCopySuccess(false), 2000);
        } catch (err) {
            console.error('Failed to copy:', err);
        }
    }, [jsonString]);

    const handleResizeStart = React.useCallback((e: React.MouseEvent) => {
        if (isMaximized) return;
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
            <DialogTitle>JSON Viewer</DialogTitle>
            <Tooltip title={copySuccess ? "Copied!" : "Copy to clipboard"}>
                <IconButton
                    aria-label="copy"
                    onClick={handleCopy}
                    sx={{
                        position: 'absolute',
                        right: 88,
                        top: 8,
                        color: (theme) => copySuccess ? theme.palette.success.main : theme.palette.grey[500],
                    }}>
                    <ContentCopyIcon />
                </IconButton>
            </Tooltip>
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
            <DialogContent sx={{
                display: 'flex',
                flexDirection: 'column',
                minHeight: 0,
                minWidth: 0,
                p: 0,
                flex: 1,
                overflow: 'hidden'
            }}>
                <div style={{ width: '100%', height: '100%', position: 'relative' }}>
                    <MonacoEditorWrapper
                        value={jsonString}
                        language="json"
                        height="100%"
                        theme={monacoTheme}
                        options={{
                            readOnly: true,
                            minimap: { enabled: false },
                            scrollBeyondLastLine: false,
                            wordWrap: 'on',
                            automaticLayout: true,
                            lineNumbers: 'on',
                            folding: true,
                            renderWhitespace: 'none',
                        }}
                    />
                </div>
            </DialogContent>
            {!isMaximized && <ResizeHandle onMouseDown={handleResizeStart} />}
        </StyledDialog>
    );
}
