import React from 'react';
import { ReactNode } from "react";
import { Dialog, DialogProps, Message } from "@theia/core/lib/browser";
import { ReactDialog } from "@theia/core/lib/browser/dialogs/react-dialog";
import { inject, injectable, postConstruct } from '@theia/core/shared/inversify';

// export const ContinuumNodeDialogProps = Symbol("ContinuumNodeDialogProps");
@injectable()
export class ContinuumNodeDialogProps extends DialogProps {

}

@injectable()
export default class ContinuumNodeDialog extends ReactDialog<void> {
    
    constructor(
        @inject(ContinuumNodeDialogProps)
        protected override readonly props: ContinuumNodeDialogProps
    ) {
        super({
            title: "Node Dialog"
        });
        this.appendAcceptButton(Dialog.OK);
        console.log("Constructing Node dialog!")
    }

    @postConstruct()
    protected init(): void {
        this.update();
    }

    protected override onAfterAttach(msg: Message): void {
        super.onAfterAttach(msg);
        this.update();
        console.log("onAfterAttach Dialog");
    }

    protected render(): ReactNode {
        return (
            <div>
                Node Dialog
            </div>
        );
    }

    get value(): undefined { return undefined; }
}