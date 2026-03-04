import * as React from "react";
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { IBaseNodeData } from "@continuum/core";
import "./BaseNode.css";
import { Box, CircularProgress, Grid, Tooltip, Typography } from "@mui/material";
import Bolt from "@mui/icons-material/Bolt";
import DynamicIcon from "../utils/DynamicIcon";

const getStatusColor = (status: IBaseNodeData['status']) => {
    switch (status) {
        case 'SUCCESS':
            return 'success.main';
        case 'FAILED':
            return 'error.main';
        case 'WARNING':
            return 'warning.main';
        case 'BUSY':
        case 'PRE-PROCESSING':
        case 'POST-PROCESSING':
            return 'info.main';
        case 'ACTIVE':
            return 'warning.main';
        default:
            return 'text.secondary';
    }
};

/**
 * A lightweight version of BaseNode without ReactFlow Handle components.
 * Used for drag preview overlay where ReactFlow context is not available.
 */
export default function DummyNode(props: IBaseNodeData) {
    return (
        <Grid
            container
            direction="row"
            justifyContent="space-between"
            alignItems="center"
            className="node-body"
            position="relative"
            sx={{
                borderRadius: '10px',
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'space-between',
                minHeight: '100px',
                minWidth: '200px',
                fontFamily: '"Fira Mono", Monospace',
                fontWeight: 500,
                letterSpacing: '-0.2px',
                borderColor: 'secondary.main',
                borderWidth: '2px',
                borderStyle: 'solid',
                bgcolor: 'background.default',
                backdropFilter: 'blur(8px)',
            }}
        >
            <Grid
                container
                direction="column"
                justifyContent="space-around"
                alignItems="center"
                className="target-port-group"
                sx={{
                    position: 'absolute',
                    left: 0,
                    height: "100%",
                    width: "10px",
                }}
            >
                {props.inputs &&
                    Object.entries(props.inputs).map(([portId]) => (
                        <Box
                            key={portId}
                            sx={{
                                background: 'transparent',
                                borderStyle: 'solid',
                                borderWidth: '2px',
                                borderRadius: '50%',
                                borderColor: 'secondary.main',
                                minWidth: '10px',
                                minHeight: '10px',
                                marginTop: '10px',
                                position: 'relative',
                                left: '-15px',
                            }}
                        />
                    ))}
            </Grid>
            <Grid className="node-content">
                <Box className="inner">
                    <Box className="body" sx={{
                        display: 'flex',
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: '8px',
                        minWidth: '200px',
                        minHeight: '80px',
                        padding: '10px',
                    }}>
                        {props.icon && (
                            <DynamicIcon icon={props.icon} fontSize="large" />
                        )}
                        <Box className="text">
                            <Typography variant="h6">{props.title}</Typography>
                            {props.subTitle && (
                                <Tooltip title={props.subTitle} placement="top">
                                    <Typography className="subline" variant="body2">
                                        {props.subTitle}
                                    </Typography>
                                </Tooltip>
                            )}
                            {props.status && (
                                <Typography
                                    variant="caption"
                                    sx={{ display: 'block', color: getStatusColor(props.status), fontWeight: 500 }}
                                >
                                    {props.status}
                                </Typography>
                            )}
                        </Box>

                        {props.status == "BUSY" && (
                            <Box className="progress">
                                <CircularProgress
                                    size={28}
                                    color="secondary"
                                    sx={{
                                        animationDuration: "200ms",
                                    }}
                                />
                            </Box>
                        )}

                        {props.status != "BUSY" && (
                            <Box className="status-badge">
                                {props.status == "SUCCESS" && (
                                    <CheckCircleIcon color="success" />
                                )}
                                {props.status == "FAILED" && (
                                    <CancelIcon color="error" />
                                )}
                                {props.status == "WARNING" && (
                                    <WarningAmberIcon color="warning" />
                                )}
                                {props.status == "ACTIVE" && <Bolt color="warning" />}
                            </Box>
                        )}
                    </Box>
                </Box>
            </Grid>
            <Grid
                container
                direction="column"
                justifyContent="space-around"
                alignItems="center"
                className="source-port-group"
                sx={{
                    position: 'absolute',
                    right: 0,
                    height: "100%",
                    width: "10px",
                }}
            >
                {props.outputs &&
                    Object.entries(props.outputs).map(([portId]) => (
                        <Box
                            key={portId}
                            sx={{
                                background: 'transparent',
                                borderStyle: 'solid',
                                borderWidth: '2px',
                                borderRadius: '50%',
                                borderColor: 'primary.main',
                                minWidth: '10px',
                                minHeight: '10px',
                                marginTop: '10px',
                                position: 'relative',
                                right: '-15px',
                            }}
                        />
                    ))}
            </Grid>
        </Grid>
    );
}
