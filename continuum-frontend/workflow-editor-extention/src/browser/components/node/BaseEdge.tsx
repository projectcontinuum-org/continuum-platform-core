import * as React from "react";
import { EdgeProps, getBezierPath } from "reactflow";
import "./BaseEdge.css"

export default class BaseEdge extends React.Component {
    private xEqual = this.props.sourceX === this.props.targetX;
    private yEqual = this.props.sourceY === this.props.targetY;
    private edgePath: any;

    constructor(public props: EdgeProps) {
        super(props);
    }

    render(): React.ReactNode {
        [this.edgePath] = getBezierPath({
            // we need this little hack in order to display the gradient for a straight line
            sourceX: this.xEqual ? this.props.sourceX + 0.0001 : this.props.sourceX,
            sourceY: this.yEqual ? this.props.sourceY + 0.0001 : this.props.sourceY,
            sourcePosition: this.props.sourcePosition,
            targetX: this.props.targetX,
            targetY: this.props.targetY,
            targetPosition: this.props.targetPosition
        });
        return (
            <>
                <path
                    id={this.props.id}
                    style={this.props.style}
                    className="react-flow__edge-path"
                    d={this.edgePath}
                    markerStart={this.props.markerStart}
                    markerEnd={this.props.markerEnd}/>
            </>
        );
    }
}
