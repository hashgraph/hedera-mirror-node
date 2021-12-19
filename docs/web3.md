# Web3 API

The Web3 API provides an implementation of existing JSON-RPC APIs for the Hedera network. JSON-RPC API is a widely used
standard for interacting with distributed ledgers. The aim in providing a Hedera implementation of these APIs is to ease
the migration of existing dApps to Hedera and simplify the developer on-ramp.

## Ethereum API

Currently, the Web3 module only provides a partial implementation of
the [Ethereum JSON-RPC API](https://eth.wiki/json-rpc/API).

## Technologies

This module uses [Spring Boot](https://spring.io/projects/spring-boot) for its application framework. To serve the
JSON-RPC APIs, [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
is used with annotation-based controllers. [Spring Data JPA](https://spring.io/projects/spring-data-jpa) with Hibernate
is used for the persistence layer.

## Acceptance Tests

The Web3 API uses [Postman](https://www.postman.com) tests to verify proper operation. The
[Newman](https://learning.postman.com/docs/running-collections/using-newman-cli/command-line-integration-with-newman)
command-line collection runner is used to execute the tests against a remote server. To use newman, either the
executable binary or Docker approach can be used. With either approach, a `baseUrl` variable can be supplied to
customize the target server.

### Executable

First ensure newman is installed locally using `npm`, then execute `newman`.

```shell
npm install -g newman
newman run hedera-mirror-web3/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

### Docker

```shell
docker run --rm -v "${PWD}/hedera-mirror-web3/postman.json:/tmp/postman.json" -t postman/newman run /tmp/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

_Note:_ To test against an instance running on the same machine as Docker use your local IP instead of 127.0.0.1.
