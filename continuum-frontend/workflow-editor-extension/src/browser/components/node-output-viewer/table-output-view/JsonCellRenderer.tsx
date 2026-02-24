import React from 'react';
import { Box, IconButton, Typography } from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';

export interface JsonCellRendererProps {
    value: any;
    onClick: (value: any) => void;
}

export function JsonCellRenderer({ value, onClick }: JsonCellRendererProps) {
    if (typeof value !== 'object' || value === null) {
        return <span>{String(value)}</span>;
    }

    const isArray = Array.isArray(value);
    const preview = isArray ? '[...]' : '{...}';
    const count = isArray ? value.length : Object.keys(value).length;
    const countLabel = isArray ? 'items' : 'keys';

    return (
        <Box
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                width: '100%',
                overflow: 'hidden'
            }}
        >
            <Typography
                variant="body2"
                sx={{
                    fontFamily: 'monospace',
                    color: 'text.secondary',
                    flexShrink: 0
                }}
            >
                {preview}
            </Typography>
            <Typography
                variant="caption"
                sx={{
                    color: 'text.disabled',
                    flexShrink: 0
                }}
            >
                ({count} {countLabel})
            </Typography>
            <IconButton
                size="small"
                onClick={(e) => {
                    e.stopPropagation();
                    onClick(value);
                }}
                sx={{
                    ml: 'auto',
                    flexShrink: 0,
                    padding: '2px'
                }}
            >
                <OpenInNewIcon fontSize="small" />
            </IconButton>
        </Box>
    );
}
