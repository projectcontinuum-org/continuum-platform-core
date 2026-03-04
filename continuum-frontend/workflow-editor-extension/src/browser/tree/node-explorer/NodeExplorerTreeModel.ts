import { TreeModelImpl, TreeModel } from '@theia/core/lib/browser/tree';
import { injectable } from '@theia/core/shared/inversify';

export const NodeExplorerTreeModel = Symbol('NodeExplorerTreeModel');
export type NodeExplorerTreeModel = TreeModel;

/**
 * Tree model for Node Explorer.
 * Extends TreeModelImpl with any custom behavior needed.
 */
@injectable()
export class NodeExplorerTreeModelImpl extends TreeModelImpl {
    // Can override methods here if needed for custom behavior
    // e.g., custom selection handling, custom expansion behavior, etc.
}
