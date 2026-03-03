/**
 * NodeExplorerTreeSearch - Custom search handler for the Node Explorer tree
 *
 * ## Why We Need a Custom Search Handler
 *
 * Theia's default `TreeSearch` class (which we extend) has a fundamental limitation:
 * it only filters nodes that are ALREADY LOADED in the tree structure.
 *
 * The default implementation works like this:
 * 1. User types in search box → `filter(pattern)` is called
 * 2. TreeSearch iterates over all loaded tree nodes using `TopDownTreeIterator`
 * 3. It performs fuzzy matching on node labels
 * 4. Matching nodes are stored in `_filteredNodes`
 * 5. `passesFilters(node)` returns true only for matching nodes
 * 6. TreeWidget re-renders, hiding non-matching nodes
 *
 * The problem: Our tree uses LAZY LOADING. Category nodes start collapsed,
 * and their children are only fetched from the backend when the user expands them.
 * This means most nodes don't exist in the tree until their parent is expanded.
 *
 * Example: If the tree has:
 *   - Machine Learning (collapsed)
 *     - Training (not loaded yet)
 *       - Fine-tune Model (not loaded yet)
 *
 * And the user searches for "fine-tune", the default TreeSearch won't find it
 * because the "Fine-tune Model" node doesn't exist in the tree structure yet!
 *
 * ## Our Solution
 *
 * We override the `filter()` method to:
 * 1. Call the backend API `/api/v1/node-explorer/search` which searches ALL nodes
 * 2. The backend can search the entire node catalog, including nested nodes
 * 3. For each matching node, we extract its parent path (e.g., "ml/training/finetune" → ["ml", "ml/training"])
 * 4. We expand all parent categories to trigger lazy loading
 * 5. We track which node IDs should be visible in `_filteredNodesAndParents`
 * 6. `passesFilters()` returns true only for matching nodes and their parents
 *
 * ## Technical Notes
 *
 * - We use `(this as any)` to access protected/private fields from the base class
 *   because TypeScript doesn't allow direct access to parent's private members
 * - Node IDs use path-based format: "category/subcategory/nodename"
 *   This lets us extract parent paths by splitting on "/"
 * - The search is debounced by Theia's SearchBox component
 *
 * @see NodeExplorerWidget for the main widget documentation
 * @see NodeExplorerService for the backend API client
 */

import { inject, injectable } from '@theia/core/shared/inversify';
import { TreeNode, CompositeTreeNode, TreeSearch, ExpandableTreeNode } from '@theia/core/lib/browser/tree';
import NodeExplorerService from '../../service/NodeExplorerService';
import { NodeExplorerRootNode, NodeExplorerCategoryNode, NodeExplorerLeafNode } from './NodeExplorerTree';
import { INodeExplorerTreeItem } from '@continuum/core';

@injectable()
export class NodeExplorerTreeSearch extends TreeSearch {

    /** Service to call the backend search API */
    @inject(NodeExplorerService)
    protected readonly nodeExplorerService!: NodeExplorerService;

    /** Current search pattern, used by passesFilters() to know if filtering is active */
    protected _currentPattern: string = '';

    /**
     * Override the default filter method to call the backend search API.
     *
     * This is the core method that makes our search work with lazy-loaded trees.
     * Instead of filtering locally loaded nodes, we:
     * 1. Call the backend API to search ALL nodes
     * 2. Expand parent categories to make matching nodes visible
     * 3. Update the filter state so passesFilters() works correctly
     *
     * @param pattern - The search pattern entered by the user
     * @returns Array of matching tree nodes (used by Theia for navigation)
     */
    override async filter(pattern: string | undefined): Promise<ReadonlyArray<Readonly<TreeNode>>> {
        this._currentPattern = pattern || '';
        // Reset the filtered set (base class uses this for passesFilters)
        (this as any)._filteredNodesAndParents = new Set();

        // Empty pattern = show all nodes (no filtering)
        if (!pattern || !pattern.trim()) {
            (this as any)._filterResult = [];
            (this as any)._filteredNodes = [];
            (this as any).fireFilteredNodesChanged([]);
            return [];
        }

        try {
            // Call backend API to search all nodes (including not-yet-loaded ones)
            const searchResults = await this.nodeExplorerService.search(pattern);
            const root = this.tree.root;

            if (!root || !NodeExplorerRootNode.is(root)) {
                (this as any)._filteredNodes = [];
                (this as any).fireFilteredNodesChanged([]);
                return [];
            }

            // Build the set of node IDs that should be visible
            const filteredSet = (this as any)._filteredNodesAndParents as Set<string>;
            const parentPaths = new Set<string>();

            for (const item of searchResults) {
                // Add the matching node itself
                filteredSet.add(item.id);

                // Extract and add all parent paths so parent categories remain visible
                // e.g., "ml/training/finetune" → add "ml" and "ml/training"
                const paths = this.getParentPaths(item.id);
                paths.forEach(p => {
                    filteredSet.add(p);
                    parentPaths.add(p);
                });
            }

            // Expand parent categories to trigger lazy loading of their children
            // This ensures the matching nodes are actually in the tree structure
            await this.expandParentCategories(parentPaths);

            // Build result nodes for Theia's search navigation (prev/next)
            const nodes = searchResults.map(item => this.toTreeNode(item, root));

            // Update base class state and notify listeners
            (this as any)._filteredNodes = nodes;
            (this as any).fireFilteredNodesChanged(nodes);
            return nodes;
        } catch (error) {
            console.error('[NodeExplorerTreeSearch] Search failed:', error);
            (this as any)._filteredNodes = [];
            (this as any).fireFilteredNodesChanged([]);
            return [];
        }
    }

    /**
     * Extract all parent category IDs from a path-based node ID.
     *
     * Node IDs in our tree follow a path format: "category/subcategory/nodename"
     * This method extracts all parent paths so they can be included in the filter.
     *
     * @example
     * getParentPaths("ml/training/finetune")
     * // Returns: ["ml", "ml/training"]
     *
     * @param nodeId - The full path-based node ID
     * @returns Array of parent path IDs, from root to immediate parent
     */
    protected getParentPaths(nodeId: string): string[] {
        const parts = nodeId.split('/');
        const paths: string[] = [];
        let currentPath = '';
        for (let i = 0; i < parts.length - 1; i++) {
            currentPath = currentPath ? `${currentPath}/${parts[i]}` : parts[i];
            paths.push(currentPath);
        }
        return paths;
    }

    /**
     * Expand all parent categories to trigger lazy loading of their children.
     *
     * This is crucial for making search work with lazy-loaded trees:
     * - Categories must be expanded for their children to be loaded from backend
     * - We expand in order from shallowest to deepest to ensure proper loading
     * - After expansion, the matching nodes will exist in the tree structure
     *
     * @param parentPaths - Set of category IDs that need to be expanded
     */
    protected async expandParentCategories(parentPaths: Set<string>): Promise<void> {
        // Sort paths by depth (shorter paths first) to expand top-down
        // This ensures parent categories are expanded before their children
        const sortedPaths = Array.from(parentPaths).sort((a, b) =>
            a.split('/').length - b.split('/').length
        );

        for (const path of sortedPaths) {
            const node = this.findNodeById(path);
            if (node && ExpandableTreeNode.is(node) && !node.expanded) {
                // Mark as expanded and refresh to trigger lazy loading
                node.expanded = true;
                await this.tree.refresh(node);
            }
        }
    }

    /**
     * Find a node by ID using breadth-first search.
     *
     * @param id - The node ID to find
     * @returns The matching node, or undefined if not found
     */
    protected findNodeById(id: string): TreeNode | undefined {
        const root = this.tree.root;
        if (!root) return undefined;

        const queue: TreeNode[] = [root];
        while (queue.length > 0) {
            const current = queue.shift()!;
            if (current.id === id) {
                return current;
            }
            if (CompositeTreeNode.is(current)) {
                queue.push(...current.children);
            }
        }
        return undefined;
    }

    /**
     * Convert a backend search result item to a TreeNode.
     * Used for building the result list for Theia's search navigation.
     */
    protected toTreeNode(item: INodeExplorerTreeItem, parent: CompositeTreeNode): TreeNode {
        if (item.type === 'CATEGORY') {
            const node: NodeExplorerCategoryNode = {
                id: item.id,
                name: item.name,
                parent,
                visible: true,
                nodeType: 'category',
                expanded: true,
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

    /**
     * Determine if a node should be visible when filtering is active.
     *
     * This is called by Theia's TreeWidget for each node during rendering.
     * - When no pattern is set, all nodes are visible
     * - When filtering, only nodes in `_filteredNodesAndParents` are visible
     *
     * The set includes both matching nodes AND their parent categories,
     * so the tree structure remains intact (you can see the path to results).
     *
     * @param node - The tree node to check
     * @returns true if the node should be displayed, false to hide it
     */
    override passesFilters(node: TreeNode): boolean {
        // No active search = show all nodes
        if (!this._currentPattern) {
            return true;
        }
        // Check if this node ID is in our filtered set
        return (this as any)._filteredNodesAndParents.has(node.id);
    }
}
