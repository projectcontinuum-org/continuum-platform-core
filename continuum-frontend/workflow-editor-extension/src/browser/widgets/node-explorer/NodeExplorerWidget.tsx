/**
 * NodeExplorerWidget - A sidebar widget for browsing and selecting workflow nodes
 *
 * ## Overview
 * This widget displays a hierarchical tree of available workflow nodes organized by category.
 * Users can browse, search, and add nodes to their workflow by double-clicking or dragging.
 *
 * ## Architecture
 * The widget is composed of two main parts:
 * 1. **Toolbar** - Contains expand/collapse all buttons (MUI IconButtons)
 * 2. **TreeWidget** - Theia's tree infrastructure with custom rendering and search
 *
 * ## Key Components
 * - `NodeExplorerWidget` (this file) - Main container widget, manages layout and toolbar
 * - `NodeExplorerTreeWidget` - Custom Theia TreeWidget with drag-and-drop support
 * - `NodeExplorerTree` - Tree data structure with lazy loading from backend API
 * - `NodeExplorerTreeSearch` - Custom search that calls backend API (see below)
 *
 * ## Why Custom Search Handler?
 * Theia's default TreeSearch only filters nodes that are ALREADY LOADED in the tree.
 * Since our tree uses lazy loading (children are fetched from the backend only when
 * a category is expanded), unexpanded categories' children would never be searchable.
 *
 * Our custom `NodeExplorerTreeSearch` solves this by:
 * 1. Calling the backend `/api/v1/node-explorer/search` API with the search query
 * 2. The backend searches ALL nodes (including nested ones not yet loaded)
 * 3. Automatically expanding parent categories to make matching nodes visible
 * 4. Filtering the tree display to show only matching results
 *
 * ## Data Flow
 * 1. User types in search box → NodeExplorerTreeSearch.filter() called
 * 2. filter() calls backend API → returns matching nodes
 * 3. Parent categories are expanded → triggers lazy loading
 * 4. passesFilters() filters visible nodes → tree re-renders with results
 *
 * @see NodeExplorerTreeSearch for search implementation details
 * @see NodeExplorerTree for tree data structure and lazy loading
 * @see NodeExplorerService for backend API client
 */

import * as React from 'react';
import { BaseWidget, Message, BoxLayout, ReactWidget } from '@theia/core/lib/browser';
import { inject, injectable, postConstruct } from '@theia/core/shared/inversify';
import { Command } from '@theia/core';
import { ExpandableTreeNode, CompositeTreeNode, TreeNode } from '@theia/core/lib/browser/tree';
import { NodeExplorerTreeWidget } from '../../tree/node-explorer/NodeExplorerTreeWidget';
import { NodeExplorerRootNode, NodeExplorerLeafNode } from '../../tree/node-explorer/NodeExplorerTree';
import WorkflowEditorWidgetFactory from '../workflow-editor/WorkflowEditorWidgetFactory';
import { IconButton, Tooltip, Box } from '@mui/material';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLess';
import { ThemeProvider, experimental_extendTheme, Experimental_CssVarsProvider as CssVarsProvider } from '@mui/material';
import { useMUIThemeStore } from '../../store/MUIThemeStore';
import NodeDragOverlay from './NodeDragOverlay';

/**
 * React component for the toolbar content.
 * Uses MUI components for consistent styling with the rest of the application.
 *
 * @param onExpandAll - Callback to expand all tree nodes
 * @param onCollapseAll - Callback to collapse all tree nodes
 */
function NodeExplorerToolbarContent({
    onExpandAll,
    onCollapseAll
}: {
    onExpandAll: () => void;
    onCollapseAll: () => void;
}) {
    // Get the current MUI theme from the global store
    const [theme] = useMUIThemeStore((state) => [state.theme]);
    const cssTheme = experimental_extendTheme(theme);

    return (
        <CssVarsProvider theme={cssTheme}>
            <ThemeProvider theme={theme}>
                <Box sx={{
                    display: 'flex',
                    gap: 0.5,
                    padding: '4px 8px',
                    borderBottom: '1px solid var(--theia-sideBarSectionHeader-border)',
                    alignItems: 'center',
                    justifyContent: 'flex-end'
                }}>
                    <Tooltip title="Expand All">
                        <IconButton
                            size="small"
                            onClick={onExpandAll}
                            sx={{ padding: '4px' }}
                        >
                            <UnfoldMoreIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Collapse All">
                        <IconButton
                            size="small"
                            onClick={onCollapseAll}
                            sx={{ padding: '4px' }}
                        >
                            <UnfoldLessIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Box>
            </ThemeProvider>
        </CssVarsProvider>
    );
}

/**
 * Toolbar widget that wraps the React content in a Theia-compatible ReactWidget.
 * This allows us to use MUI components within Theia's widget system.
 *
 * The toolbar is added to the NodeExplorerWidget's BoxLayout and provides
 * quick access to expand/collapse operations.
 */
class NodeExplorerToolbar extends ReactWidget {
    protected onExpandAll: () => void = () => {};
    protected onCollapseAll: () => void = () => {};

    constructor() {
        super();
        this.addClass('node-explorer-toolbar');
        // Ensure toolbar doesn't collapse to zero height in flex layout
        this.node.style.minHeight = '36px';
        this.node.style.flexShrink = '0';
    }

    /**
     * Set the callback handlers for toolbar buttons.
     * Must be called after construction to wire up the parent widget's methods.
     */
    setHandlers(onExpandAll: () => void, onCollapseAll: () => void): void {
        this.onExpandAll = onExpandAll;
        this.onCollapseAll = onCollapseAll;
        this.update();
    }

    protected render(): React.ReactNode {
        return (
            <>
                <NodeExplorerToolbarContent
                    onExpandAll={this.onExpandAll}
                    onCollapseAll={this.onCollapseAll}
                />
                <NodeDragOverlay />
            </>
        );
    }
}

/**
 * Main Node Explorer widget displayed in the IDE sidebar.
 *
 * This widget allows users to:
 * - Browse available workflow nodes organized by category
 * - Search for nodes using the built-in search box (searches backend API)
 * - Expand/collapse categories using toolbar buttons
 * - Add nodes to workflow by double-clicking or dragging
 *
 * The widget uses Theia's BaseWidget as a container and composes:
 * - A toolbar (NodeExplorerToolbar) with expand/collapse buttons
 * - A tree widget (NodeExplorerTreeWidget) with the node hierarchy
 */
@injectable()
export default class NodeExplorerWidget extends BaseWidget {
    static readonly ID = 'continuum-node-explorer:widget';
    static readonly LABEL = 'Node Explorer';
    static readonly COMMAND: Command = { id: 'node-explorer-widget:command' };

    /** The tree widget that displays the node hierarchy */
    @inject(NodeExplorerTreeWidget)
    protected readonly treeWidget!: NodeExplorerTreeWidget;

    /** Factory to access the active workflow editor for adding nodes */
    @inject(WorkflowEditorWidgetFactory)
    protected readonly workflowEditorWidgetFactory!: WorkflowEditorWidgetFactory;

    /** Toolbar widget with expand/collapse buttons */
    protected toolbar!: NodeExplorerToolbar;

    @postConstruct()
    protected init(): void {
        // Configure widget identity and appearance
        this.id = NodeExplorerWidget.ID;
        this.title.label = NodeExplorerWidget.LABEL;
        this.title.caption = NodeExplorerWidget.LABEL;
        this.title.closable = false;
        this.title.iconClass = 'continuum continuum-widget fa fa-solid fa-folder-tree';

        // Make this widget fill available space using flexbox
        this.node.style.display = 'flex';
        this.node.style.flexDirection = 'column';
        this.node.style.height = '100%';

        // Create and configure the toolbar
        this.toolbar = new NodeExplorerToolbar();
        this.toolbar.setHandlers(
            () => this.expandAll(),
            () => this.collapseAll()
        );

        // Set up vertical layout: toolbar on top, tree below
        const layout = new BoxLayout({ direction: 'top-to-bottom' });
        layout.addWidget(this.toolbar);
        layout.addWidget(this.treeWidget);
        BoxLayout.setStretch(this.toolbar, 0);  // Toolbar takes minimum space
        BoxLayout.setStretch(this.treeWidget, 1);  // Tree fills remaining space
        this.layout = layout;

        // Ensure tree widget fills its container
        this.treeWidget.node.style.flex = '1';
        this.treeWidget.node.style.height = '100%';
        this.treeWidget.node.style.overflow = 'auto';

        // Wire up double-click to add nodes to the active workflow
        this.treeWidget.onNodeDoubleClick(node => {
            this.handleNodeDoubleClick(node);
        });

        // Wire up drag-end to add nodes to the active workflow
        this.treeWidget.onNodeDragEnd(({ node, mouseEvent }) => {
            this.handleNodeDragEnd(node, mouseEvent);
        });

        this.update();
    }

    /**
     * Expand all tree nodes recursively.
     * This triggers lazy loading for all categories, fetching their children from the backend.
     */
    protected async expandAll(): Promise<void> {
        const root = this.treeWidget.model.root;
        if (root && CompositeTreeNode.is(root)) {
            await this.expandNodeRecursively(root);
        }
    }

    /**
     * Recursively expand a node and all its children.
     * Uses depth-first traversal to expand from top to bottom.
     */
    protected async expandNodeRecursively(node: TreeNode): Promise<void> {
        if (ExpandableTreeNode.is(node) && !node.expanded) {
            await this.treeWidget.model.expandNode(node);
        }
        if (CompositeTreeNode.is(node)) {
            for (const child of node.children) {
                await this.expandNodeRecursively(child);
            }
        }
    }

    /**
     * Collapse all tree nodes back to root level.
     * Uses Theia's built-in collapseAll method.
     */
    protected async collapseAll(): Promise<void> {
        const root = this.treeWidget.model.root;
        if (root && CompositeTreeNode.is(root)) {
            await this.treeWidget.model.collapseAll(root);
        }
    }

    /**
     * Handle double-click on a leaf node to add it to the active workflow.
     * Creates a synthetic mouse event and delegates to the workflow editor.
     */
    protected handleNodeDoubleClick(node: NodeExplorerLeafNode): void {
        if (node.nodeData) {
            const nodeRepoItem = {
                id: node.id,
                name: node.nodeData.title || node.id,
                nodeInfo: node.nodeData
            };
            const syntheticEvent = new MouseEvent('dblclick');
            this.workflowEditorWidgetFactory.activeWidget?.addNewNode(syntheticEvent, nodeRepoItem);
        }
    }

    /**
     * Handle drag end on a leaf node to add it to the active workflow.
     * Uses the actual mouse event position to place the node correctly.
     */
    protected handleNodeDragEnd(node: NodeExplorerLeafNode, mouseEvent: MouseEvent): void {
        if (node.nodeData) {
            console.log('[NodeExplorerWidget] handleNodeDragEnd at:', mouseEvent.clientX, mouseEvent.clientY);
            const nodeRepoItem = {
                id: node.id,
                name: node.nodeData.title || node.id,
                nodeInfo: node.nodeData
            };
            this.workflowEditorWidgetFactory.activeWidget?.addNewNode(mouseEvent, nodeRepoItem);
        }
    }

    protected override onActivateRequest(msg: Message): void {
        super.onActivateRequest(msg);
        this.treeWidget.activate();
    }

    protected override async onAfterAttach(msg: Message): Promise<void> {
        super.onAfterAttach(msg);
        // Initialize the tree with a root node and fetch initial data
        this.treeWidget.model.root = NodeExplorerRootNode.create();
        await this.treeWidget.model.refresh();
        // Ensure toolbar renders after the widget is attached to DOM
        this.toolbar.update();
    }
}
