import * as React from 'react';
import DialogTitle from '@mui/material/DialogTitle';
import Dialog from '@mui/material/Dialog';
import { Box, Button, DialogActions, DialogContent, IconButton, Typography, styled, Tabs, Tab } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import MaximizeIcon from '@mui/icons-material/Fullscreen';
import RestoreIcon from '@mui/icons-material/FullscreenExit';
import { JsonForms, JsonFormsDispatch, withJsonFormsLayoutProps } from '@jsonforms/react';
import {
  materialCells,
  materialRenderers
} from '@jsonforms/material-renderers';
import {
  JsonFormsCore,
  JsonSchema,
  UISchemaElement,
  rankWith,
  uiTypeIs,
  and,
  categorizationHasCategory,
  Categorization,
  Category,
  LayoutProps
} from '@jsonforms/core';
import CodeEditorControl, { codeEditorTester } from './CodeEditorRenderer';

/**
 * Custom Tab Panel component for categorization layout
 */
interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`categorization-tabpanel-${index}`}
      aria-labelledby={`categorization-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
    </div>
  );
}

/**
 * Custom Material Categorization Layout Renderer
 * Renders categories as MUI Tabs with proper tab panel content
 */
const MaterialCategorizationLayoutRenderer = (props: LayoutProps) => {
  const { uischema, schema, path, visible, renderers, cells } = props;
  const [activeTab, setActiveTab] = React.useState(0);

  const categorization = uischema as Categorization;
  // Get all Category elements from the categorization
  const categories = (categorization.elements || []).filter(
    (el): el is Category => el.type === 'Category'
  );

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  if (!visible) {
    return null;
  }

  return (
    <Box sx={{ width: '100%' }}>
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
        >
          {categories.map((category: Category, index: number) => (
            <Tab
              key={index}
              label={category.label || `Tab ${index + 1}`}
              id={`categorization-tab-${index}`}
              aria-controls={`categorization-tabpanel-${index}`}
            />
          ))}
        </Tabs>
      </Box>
      {categories.map((category: Category, index: number) => (
        <TabPanel key={index} value={activeTab} index={index}>
          {category.elements?.map((element, elementIndex) => (
            <JsonFormsDispatch
              key={`${path}-${index}-${elementIndex}`}
              uischema={element}
              schema={schema}
              path={path}
              renderers={renderers}
              cells={cells}
            />
          ))}
        </TabPanel>
      ))}
    </Box>
  );
};

// Wrap with JSON Forms HOC to inject props
const MaterialCategorizationLayout = withJsonFormsLayoutProps(MaterialCategorizationLayoutRenderer);

/**
 * Custom tester for Categorization layout.
 * Checks if the UI schema is a Categorization with at least one Category element.
 */
const categorizationTester = rankWith(
  6, // Higher rank than default renderers
  and(
    uiTypeIs('Categorization'),
    categorizationHasCategory
  )
);

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
    },
    '& .MuiDialogContent-root': {
      padding: theme.spacing(2),
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
    },
    '& .MuiDialogActions-root': {
      padding: theme.spacing(1),
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
    },
    '& .MuiDialogTitle-root': {
      backgroundColor: theme.palette.background.paper || theme.palette.background.default || '#1e1e1e',
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

const MIN_DIALOG_WIDTH = 400;
const MIN_DIALOG_HEIGHT = 300;

const customRenderers = [
  { tester: codeEditorTester, renderer: CodeEditorControl },
  { tester: categorizationTester, renderer: MaterialCategorizationLayout },
  ...materialRenderers,
];

export interface NodeDialogProps {
    open: boolean;
    // selectedValue: string;
    onClose: (value: any) => void;
    onSave: (data: any) => void;
    initialData?: any;
    dataSchema?: JsonSchema;
    uiSchema?: UISchemaElement;
    readOnly?: boolean;
}

export default function NodeDialog({ onClose, onSave, readOnly=false, open, initialData, dataSchema, uiSchema }: NodeDialogProps) {

    const [data, setData] = React.useState(initialData);
    const [hasErrors, setHasErrors] = React.useState(false);
    const [dialogSize, setDialogSize] = React.useState({ width: 600, height: 600 });
    const [isResizing, setIsResizing] = React.useState(false);
    const [isMaximized, setIsMaximized] = React.useState(false);
    const resizeStartPos = React.useRef({ x: 0, y: 0, width: 0, height: 0 });
    const previousSize = React.useRef({ width: 600, height: 600 });

    const handleClose = React.useCallback((args: any) => {
        console.log("handleClose", args);
        onClose(data);
    }, [data]);

    const onSavePressed = React.useCallback((args: any) => {
        console.log("onSavePressed", args);
        onSave(data);
    }, [data]);

    const onDataChange = React.useCallback(({errors, data}: Pick<JsonFormsCore, "data" | "errors">) => {
        setData(data);
        errors && setHasErrors(errors.length > 0)
    }, [data]);

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
            onClose={handleClose}
            customWidth={dialogSize.width}
            customHeight={dialogSize.height}>
            <DialogTitle>Node Settings</DialogTitle>
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
                onClick={handleClose}
                sx={{
                    position: 'absolute',
                    right: 8,
                    top: 8,
                    color: (theme) => theme.palette.grey[500],
                }}>
                <CloseIcon />
            </IconButton>
            <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <Box sx={{
                    minWidth: "500px",
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'auto',
                    p: 2,
                    // Ensure JsonForms categorization layout takes full space
                    '& > div': {
                      display: 'flex',
                      flexDirection: 'column',
                      flex: 1,
                    },
                    // Style MUI Tabs for the categorization
                    '& .MuiTabs-root': {
                      minHeight: 'auto',
                      borderBottom: 1,
                      borderColor: 'divider',
                    },
                    // Ensure tab panels display correctly
                    '& .MuiBox-root[role="tabpanel"]': {
                      flex: 1,
                      overflow: 'auto',
                      pt: 2,
                    },
                    // Fix for hidden tab panels
                    '& .MuiBox-root[role="tabpanel"][hidden]': {
                      display: 'none',
                    },
                  }}>
                    <JsonForms
                        schema={dataSchema}
                        uischema={uiSchema}
                        data={data}
                        renderers={customRenderers}
                        cells={materialCells}
                        onChange={onDataChange}/>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button autoFocus onClick={handleClose}>
                    Cancel
                </Button>
                <Button
                    autoFocus
                    onClick={onSavePressed}
                    disabled={hasErrors || readOnly}>
                    <Typography>Save changes</Typography>
                </Button>
            </DialogActions>
            {!isMaximized && <ResizeHandle onMouseDown={handleResizeStart} />}
        </StyledDialog>
    );
}