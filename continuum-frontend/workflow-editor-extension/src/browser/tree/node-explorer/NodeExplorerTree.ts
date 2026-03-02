import { TreeImpl, CompositeTreeNode, TreeNode } from '@theia/core/lib/browser/tree';
import { ExpandableTreeNode } from '@theia/core/lib/browser/tree/tree-expansion';
import { SelectableTreeNode } from '@theia/core/lib/browser/tree/tree-selection';
import { injectable, inject } from '@theia/core/shared/inversify';
import { IBaseNodeData, INodeExplorerTreeItem } from '@continuum/core';
import NodeExplorerService from '../../service/NodeExplorerService';

/**
 * Custom tree node for category (folder) nodes
 */
export interface NodeExplorerCategoryNode extends ExpandableTreeNode, SelectableTreeNode {
    nodeType: 'category';
    nodeData?: undefined;
}

export namespace NodeExplorerCategoryNode {
    export function is(node: TreeNode | undefined): node is NodeExplorerCategoryNode {
        return !!node && 'nodeType' in node && (node as NodeExplorerCategoryNode).nodeType === 'category';
    }
}

/**
 * Custom tree node for leaf (actual Continuum node) nodes
 */
export interface NodeExplorerLeafNode extends SelectableTreeNode {
    nodeType: 'node';
    nodeData: IBaseNodeData;
}

export namespace NodeExplorerLeafNode {
    export function is(node: TreeNode | undefined): node is NodeExplorerLeafNode {
        return !!node && 'nodeType' in node && (node as NodeExplorerLeafNode).nodeType === 'node';
    }
}

/**
 * Union type for all node explorer tree nodes
 */
export type NodeExplorerNode = NodeExplorerCategoryNode | NodeExplorerLeafNode;

export namespace NodeExplorerNode {
    export function is(node: TreeNode | undefined): node is NodeExplorerNode {
        return NodeExplorerCategoryNode.is(node) || NodeExplorerLeafNode.is(node);
    }
}

/**
 * Root node for the node explorer tree
 */
export interface NodeExplorerRootNode extends CompositeTreeNode {
    nodeType: 'root';
}

export namespace NodeExplorerRootNode {
    export function is(node: TreeNode | undefined): node is NodeExplorerRootNode {
        return !!node && 'nodeType' in node && (node as NodeExplorerRootNode).nodeType === 'root';
    }

    export function create(): NodeExplorerRootNode {
        return {
            id: 'node-explorer-root',
            name: 'Node Explorer',
            nodeType: 'root',
            visible: false,  // Root invisible, children appear at top level
            children: [],
            parent: undefined
        };
    }
}

/**
 * Tree data provider for Node Explorer.
 * Implements lazy loading via resolveChildren() method.
 */
@injectable()
export class NodeExplorerTree extends TreeImpl {

    @inject(NodeExplorerService)
    protected readonly nodeExplorerService!: NodeExplorerService;

    /**
     * Key method for lazy loading.
     * Called automatically by Theia when a node is expanded.
     */
    protected override async resolveChildren(parent: CompositeTreeNode): Promise<TreeNode[]> {
        if (NodeExplorerRootNode.is(parent) || NodeExplorerCategoryNode.is(parent)) {
            const parentId = NodeExplorerRootNode.is(parent) ? '' : parent.id;
            try {
                const items = await this.nodeExplorerService.getChildren(parentId);
                return items.map(item => this.toTreeNode(item, parent));
            } catch (error) {
                console.error('[NodeExplorerTree] Failed to load children for node:', parentId, error);
                return [];
            }
        }
        return [];
    }

    /**
     * Transform API response to Theia TreeNode
     */
    protected toTreeNode(item: INodeExplorerTreeItem, parent: CompositeTreeNode): NodeExplorerNode {
        if (item.type === 'CATEGORY') {
            const node: NodeExplorerCategoryNode = {
                id: item.id,
                name: item.name,
                parent,
                visible: true,
                nodeType: 'category',
                expanded: false,
                selected: false,
                children: []
            };
            return node;
        }

        const node: NodeExplorerLeafNode = {
            id: item.id,
            name: item.name,
            parent,
            visible: true,
            nodeType: 'node',
            selected: false,
            nodeData: item.nodeInfo!
        };
        return node;
    }
}
