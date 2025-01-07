import * as React from "react";
import { Handle, NodeProps, Position } from "reactflow";
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { IBaseNodeData } from "@continuum/core";
import "./BaseNode.css"
import { CircularProgress, Grid, Typography } from "@mui/material";
import Bolt from "@mui/icons-material/Bolt";
import { useMUIThemeStore } from "../../store/MUIThemeStore";
import { mirage } from 'ldrs'

mirage.register()

export default function BaseNode(props: NodeProps<IBaseNodeData>) {
    const [theme] = useMUIThemeStore((state)=>([state.theme]))

    return (
        <Grid container
            direction="row"
            justifyContent="space-between"
            alignItems="center"
            className="node-body"
            position="relative">
            <Grid container 
                direction="column"
                justifyContent="space-around"
                alignItems="flex-start"
                sx={{
                    height: "100%",
                    width: "10px"
                }}>
                {
                    props.data.inputs && Object.entries(props.data.inputs).map(([portId])=><Handle key={portId} id={portId} type="target" position={Position.Left} />)
                }
            </Grid>
            <Grid className="node-content">
                <Grid className="inner">
                    <Grid className="body">
                        <Grid className="text">
                            <Typography variant="h6">{props.data.title}</Typography>
                            {props.data.subTitle && <Typography className="subline" variant="h6">{props.data.subTitle + ` #${props.id} ${props.data.status != null ? props.data.status : ""}`}</Typography>}
                        </Grid>
                        
                        {props.data.status == "BUSY" && <Grid className="progress">
                            <CircularProgress size={30} color="secondary" sx={{
                                animationDuration: '200ms',
                                }}
                                {...props.data.progressPercentage && {variant: "determinate", value: props.data.progressPercentage}}/>
                        </Grid>}
                        {props.data.status == "PRE-PROCESSING" && <Grid className="progress">
                            <l-mirage color={theme.palette.primary.main}/>
                        </Grid>}
                        
                        {props.data.status != "BUSY" && 
                            <Grid className="status-badge">
                                {props.data.status == "SUCCESS" && <CheckCircleIcon color="success"/>}
                                {props.data.status == "FAILED" && <CancelIcon color="error"/>}
                                {props.data.status == "WARNING" && <WarningAmberIcon color="warning"/>}
                                {props.data.status == "ACTIVE" && <Bolt color="warning"/>}
                            </Grid>
                        }
                    </Grid>
                </Grid>
            </Grid>
            <Grid container 
                direction="column"
                justifyContent="space-around"
                alignItems="flex-end"
                sx={{
                    height: "100%",
                    width: "10px"
                }}>
                {
                    props.data.outputs && Object.entries(props.data.outputs).map(([portId])=><Handle key={portId} id={portId} type="source" position={Position.Right} />)
                }
            </Grid>
        </Grid>
    );
    
}
