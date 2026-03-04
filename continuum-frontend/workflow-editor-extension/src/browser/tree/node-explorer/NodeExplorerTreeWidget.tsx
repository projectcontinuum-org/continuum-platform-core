import * as React from 'react';
import SVG from 'react-inlinesvg';
import { TreeWidget, TreeProps, TreeNode, NodeProps, TreeModel } from '@theia/core/lib/browser/tree';
import { ContextMenuRenderer } from '@theia/core/lib/browser';
import { injectable, inject, postConstruct } from '@theia/core/shared/inversify';
import { NodeExplorerCategoryNode, NodeExplorerLeafNode, NodeExplorerNode } from './NodeExplorerTree';
import { Emitter, Event } from '@theia/core/lib/common';
import { useNodeDragStore } from '../../store/NodeDragStore';

export const NODE_EXPLORER_CONTEXT_MENU: string[] = ['node-explorer-context-menu'];

/**
 * Event data for drag end events
 */
export interface NodeDragEndEvent {
    node: NodeExplorerLeafNode;
    mouseEvent: MouseEvent;
}

/**
 * Tree widget for Node Explorer.
 * Provides custom rendering, double-click handling, and auto-filtering on search.
 */
@injectable()
export class NodeExplorerTreeWidget extends TreeWidget {

    static readonly ID = 'node-explorer-tree-widget';
    static readonly LABEL = 'Node Explorer';

    protected readonly onNodeDoubleClickEmitter = new Emitter<NodeExplorerLeafNode>();
    readonly onNodeDoubleClick: Event<NodeExplorerLeafNode> = this.onNodeDoubleClickEmitter.event;

    protected readonly onNodeDragEndEmitter = new Emitter<NodeDragEndEvent>();
    readonly onNodeDragEnd: Event<NodeDragEndEvent> = this.onNodeDragEndEmitter.event;

    constructor(
        @inject(TreeProps) props: TreeProps,
        @inject(TreeModel) override readonly model: TreeModel,
        @inject(ContextMenuRenderer) contextMenuRenderer: ContextMenuRenderer
    ) {
        super(props, model, contextMenuRenderer);
    }

    @postConstruct()
    protected override init(): void {
        super.init();
        this.id = NodeExplorerTreeWidget.ID;
        this.title.label = NodeExplorerTreeWidget.LABEL;
        this.title.caption = NodeExplorerTreeWidget.LABEL;
        this.title.iconClass = 'fa fa-folder-tree';
        this.title.closable = false;

        // Add custom styles for larger tree items
        this.addClass('node-explorer-tree');

        // Auto-enable filtering when search text changes
        if (this.searchBox) {
            this.toDispose.push(
                this.searchBox.onTextChange(data => {
                    // Enable filtering when there's text, disable when empty
                    const shouldFilter = !!data && data.trim().length > 0;
                    if (this.searchBox && this.searchBox.isFiltering !== shouldFilter) {
                        // Access the protected method to toggle filtering
                        (this.searchBox as any).doFireFilterToggle(shouldFilter);
                    }
                })
            );
        }
    }

    protected override createContainerAttributes(): React.HTMLAttributes<HTMLElement> {
        const attrs = super.createContainerAttributes();
        return {
            ...attrs,
            style: {
                ...attrs.style as React.CSSProperties,
                fontSize: '14px',
                lineHeight: '32px'
            }
        };
    }

    protected override createNodeAttributes(node: TreeNode, props: NodeProps): React.Attributes & React.HTMLAttributes<HTMLElement> {
        const attributes = super.createNodeAttributes(node, props);
        const baseStyle: React.CSSProperties = {
            minHeight: '32px',
            lineHeight: '32px'
        };

        if (NodeExplorerLeafNode.is(node)) {
            return {
                ...attributes,
                style: { ...attributes.style as React.CSSProperties, ...baseStyle },
                onMouseDown: (e: React.MouseEvent) => this.handleMouseDown(e, node)
            };
        }

        return {
            ...attributes,
            style: { ...attributes.style as React.CSSProperties, ...baseStyle }
        };
    }

    /**
     * Custom icon rendering based on node type
     */
    protected override renderIcon(node: TreeNode, props: NodeProps): React.ReactNode {
        if (NodeExplorerLeafNode.is(node)) {
            const iconSvg = node.nodeData?.icon?.trim();
            if (iconSvg && iconSvg.toLowerCase().startsWith('<svg')) {
                return (
                    <span className="theia-tree-icon" style={{ marginRight: '8px', display: 'inline-flex', width: '20px', height: '20px', alignItems: 'center' }}>
                        <SVG src={iconSvg} width={20} height={20} />
                    </span>
                );
            }
            return <span className="theia-tree-icon fa fa-cube" style={{ marginRight: '8px', fontSize: '16px' }}></span>;
        }

        if (NodeExplorerCategoryNode.is(node)) {
            const iconClass = node.expanded ? 'fa fa-folder-open' : 'fa fa-folder';
            return <span className={`theia-tree-icon ${iconClass}`} style={{ marginRight: '8px', fontSize: '16px' }}></span>;
        }

        return super.renderIcon(node, props);
    }

    /**
     * Custom caption rendering
     */
    protected override getCaptionChildren(node: TreeNode, props: NodeProps): React.ReactNode {
        if (NodeExplorerNode.is(node)) {
            return node.name;
        }
        return super.getCaptionChildren(node, props);
    }

    /**
     * Handle double-click on nodes
     */
    protected override handleDblClickEvent(node: TreeNode | undefined, event: React.MouseEvent<HTMLElement>): void {
        super.handleDblClickEvent(node, event);

        if (NodeExplorerLeafNode.is(node)) {
            // Emit event for leaf node double-click (to add to workflow)
            this.onNodeDoubleClickEmitter.fire(node);
        }
    }

    /**
     * Handle mouse down - start custom drag tracking
     */
    protected handleMouseDown(event: React.MouseEvent, node: NodeExplorerLeafNode): void {
        // Only handle left mouse button
        if (event.button !== 0) return;

        const startX = event.clientX;
        const startY = event.clientY;
        let isDragging = false;
        const dragThreshold = 5;

        const handleMouseMove = (e: MouseEvent) => {
            const deltaX = Math.abs(e.clientX - startX);
            const deltaY = Math.abs(e.clientY - startY);

            if (!isDragging && (deltaX > dragThreshold || deltaY > dragThreshold)) {
                isDragging = true;
                console.log('[NodeExplorerTreeWidget] Drag start:', node.nodeData);
                if (node.nodeData) {
                    useNodeDragStore.getState().startDrag(node.nodeData);
                }
            }

            if (isDragging) {
                useNodeDragStore.getState().updatePosition(e.clientX, e.clientY);
            }
        };

        const handleMouseUp = (e: MouseEvent) => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);

            if (isDragging) {
                console.log('[NodeExplorerTreeWidget] Drag end at:', e.clientX, e.clientY);
                useNodeDragStore.getState().endDrag();
                this.onNodeDragEndEmitter.fire({
                    node,
                    mouseEvent: e
                });
            }
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }

    dispose(): void {
        this.onNodeDoubleClickEmitter.dispose();
        this.onNodeDragEndEmitter.dispose();
        super.dispose();
    }
}
