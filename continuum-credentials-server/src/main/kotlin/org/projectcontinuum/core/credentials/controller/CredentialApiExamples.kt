package org.projectcontinuum.core.credentials.controller

/** Request and response examples shared by the credentials controllers' OpenAPI annotations. */
object CredentialApiExamples {

  const val CREATE_CREDENTIAL_REQUEST = """{
  "name": "aws-prod",
  "type": "GENERIC",
  "typeVersion": "1.0.0",
  "description": "Read-only access to the analytics bucket",
  "data": {
    "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
    "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    "region": "us-east-2"
  }
}"""

  const val CREDENTIAL_RESPONSE = """{
  "userId": "alice",
  "name": "aws-prod",
  "type": "GENERIC",
  "typeVersion": "1.0.0",
  "description": "Read-only access to the analytics bucket",
  "data": {
    "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
    "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    "region": "us-east-2"
  },
  "createdBy": "alice",
  "updatedBy": "alice",
  "createdAt": "2026-11-21T09:00:00Z",
  "updatedAt": "2026-11-21T09:00:00Z",
  "lastAccessedAt": null
}"""

  const val UPDATE_CREDENTIAL_REQUEST = """{
  "description": "Rotated on 2026-11-21",
  "data": {
    "accessKeyId": "AKIAI44QH8DHBEXAMPLE",
    "secretAccessKey": "je7MtGbClwBF/2Zp9Utk/h3yCo8nvbEXAMPLEKEY",
    "region": "us-east-2"
  }
}"""

  const val CREATE_TYPE_REQUEST = """{
  "type": "postgres",
  "version": "1.0.0",
  "schema": {
    "type": "object",
    "required": ["host", "username", "password"],
    "properties": {
      "host": { "type": "string" },
      "port": { "type": "string", "pattern": "^[0-9]+$" },
      "username": { "type": "string" },
      "password": { "type": "string" }
    },
    "additionalProperties": false
  },
  "uiSchema": {
    "type": "VerticalLayout",
    "elements": [
      { "type": "Control", "scope": "#/properties/host" },
      { "type": "Control", "scope": "#/properties/port" },
      { "type": "Control", "scope": "#/properties/username" },
      { "type": "Control", "scope": "#/properties/password", "options": { "format": "password" } }
    ]
  }
}"""

  const val CREDENTIAL_NOT_FOUND =
    """{"type":"about:blank","title":"Not Found","status":404,"detail":"Credential 'aws-prod' not found for user 'alice'","instance":"/api/v1/credentials/aws-prod","error":"Credential 'aws-prod' not found for user 'alice'"}"""

  const val TYPE_NOT_FOUND =
    """{"type":"about:blank","title":"Not Found","status":404,"detail":"Credential type 'postgres' version '2.0.0' does not exist","instance":"/api/v1/credentials","error":"Credential type 'postgres' version '2.0.0' does not exist"}"""

  const val CREDENTIAL_CONFLICT =
    """{"type":"about:blank","title":"Conflict","status":409,"detail":"Credential 'aws-prod' already exists for user 'alice'","instance":"/api/v1/credentials","error":"Credential 'aws-prod' already exists for user 'alice'"}"""

  const val VALIDATION_PROBLEM =
    """{"type":"about:blank","title":"Invalid request","status":400,"detail":"name: Name must contain only alphanumeric characters, hyphens, and underscores","instance":"/api/v1/credentials","error":"name: Name must contain only alphanumeric characters, hyphens, and underscores","fieldErrors":[{"field":"name","message":"Name must contain only alphanumeric characters, hyphens, and underscores"}]}"""

  const val SCHEMA_PROBLEM =
    """{"type":"about:blank","title":"Bad Request","status":400,"detail":"Credential data does not match the schema: $.password: is missing but it is required","instance":"/api/v1/credentials","error":"Credential data does not match the schema: $.password: is missing but it is required"}"""
}
