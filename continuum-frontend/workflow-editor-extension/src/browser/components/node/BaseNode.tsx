import * as React from "react";
import { Handle, NodeProps, Position } from "reactflow";
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import SkipNextIcon from '@mui/icons-material/SkipNext';
import { IBaseNodeData, StageStatus } from "@continuum/core";
import "./BaseNode.css";
import { Box, CircularProgress, Grid, Step, StepLabel, Stepper, Tooltip, Typography } from "@mui/material";
import Bolt from "@mui/icons-material/Bolt";
import { useMUIThemeStore } from "../../store/MUIThemeStore";
import { mirage } from 'ldrs'
import DynamicIcon from "../utils/DynamicIcon";

mirage.register()

const formatDuration = (ms?: number): string | null => {
    if (!ms) return null;

    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d`;
    if (hours > 0) return `${hours}h`;
    if (minutes > 0) return `${minutes}m`;
    if (seconds > 0) return `${seconds}s`;
    return `${ms}ms`;
};

const getStepIcon = (status: StageStatus) => {
    switch (status) {
        case StageStatus.COMPLETED:
            return <CheckCircleIcon color="success" fontSize="small" />;
        case StageStatus.IN_PROGRESS:
            return <HourglassEmptyIcon color="info" fontSize="small" />;
        case StageStatus.FAILED:
            return <CancelIcon color="error" fontSize="small" />;
        case StageStatus.SKIPPED:
            return <SkipNextIcon color="disabled" fontSize="small" />;
        case StageStatus.PENDING:
        default:
            return <RadioButtonUncheckedIcon color="disabled" fontSize="small" />;
    }
};

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

export default function BaseNode(props: NodeProps<IBaseNodeData>) {
    const [theme] = useMUIThemeStore((state)=>([state.theme]))

    return (
      <Grid
        container
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        className="node-body"
        position="relative"
      >
        <Grid
          container
          direction="column"
          justifyContent="space-around"
          alignItems="flex-start"
          sx={{
            height: "100%",
            width: "10px",
          }}
        >
          {props.data.inputs &&
            Object.entries(props.data.inputs).map(([portId]) => (
              <Handle
                key={portId}
                id={portId}
                type="target"
                position={Position.Left}
              />
            ))}
        </Grid>
        <Grid className="node-content">
          <Box className="inner">
            <Box className="body">
              {props.data.icon && (
                <DynamicIcon icon={props.data.icon} fontSize="large" />
              )}
              <Box className="text">
                <Typography variant="h6">{props.data.title}</Typography>
                {props.data.subTitle && (
                  <Tooltip title={props.data.subTitle} placement="top">
                    <Typography className="subline" variant="body2">
                      {props.data.subTitle}
                    </Typography>
                  </Tooltip>
                )}
                <Typography variant="caption" sx={{ display: 'block', color: 'text.disabled', fontSize: '0.65rem' }}>
                  #{props.id}
                </Typography>
                {props.data.status && (
                  <Typography
                    variant="caption"
                    sx={{ display: 'block', color: getStatusColor(props.data.status), fontWeight: 500 }}
                  >
                    {props.data.status}
                  </Typography>
                )}
                {props.data.nodeProgress?.totalDurationMs && (
                  <Typography variant="caption" sx={{ display: 'block', color: 'text.disabled', fontSize: '0.65rem' }}>
                    {formatDuration(props.data.nodeProgress.totalDurationMs)}
                  </Typography>
                )}
              </Box>

              {props.data.status == "BUSY" && (
                <Box className="progress">
                  <CircularProgress
                    size={28}
                    color="secondary"
                    sx={{
                      animationDuration: "200ms",
                    }}
                    {...(props.data.nodeProgress?.progressPercentage && {
                      variant: "determinate",
                      value: props.data.nodeProgress?.progressPercentage,
                    })}
                  />
                </Box>
              )}
              {props.data.status == "PRE-PROCESSING" && (
                <Box className="progress">
                  <l-mirage color={theme.palette.primary.main} />
                </Box>
              )}

              {props.data.status != "BUSY" && (
                <Box className="status-badge">
                  {props.data.status == "SUCCESS" && (
                    <CheckCircleIcon color="success" />
                  )}
                  {props.data.status == "FAILED" && (
                    <CancelIcon color="error" />
                  )}
                  {props.data.status == "WARNING" && (
                    <WarningAmberIcon color="warning" />
                  )}
                  {props.data.status == "ACTIVE" && <Bolt color="warning" />}
                </Box>
              )}
            </Box>
            {props.data.nodeProgress?.stageStatus && (
              <Stepper
                orientation="vertical"
                sx={{
                  mt: 1.25,
                  mb: 1.25,
                  '& .MuiStepLabel-root': { padding: '0' },
                  '& .MuiStepLabel-label': { fontSize: '0.75rem' },
                  '& .MuiStepConnector-line': { minHeight: '10px' },
                  '& .MuiStep-root': { padding: '0' },
                }}
              >
                {Object.entries(props.data.nodeProgress.stageStatus).map(([stageName, status]) => (
                  <Step key={stageName} active={status === StageStatus.IN_PROGRESS} completed={status === StageStatus.COMPLETED}>
                    <StepLabel
                      StepIconComponent={() => getStepIcon(status)}
                      error={status === StageStatus.FAILED}
                    >
                      {stageName}
                    </StepLabel>
                  </Step>
                ))}
              </Stepper>
            )}
          </Box>
        </Grid>
        <Grid
          container
          direction="column"
          justifyContent="space-around"
          alignItems="flex-end"
          sx={{
            height: "100%",
            width: "10px",
          }}
        >
          {props.data.outputs &&
            Object.entries(props.data.outputs).map(([portId]) => (
              <Handle
                key={portId}
                id={portId}
                type="source"
                position={Position.Right}
              />
            ))}
        </Grid>
      </Grid>
    );
    
}
