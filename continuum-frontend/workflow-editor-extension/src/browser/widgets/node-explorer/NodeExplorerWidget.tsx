import { BaseWidget, Message, PanelLayout } from '@theia/core/lib/browser';
import { inject, injectable, postConstruct } from '@theia/core/shared/inversify';
import { Command } from '@theia/core';
import { NodeExplorerTreeWidget } from '../../tree/node-explorer/NodeExplorerTreeWidget';
import { NodeExplorerRootNode, NodeExplorerLeafNode } from '../../tree/node-explorer/NodeExplorerTree';
import WorkflowEditorWidgetFactory from '../workflow-editor/WorkflowEditorWidgetFactory';

@injectable()
export default class NodeExplorerWidget extends BaseWidget {
    static readonly ID = 'continuum-node-explorer:widget';
    static readonly LABEL = 'Node Explorer';
    static readonly COMMAND: Command = { id: 'node-explorer-widget:command' };

    @inject(NodeExplorerTreeWidget)
    protected readonly treeWidget!: NodeExplorerTreeWidget;

    @inject(WorkflowEditorWidgetFactory)
    protected readonly workflowEditorWidgetFactory!: WorkflowEditorWidgetFactory;

    @postConstruct()
    protected init(): void {
        this.id = NodeExplorerWidget.ID;
        this.title.label = NodeExplorerWidget.LABEL;
        this.title.caption = NodeExplorerWidget.LABEL;
        this.title.closable = false;
        this.title.iconClass = 'continuum continuum-widget fa fa-solid fa-folder-tree';

        // Make this widget fill available space
        this.node.style.display = 'flex';
        this.node.style.flexDirection = 'column';
        this.node.style.height = '100%';

        // Set up the layout with the tree widget
        const layout = new PanelLayout();
        layout.addWidget(this.treeWidget);
        this.layout = layout;

        // Make tree widget fill the parent
        this.treeWidget.node.style.flex = '1';
        this.treeWidget.node.style.height = '100%';

        // Listen for double-click events to add nodes to workflow
        this.treeWidget.onNodeDoubleClick(node => {
            this.handleNodeDoubleClick(node);
        });

        this.update();
    }

    protected handleNodeDoubleClick(node: NodeExplorerLeafNode): void {
        if (node.nodeData) {
            const nodeRepoItem = {
                id: node.id,
                name: node.nodeData.title || node.id,
                nodeInfo: node.nodeData
            };
            // Create a synthetic event for addNewNode
            const syntheticEvent = new MouseEvent('dblclick');
            this.workflowEditorWidgetFactory.activeWidget?.addNewNode(syntheticEvent, nodeRepoItem);
        }
    }

    protected override onActivateRequest(msg: Message): void {
        super.onActivateRequest(msg);
        this.treeWidget.activate();
    }

    protected override async onAfterAttach(msg: Message): Promise<void> {
        super.onAfterAttach(msg);
        this.treeWidget.model.root = NodeExplorerRootNode.create();
        await this.treeWidget.model.refresh();
    }
}
