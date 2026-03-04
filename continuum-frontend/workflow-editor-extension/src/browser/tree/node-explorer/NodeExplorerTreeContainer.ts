/**
 * NodeExplorerTreeContainer - Dependency Injection setup for the Node Explorer tree
 *
 * ## Overview
 * This file configures the Inversify DI container for all Node Explorer tree components.
 * Theia's tree infrastructure uses dependency injection extensively, and this container
 * wires together all the custom implementations we provide.
 *
 * ## Why We Need a Custom Container
 * Theia provides `createTreeContainer()` which sets up default bindings for:
 * - Tree (data structure)
 * - TreeModel (business logic)
 * - TreeWidget (UI rendering)
 * - TreeSearch (filtering)
 * - And more...
 *
 * We need to override several of these with our custom implementations:
 * - `NodeExplorerTree` - Custom tree with lazy loading from backend API
 * - `NodeExplorerTreeWidget` - Custom rendering with icons and drag-and-drop
 * - `NodeExplorerTreeSearch` - Custom search that calls backend API (see NodeExplorerTreeSearch.ts)
 *
 * ## Container Hierarchy
 * The container structure is:
 * ```
 * parent (application container)
 *   └── intermediate (binds NodeExplorerService)
 *         └── child (tree container from createTreeContainer)
 * ```
 *
 * We need the intermediate container because:
 * 1. `NodeExplorerTreeSearch` depends on `NodeExplorerService` (for backend API calls)
 * 2. `createTreeContainer` creates bindings for TreeSearch DURING its execution
 * 3. The service must be available BEFORE createTreeContainer is called
 * 4. Binding it in the child container AFTER would be too late
 *
 * ## Tree Properties Explained
 * - `virtualized: true` - Only render visible rows (performance for large trees)
 * - `search: true` - Enable the search box in the tree widget
 * - `multiSelect: false` - Single selection only
 * - `globalSelection: false` - Don't sync with global IDE selection
 *
 * @see NodeExplorerWidget for the main widget that uses this tree
 * @see NodeExplorerTreeSearch for custom search implementation details
 */

import { Container, interfaces } from '@theia/core/shared/inversify';
import { createTreeContainer, TreeProps, defaultTreeProps, TreeModel } from '@theia/core/lib/browser/tree';
import { NodeExplorerTree } from './NodeExplorerTree';
import { NodeExplorerTreeModel, NodeExplorerTreeModelImpl } from './NodeExplorerTreeModel';
import { NodeExplorerTreeWidget, NODE_EXPLORER_CONTEXT_MENU } from './NodeExplorerTreeWidget';
import { NodeExplorerTreeSearch } from './NodeExplorerTreeSearch';
import NodeExplorerService from '../../service/NodeExplorerService';

/**
 * Create and configure the DI container for the Node Explorer tree.
 *
 * This function is called by the NodeExplorerContribution when creating
 * the tree widget instance.
 *
 * @param parent - The parent container (typically the application's main container)
 * @returns A configured container with all Node Explorer tree bindings
 */
export function createNodeExplorerTreeContainer(parent: interfaces.Container): Container {
    // Create an intermediate container to bind NodeExplorerService FIRST.
    // This is critical because NodeExplorerTreeSearch (our custom search) needs
    // to inject NodeExplorerService, and the binding must exist before
    // createTreeContainer() creates the TreeSearch binding.
    const intermediate = new Container({ defaultScope: 'Singleton' });
    intermediate.parent = parent;

    // Bind the backend API service if not already bound
    if (!intermediate.isBound(NodeExplorerService)) {
        intermediate.bind(NodeExplorerService).toSelf().inSingletonScope();
    }

    // Create the tree container with our custom implementations.
    // Theia's createTreeContainer sets up all the standard tree bindings
    // and allows us to override specific implementations.
    const child = createTreeContainer(intermediate, {
        // Custom tree data structure with lazy loading
        tree: NodeExplorerTree,
        // Custom model (currently uses default behavior, but can be extended)
        model: NodeExplorerTreeModelImpl,
        // Custom widget with icons, drag-and-drop, and larger items
        widget: NodeExplorerTreeWidget,
        // Custom search that calls backend API instead of local filtering
        // This is the key override that makes search work with lazy-loaded trees!
        search: NodeExplorerTreeSearch,
        // Tree configuration properties
        props: {
            ...defaultTreeProps,
            // Context menu for right-click actions
            contextMenuPath: NODE_EXPLORER_CONTEXT_MENU,
            // Virtualized rendering for performance
            virtualized: true,
            // Enable the search box
            search: true,
            // Single selection mode
            multiSelect: false,
            // Don't sync with global IDE selection
            globalSelection: false
        } as TreeProps
    });

    // Bind our custom model symbol to the TreeModel service
    // This allows other code to inject NodeExplorerTreeModel if needed
    child.bind(NodeExplorerTreeModel).toService(TreeModel);

    return child;
}

/**
 * Symbols for dependency injection.
 * These can be used to request specific Node Explorer tree components.
 */
export const NodeExplorerTreeSymbols = {
    Tree: Symbol('NodeExplorerTree'),
    Model: NodeExplorerTreeModel,
    Widget: Symbol('NodeExplorerTreeWidget')
};
