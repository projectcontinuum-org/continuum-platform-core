import { Container, interfaces } from '@theia/core/shared/inversify';
import { createTreeContainer, TreeProps, defaultTreeProps, TreeModel } from '@theia/core/lib/browser/tree';
import { NodeExplorerTree } from './NodeExplorerTree';
import { NodeExplorerTreeModel, NodeExplorerTreeModelImpl } from './NodeExplorerTreeModel';
import { NodeExplorerTreeWidget, NODE_EXPLORER_CONTEXT_MENU } from './NodeExplorerTreeWidget';
import NodeExplorerService from '../../service/NodeExplorerService';

/**
 * Create a container for the Node Explorer tree widget.
 * This sets up all the necessary bindings for the Theia tree infrastructure.
 */
export function createNodeExplorerTreeContainer(parent: interfaces.Container): Container {
    const child = createTreeContainer(parent, {
        tree: NodeExplorerTree,
        model: NodeExplorerTreeModelImpl,
        widget: NodeExplorerTreeWidget,
        props: {
            ...defaultTreeProps,
            contextMenuPath: NODE_EXPLORER_CONTEXT_MENU,
            virtualized: true,
            search: true,
            multiSelect: false,
            globalSelection: false
        } as TreeProps
    });

    // Bind the NodeExplorerService if not already bound in parent
    if (!child.isBound(NodeExplorerService)) {
        child.bind(NodeExplorerService).toSelf().inSingletonScope();
    }

    // Bind the custom model symbol
    child.bind(NodeExplorerTreeModel).toService(TreeModel);

    return child;
}

/**
 * Symbols for dependency injection
 */
export const NodeExplorerTreeSymbols = {
    Tree: Symbol('NodeExplorerTree'),
    Model: NodeExplorerTreeModel,
    Widget: Symbol('NodeExplorerTreeWidget')
};
