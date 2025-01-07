import * as React from "react";
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { IBaseNodeData } from "@continuum/core";
import "./BaseNode.css"
import { Box, CircularProgress, Grid, Typography } from "@mui/material";
import Bolt from "@mui/icons-material/Bolt";

export default function DummyNode(props: IBaseNodeData) {

    return (
        <Grid container
            direction="row"
            justifyContent="space-between"
            alignItems="center"
            sx={{
                display: "flex",
                flexDirection: "row",
                justifyContent: "space-between",
                alignItems: "center",
                borderColor: "secondary.main",
                borderStyle: "solid",
                borderWidth: "2px",
                borderRadius: "10px",
                width: "200px",
                height: "100px",
                padding: "0px",
                bgcolor: "background.default"
            }}
            position="relative">
            <Grid item className="target-port-group" sx={{
                position: "absolute",
                width: "10px",
                height: "100%",
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",}}>
                {
                    props.inputs && Object.entries(props.inputs).map(([portId])=><Box key={portId} sx={{
                        background: "transparent",
                        borderColor: "secondary.main",
                        borderStyle: "solid",
                        borderWidth: "2px",
                        borderRadius: "50%",
                        minWidth: "10px",
                        minHeight: "10px",
                        top: "0px",
                        marginTop: "10px",
                        position: "relative",
                        left: "-15px"
                    }} />)
                }
            </Grid>
            <Grid className="node-content">
                <Grid className="inner">
                    <Grid className="body">
                        <Grid className="text" sx={{paddingLeft: "10px"}}>
                            <Typography variant="h6" sx={{
                                fontSize: "16px",
                                marginBottom: "2px",
                                lineHeight: 1
                            }}>{props.title}</Typography>
                            {props.subTitle && <Typography sx={{
                                fontSize: "12px",
                                color: "#777"
                            }} variant="h6">{props.subTitle}</Typography>}
                        </Grid>
                        {props.status == "BUSY" && 
                            <Grid className="progress">
                                <CircularProgress size={30} color="secondary" />
                            </Grid>
                        }
                        {props.status != "BUSY" && 
                            <Grid className="status-badge">
                                {props.status == "SUCCESS" && <CheckCircleIcon color="success"/>}
                                {props.status == "FAILED" && <CancelIcon color="error"/>}
                                {props.status == "WARNING" && <WarningAmberIcon color="warning"/>}
                                {props.status == "ACTIVE" && <Bolt color="warning"/>}
                            </Grid>
                        }
                    </Grid>
                </Grid>
            </Grid>
            <Grid item className="source-port-group">
                {
                    props.outputs && Object.entries(props.outputs).map(([portId])=><Box key={portId} sx={{
                        background: "transparent",
                        borderColor: "primary.main",
                        borderStyle: "solid",
                        borderWidth: "2px",
                        borderRadius: "50%",
                        minWidth: "10px",
                        minHeight: "10px",
                        marginTop: "10px",
                        position: "relative",
                        right: "-15px"
                    }} />)
                }
            </Grid>
        </Grid>
    );
    
}
