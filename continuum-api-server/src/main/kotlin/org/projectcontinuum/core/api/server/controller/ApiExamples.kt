package org.projectcontinuum.core.api.server.controller

/**
 * Request and response examples shared by the controllers' OpenAPI annotations. The workflow uses two real nodes of
 * `continuum-feature-base` (Create Table → Text Normalizer) connected by one edge, so it validates against a platform
 * with that worker registered.
 */
object ApiExamples {

  const val WORKFLOW_ID = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10"
  const val RUN_ID = "0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77"
  const val SCHEDULE_ID = "1d0f3c52-7a3b-4c8e-9d4f-2b6a8e1f0c3d"
  const val CREATE_TABLE_NODE = "org.projectcontinuum.feature.base.analytics.node.CreateTableNodeModel"
  const val TEXT_NORMALIZER_NODE = "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel"

  const val TWO_NODE_WORKFLOW = """{
  "id": "a3f1c9e2b7d84f60a1c2e3d4f5a6b7c8d9e0f1a2b3c4d5e6f708192a3b4c5d6e",
  "name": "examples/text-cleanup.cwf",
  "active": false,
  "nodes": [
    {
      "id": "create",
      "type": "BaseNode",
      "position": { "x": 0, "y": 0 },
      "width": 250,
      "height": 120,
      "selected": false,
      "data": {
        "id": "org.projectcontinuum.feature.base.analytics.node.CreateTableNodeModel",
        "title": "Create Table",
        "description": "Creates a structured table from FreeMarker template configuration",
        "nodeModel": "org.projectcontinuum.feature.base.analytics.node.CreateTableNodeModel",
        "outputs": { "data": { "name": "output table", "contentType": "text/plain" } },
        "properties": { "jsonArrayString": "[{\"name\": \"  Alice \"}, {\"name\": \"BOB!\"}]" }
      }
    },
    {
      "id": "normalize",
      "type": "BaseNode",
      "position": { "x": 330, "y": 0 },
      "width": 250,
      "height": 120,
      "selected": false,
      "data": {
        "id": "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel",
        "title": "Text Normalizer",
        "description": "Normalizes text by trimming, lowercasing, and removing non-alphanumeric characters",
        "nodeModel": "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel",
        "inputs": { "data": { "name": "input table", "contentType": "text/plain" } },
        "outputs": { "data": { "name": "output table", "contentType": "text/plain" } },
        "properties": { "inputCol": "name", "outputCol": "clean_name" }
      }
    }
  ],
  "edges": [
    {
      "id": "reactflow__edge-createdata-normalizedata",
      "type": "BaseEdge",
      "source": "create",
      "sourceHandle": "data",
      "target": "normalize",
      "targetHandle": "data"
    }
  ]
}"""

  const val CREATE_SCHEDULE_REQUEST = """{
  "name": "examples/text-cleanup.cwf/nightly.schedule",
  "cronExpression": "0 2 * * *",
  "timeZone": "Europe/Berlin",
  "continuumWorkflowModel": $TWO_NODE_WORKFLOW
}"""

  const val START_RESPONSE = """{ "workflowId": "$WORKFLOW_ID", "warnings": [] }"""

  const val INVALID_WORKFLOW_PROBLEM = """{
  "type": "about:blank",
  "title": "Invalid workflow",
  "status": 400,
  "detail": "Invalid workflow: 1 error(s). Node 'normalize' (org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel): property 'outputCol' is required.",
  "instance": "/api/v1/workflow",
  "errors": [
    {
      "message": "Node 'normalize' (org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel): property 'outputCol' is required.",
      "node": "normalize",
      "property": "outputCol"
    }
  ],
  "warnings": []
}"""

  const val VALIDATION_RESPONSE_INVALID = """{
  "valid": false,
  "nodeCount": 2,
  "edgeCount": 1,
  "errors": [
    {
      "message": "Edge 'reactflow__edge-createdata-normalizedata' targets unknown input port 'table' of node 'normalize'.",
      "edge": "reactflow__edge-createdata-normalizedata"
    }
  ],
  "warnings": [ { "message": "Node 'normalize' has an input port 'data' with no incoming edge.", "node": "normalize" } ]
}"""

  const val WORKFLOW_NOT_FOUND_PROBLEM =
    """{"type":"about:blank","title":"Not Found","status":404,"detail":"Workflow not found","instance":"/api/v1/workflow/$WORKFLOW_ID/cancel"}"""

  const val SCHEDULE_NOT_FOUND_PROBLEM =
    """{"type":"about:blank","title":"Not Found","status":404,"detail":"Schedule not found","instance":"/api/v1/workflow/schedule/$SCHEDULE_ID"}"""
}
