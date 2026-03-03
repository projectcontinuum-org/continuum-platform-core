import * as React from 'react';
import { Box } from '@mui/material';
import { ThemeProvider, experimental_extendTheme, Experimental_CssVarsProvider as CssVarsProvider } from '@mui/material';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import { useNodeDragStore } from '../../store/NodeDragStore';
import DummyNode from '../../components/node/DummyNode';

/**
 * Floating overlay that shows a BaseNode preview while dragging from Node Explorer.
 * Uses Zustand store to track drag state and position.
 */
export default function NodeDragOverlay() {
    const [theme] = useMUIThemeStore((state) => [state.theme]);
    const cssTheme = experimental_extendTheme(theme);
    const isDragging = useNodeDragStore((state) => state.isDragging);
    const draggedNodeData = useNodeDragStore((state) => state.draggedNodeData);
    const position = useNodeDragStore((state) => state.position);

    if (!isDragging || !draggedNodeData) {
        return null;
    }

    return (
        <CssVarsProvider theme={cssTheme}>
            <ThemeProvider theme={theme}>
                <Box
                    sx={{
                        zIndex: 9999,
                        position: 'fixed',
                        top: position.y - 50,
                        left: position.x - 100,
                        pointerEvents: 'none',
                        transform: 'scale(1.5)',
                        transformOrigin: 'top left'
                    }}>
                    <DummyNode {...draggedNodeData} />
                </Box>
            </ThemeProvider>
        </CssVarsProvider>
    );
}
