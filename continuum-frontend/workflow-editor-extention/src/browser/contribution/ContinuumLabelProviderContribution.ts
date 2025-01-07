import "../icons/fa-import.css";

import { FileStatNode, FileTreeLabelProvider } from "@theia/filesystem/lib/browser/file-tree"

export default class ContinuumFileTreeLabelProviderContribution extends FileTreeLabelProvider {

    canHandle(element: object): number {
        if (FileStatNode.is(element)) {
            let uri = element.uri;
            if (uri.path.ext === '.cwf') {
                return super.canHandle(element)+1;
            }
        }
        return 0;
    }

    getIcon(node: FileStatNode): string {
        return "continuum continuum-file workflow-file-icon fa fa-solid fa-diagram-project";
    }

}
