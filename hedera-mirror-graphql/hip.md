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

Defines a new GraphQL API for the Hedera Mirror Node.

## Motivation

The current mirror node REST API provides a functional API that returns most of the data users need. However, due to the
nature of REST APIs, it suffers from problems inherit with REST including both under-fetching and over-fetching.

Under-fetching can occur when the user does one query to fetch a list of items, then for each item in the list does
another query to get nested data. For example, if you use the REST API to query for a list of accounts and then get each
account's list of NFTs. REST APIs don't allow the user to define the nested or related data to return everything in one
response and avoid multiple round trips to the server. Similarly, REST APIs don't support standard ways of doing nested
pagination that would allow for such query patterns.

Over-fetching can occur when all fields are returned in the REST response. The user should be able to
select which fields they care about and the API should return only those. Some REST APIs allow users to supply a
list of fields to return which works fine for top-level fields but breaks down when attempted for nested fields or
lists.

GraphQL is an API query language developed by Facebook (Meta) and now widely accepted as a popular alternative to REST.
It can help alleviate some of the above problems and the development of such an API has been requested numerous times
from internal and external members of the Hedera community.

## Rationale

An existing GraphQL API for the Hedera network was created by community members under the name
[Hgraph.io](https://www.hgraph.io/). This API uses the open source Hedera mirror node code base as its source of
information. We decided not to base the GraphQL schema in this HIP off of Hgraph's schema for a number of reasons. For
one, Hgraph is not open source, so we could not leverage its codebase as a starting point. Secondly, the GraphQL schema
it defines is almost a one-to-one mapping of the database schema without much, if any, nesting of data. This negates one
of the main benefits of GraphQL by not taking advantage of nested pagination and allowing the client to retrieve larger
graphs of data. The open source mirror node does not provide any guarantees of database schema compatibility between
releases so this schema will become harder to maintain over time without the use of a DTO mapping layer.

[The Graph](https://thegraph.com/) provides an indexing protocol for networks and allows anyone to publish sub-graphs
for their subset of data. Hedera could write an indexer to integrate with The Graph, thus exposing Hedera data for use
in sub-graphs. While The Graph does use GraphQL for its subgraph, it differs from the intent of the API in this HIP. The
Graph can only index individual smart contracts and their storage allowing for rich sub-graphs that are specific to that
specific contract. It cannot be used to expose the numerous non-contract related entities in Hedera like accounts,
tokens, etc. Thus, it cannot be used as a general purpose mirror node API.

## User stories

1) As a dApp developer, I want to request only the exact data that I need.
2) As a dApp developer, I want to avoid multiple requests to the API to get my data quicker.
3) As a JSON-RPC relay operator, I want to reduce maintenance costs and improve latency.
4) As an explorer, I want to reduce page load times.

## Specification

The new GraphQL API will be written as a new module in the open source Hedera Mirror Node repository. It will be Java
based to allow for code reuse with the other modules. It will interact directly with the SQL database like the other
modules. Creating it as separate module will allow it to be a microservice that can be scaled separately from other
components to auto-scale based upon load. The API will be defined in a contract-first manner using the GraphQL schema
files defined in the following sections.

### Queries

The current REST API has two broad top-level query patterns: list all entities/transactions or get a specific
entity/transaction (or data associated with it). Based upon metrics, the latter scenario is more common since dApps
will generally want to look up their specific account, topic, transaction, etc. Listing all accounts or other top
level resources makes more sense for narrower use cases like explorers or analytics.

```graphql

```

### Common

Below are the directives, enums, inputs, interfaces, scalars, and types that are common to more than one of the
queries.

```graphql

```

### Account

```graphql

```

### Block

```graphql

```

### Contract

```graphql

```

### File

```graphql

```

### Network

```graphql

```

### Schedule

```graphql

```

### Token

```graphql

```

### Topic

```graphql

```

### Transaction

```graphql

```

## Backwards Compatibility

This HIP introduces a new mirror node API and as such introduces no backwards compatibility concerns. The existing
mirror node REST API will continue to be maintained.

## Security Implications

A new API can increase the load on the database and will have to be architected in such a way as to be scalable via the
normal techniques like caching, data replication, horizontal auto-scaling, etc. In contrast to REST APIs, users can
request significantly more data in a single request since they control the response format. As a result, the mirror node
GraphQL implementation will enforce limits in a number of areas to ensure any single query doesn't negatively impact
other users. Different mirror node operators may choose to decrease or increase these limits to suit their needs.

### Query Timeout

A maximum time to process a GraphQL query will be enforced via a query timeout. The exact query timeout will be
determined based upon performance testing.

### Query Depth Limit

Query depth will be limited to at most 3 levels so that calls cannot recurse infinitely and overwhelm the server. The
exact query depth will be determined based upon performance testing.

### List Size Limit

Limiting each list is necessary to reduce the multiplier of having nested queries. Any list will be limited to at
most 100 items. Each of those list could potentially contain other lists who are each similarly constrained to 100 items
and so on.

### Query Complexity Limit

A query complexity score will be calculated from the query and any request that exceeds the maximum complexity score
will be rejected. The complexity of a query is calculated by summing up the number of requested fields multiplied by the
number of items requested in the list. The exact complexity limit will be determined based upon performance testing.

### IP Rate Limit

An IP-based rate limit will be enforced at the load balancer level to ensure any one client does not overwhelm the
API endpoint. Since a single GraphQL call might be the equivalent of many REST API calls, this limit might be lower
than the REST API limit. The exact rate limit will be determined based upon performance testing.

### Complexity Rate Limit

The complexity score used to calculate the per query limit could also be used to keep a running total of a client's
API use during a period of time. Mirror node operators may choose to monitor and use these points to limit the maximum
number of points a client can use per hour.

## How to Teach This

The new GraphQL API will be developed in a contract-first manner using standard GraphQL schema files. These schema files
will contain extensive comments for every operation, type, and field present to document their intended purpose. Some
mirror node implementations may choose to run a GraphiQL explorer embedded alongside the API for easy exploration of the
available queries. Additional documentation will be provided by Hedera to instruct users how to use the new API.

## Reference Implementation

A reference implementation is underway on the Mirror Node GitHub repository. The results of this effort will manifest
themselves as a new `hedera-mirror-graphql` module and associated microservice. GitHub issues labeled
with [graphql](https://github.com/hashgraph/hedera-mirror-node/issues?q=is%3Aissue+is%3Aopen+graphql+label%3Agraphql)
can be used by interested parties to track the progress of this work.

## Rejected Ideas

### REST API Projections

The current REST API could be enhanced to provide some benefits of GraphQL like field selection (projection) or
nested pagination. Field selection would take a some amount of effort to rewrite queries to have dynamic select
statements. Problems arise when attempting to design a manner to specify via query parameters what fields to return when
those fields are on nested objects. There is no standard syntax in REST API for projection. Likewise, nested pagination
is tricky to support since there is no concept of parameters on nested lists to support limit, order and cursor.

### GraphQL Mutations

Mirror nodes are read only and as a result users can't modify their state via a GraphQL mutation.

### GraphQL Subscriptions

Mirror nodes do eventually want to support subscriptions to deliver real-time updates, but to reduce the scope of this
HIP we have intentionally excluded it at this time. We will revisit support for subscriptions at a later point in time
once the mirror node team finalizes the scalability of its data architecture.

## Open Issues

## References

* [Mirror Node GitHub](https://github.com/hashgraph/hedera-mirror-node)
* [REST OpenAPI Documentation](https://mainnet-public.mirrornode.hedera.com/api/v1/docs)
* [GraphQL Specification](https://spec.graphql.org/)
* [GraphQL Relay Specification](https://relay.dev/docs/guides/graphql-server-specification/)
* [GraphiQL](https://github.com/graphql/graphiql)
* [Hgraph.io](https://www.hgraph.io/)

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE)
or (https://www.apache.org/licenses/LICENSE-2.0)
