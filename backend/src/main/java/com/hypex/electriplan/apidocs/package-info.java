/**
 * The API's own description: the OpenAPI document at /api/docs/openapi.json
 * and Swagger UI at /api/docs. springdoc builds it from the controllers; this
 * module adds what springdoc cannot see on its own (the sign-in scheme, which
 * record fields are required or may be null). The tenancy module documents
 * its company header and permissions itself.
 *
 * <p>contracts/openapi.json is a copy of the document, kept current by
 * OpenApiContractTest; the web app's TypeScript types are generated from it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "API documentation")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.apidocs;
