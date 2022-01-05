# Rosetta API

## Overview

The Rosetta API is a REST API complying with
the [Rosetta API Specification](https://www.rosetta-api.org/docs/welcome.html) with a focus on blockchain data
integration. This server enables exchanges to be able to integrate and work with the Hedera Hashgraph network. The
server is written in Golang and is largely based on the [rosetta-sdk-go](https://github.com/coinbase/rosetta-sdk-go).
Its main job is to respond to Rosetta requests, to the extent allowed by Hedera, while fetching information from the
mirror node database.

## Architecture

![Architecture](rosetta-architecture.png)

The rosetta-sdk-go takes care of a significant part of the entity model definitions and API work. The Rosetta API server
has four main components:

### Domain models

These are models internal to the system allowing for safe and easy serialization and passing off information. These are
ultimately converted to/from Rosetta models or are marshaled from database records.

### Domain Repositories

These are repositories used for fetching data from the mirror node database and marshaling it into the domain models.
They provide an abstraction from the persistence layer and allow the services to request the necessary data.

### Business Logic Services

These services execute business logic in response to requests from client applications. They make use of the
repositories to gather the necessary domain models, convert them to the rosetta types, and return them back to the
client.

### Rosetta API Controllers

These are structures coming out of the box with rosetta-sdk-go. These handle the raw requests, marshaling/unmarshaling
the data, and triggering the business logic services.

## Acceptance Tests

The Rosetta API uses [Postman](https://www.postman.com) tests to verify proper operation. The
[Newman](https://learning.postman.com/docs/running-collections/using-newman-cli/command-line-integration-with-newman)
command-line collection runner is used to execute the tests against a remote server. To use newman, either the
executable binary or Docker approach can be used. With either approach, a `base_url` variable can be supplied to
customize the target server.

### Executable

First ensure newman is installed locally using `npm`, then execute `newman`.

```shell
npm install -g newman
newman run hedera-mirror-rosetta/scripts/validation/postman/rosetta-api-postman.json --env-var base_url=https://previewnet.mirrornode.hedera.com/rosetta
```

### Docker

```shell
docker run --rm -v "${PWD}/hedera-mirror-rosetta/scripts/validation/postman/rosetta-api-postman.json:/tmp/postman.json" -t postman/newman run /tmp/postman.json --env-var base_url=https://previewnet.mirrornode.hedera.com/rosetta
```

_Note:_ To test against an instance running on the same machine as Docker use your local IP instead of 127.0.0.1.
