import * as React from 'react';
import DialogTitle from '@mui/material/DialogTitle';
import Dialog from '@mui/material/Dialog';
import { Box, Button, DialogActions, DialogContent, IconButton, Typography, styled } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { JsonForms } from '@jsonforms/react';
import { materialCells, materialRenderers } from '@jsonforms/material-renderers';
import { JsonFormsCore, JsonSchema, UISchemaElement } from '@jsonforms/core';

const StyledDialog = styled(Dialog)(({ theme }) => ({
    '& .MuiDialogContent-root': {
      padding: theme.spacing(2),
    },
    '& .MuiDialogActions-root': {
      padding: theme.spacing(1),
    },
}));

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

    return (
        <StyledDialog 
            open={open} 
            onClose={handleClose} >
            <DialogTitle>Node Settings</DialogTitle>
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
            <DialogContent dividers>
                <Box sx={{
                    minWidth: "500px",
                    minHeight: "500px",
                    p: 5}}>
                    <JsonForms
                        schema={dataSchema}
                        uischema={uiSchema}
                        data={data}
                        renderers={materialRenderers}
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
        </StyledDialog>
    );
}