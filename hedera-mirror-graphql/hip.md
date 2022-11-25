---
hip: TBD
title: Mirror Node GraphQL API
author: Steven Sheehy <@steven-sheehy>
working-group: Daniel Costa
type: Standards Track
category: Mirror
needs-council-approval: Yes
status: Draft
created: 2022-11-29
discussions-to: https://github.com/hashgraph/hedera-improvement-proposal/discussions/TBD
---

## Abstract

Please provide a short (~200 word) description of the issue being addressed.

## Motivation

The motivation is critical for HIPs that want to change the Hedera codebase or ecosystem. It should clearly explain why
the existing specification is inadequate to address the problem that the HIP solves. HIP submissions without sufficient
motivation may be rejected outright.

## Rationale

The rationale fleshes out the specification by describing why particular design decisions were made. It should describe
alternate designs that were considered and related work, e.g. how the feature is supported in other languages.

The rationale should provide evidence of consensus within the community and discuss important objections or concerns
raised during the discussion.

## User stories

1) As a user, I want to request only the exact data that I need.

GraphQL allows users to select only the fields they care about.

## Specification

## Backwards Compatibility

This HIP introduces a new mirror node API and as such introduces no backwards compatibility concerns. The existing
mirror node REST API will continue to be maintained.

## Security Implications

A new API can increase the load on the database and will have to be architected in such a way as to be scalable via the
normal techniques like caching, data replication, horizontal auto-scaling, etc. In contract to REST APIs, users can
request significantly more data in a single request since they control the response format. As a result, the mirror node
GraphQL implementation will enforce limits in a number of areas to ensure any single query doesn't negatively impact
other users.

Every list must contain

## How to Teach This

The new GraphQL API will be developed in a contract-first manner using standard GraphQL schema files. These schema files
will contain extensive comments for every operation, type, and field present to document their intended purpose. Some
mirror node implementations may choose to run a GraphiQL explorer embedded alongside the API for easy exploration of the
available queries. Additional documentation will be provided by Hedera to instruct users how to use the new API.

## Reference Implementation

A reference implementation is underway on the Mirror Node GitHub repository. The results of this effort will manifest
themselves as a new `hedera-mirror-graphql` module and associated microservice. GitHub issues labeled
with [graphql](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aissue+is%3Aopen+graphql+label%3Agraphql)
can be used by interested parties to track the progression of this work.

## Rejected Ideas

### REST API Projections

The current REST API could be enhanced to provide some benefits of GraphQL like field selection (projection) or
nested pagination. Field selection would take a some amount of effort to rewrite queries to have dynamic select
statements. Problems arise when attempting to design a manner to specify via query parameters what fields to return when
those fields are on nested objects. There is no standard syntax in REST API for projection. Likewise, nested pagination

### GraphQL Mutations

Mirror nodes are read only and as a result users can't modify their state via a GraphQL mutation.

### GraphQL Subscriptions

Mirror nodes do eventually want to support subscriptions to deliver real-time updates, but to reduce the scope of this
HIP we have intentionally excluded it at this time. We will revisit support for subscriptions at a later point in time
once the mirror node team finalizes some data architecture

## Open Issues

## References

* [Mirror Node GitHub](https://github.com/hashgraph/hedera-mirror-node)
* [REST OpenAPI Documentation](https://mainnet-public.mirrornode.hedera.com/api/v1/docs)
* [GraphQL Specification](https://spec.graphql.org/)
* [GraphQL Relay Specification](https://relay.dev/docs/guides/graphql-server-specification/)
* [GraphiQL](https://github.com/graphql/graphiql)

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE)
or (https://www.apache.org/licenses/LICENSE-2.0)
