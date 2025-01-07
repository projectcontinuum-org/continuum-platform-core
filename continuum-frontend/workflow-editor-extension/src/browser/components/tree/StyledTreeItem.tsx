import * as React from 'react';
import Box from '@mui/material/Box';
import { useSpring, animated } from '@react-spring/web';
import { TransitionProps } from '@mui/material/transitions';
import Collapse from '@mui/material/Collapse';
import { alpha, styled, useTheme } from '@mui/material/styles';
import { TreeItem, TreeItemProps, treeItemClasses } from '@mui/x-tree-view/TreeItem';
import { Typography } from '@mui/material';

declare module "react" {
    interface CSSProperties {
        "--tree-view-color"?: string;
        "--tree-view-bg-color"?: string;
    }
}

export type StyledTreeItemProps = TreeItemProps & {
    bgColor?: string;
    bgColorForDarkMode?: string;
    color?: string;
    colorForDarkMode?: string;
    labelIcon: React.ReactNode;
    labelInfo?: string;
    labelText: string;
    onDoubleClick?: React.MouseEventHandler<HTMLDivElement>;
};

function TransitionComponent(props: TransitionProps) {
    const style = useSpring({
        to: {
            opacity: props.in ? 1 : 0,
            transform: `translate3d(${props.in ? 0 : 20}px,0,0)`
        }
    });

    return (
        <animated.div style={style}>
            <Collapse {...props} />
        </animated.div>
    );
}

const CustomTreeItem = React.forwardRef(
    function CustomTreeItem(props: StyledTreeItemProps, ref: React.Ref<HTMLLIElement>) {
        const theme = useTheme();
        const {
            bgColor,
            color,
            labelIcon: LabelIcon,
            labelInfo,
            labelText,
            colorForDarkMode,
            bgColorForDarkMode,
            onDoubleClick,
            ...other
        } = props;

        const styleProps = {
            "--tree-view-color":
                theme.palette.mode !== "dark" ? color : colorForDarkMode,
            "--tree-view-bg-color":
                theme.palette.mode !== "dark" ? bgColor : bgColorForDarkMode,
        };

        return (
            <TreeItem
                label={
                    <Box
                        onDoubleClick={onDoubleClick}
                        sx={{
                            display: "flex",
                            alignItems: "center",
                            p: 0.5,
                            pr: 0,
                        }}>
                        <Box color="inherit" sx={{ mr: 1, mt: 1 }}>{LabelIcon}</Box>
                        <Typography
                            variant="body2"
                            sx={{ fontWeight: "inherit", flexGrow: 1 }}>
                            {labelText}
                        </Typography>
                        <Typography 
                            variant="caption" 
                            color="inherit">
                            {labelInfo}
                        </Typography>
                    </Box>
                }
                style={styleProps}
                {...other}
                TransitionComponent={TransitionComponent}
                ref={ref} />
        );
    }
);

const StyledTreeItem = styled(CustomTreeItem)(({ theme }) => ({
    [`& .${treeItemClasses.iconContainer}`]: {
        '& .close': {
            opacity: 0.3,
        },
    },
    [`& .${treeItemClasses.group}`]: {
        marginLeft: 10,
        paddingLeft: 10,
        borderLeft: `1px dashed ${alpha(theme.palette.text.primary, 0.4)}`,
    },
}));

export interface IRenderTreeItem {
    id: string;
    name: string;
    children?: readonly IRenderTreeItem[];
}


export default StyledTreeItem