# Web3 API

The Web3 API provides Java-based REST APIs for the mirror node.

## Contract Call

Currently, the Web3 module only provides a partial implementation of contract call.

## Technologies

This module uses [Spring Boot](https://spring.io/projects/spring-boot) for its application framework. To serve the
APIs, [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
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
newman run charts/hedera-mirror-web3/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

### Docker

```shell
docker run --rm -v "${PWD}/charts/hedera-mirror-web3/postman.json:/tmp/postman.json" -t postman/newman run /tmp/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

_Note:_ To test against an instance running on the same machine as Docker use your local IP instead of 127.0.0.1.

### Supported/unsupported operations


| Estimate | Static | Operation Type                                                                            | Supported | Historical data support | Reads | Modifications |
|----------|--------|-------------------------------------------------------------------------------------------|-----------|-------------------------|-------|---------------|
|    Y     |   Y    | non precompile functions                                                                  |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for Hts system contract AssociatePrecompile                                    |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | non precompile functions with lazy account creation                                       |     Y     |           N             |   Y   | Y             |
|    Y     |   Y    | operations for ERC precompile functions (balance, symbol, tokenURI, name, decimals, etc.) |     Y     |           N             |   Y   | N             |
|    Y     |   N    | operations for HTS system contract MintPrecompile                                         |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract GrantKycPrecompile                                     |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract WipePrecompile                                         |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract BurnPrecompile                                         |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract DissociatePrecompile                                   |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract SetApprovalForAllPrecompile                            |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract ApprovePrecompile                                      |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract RevokeKycPrecompile                                    |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract UnpausePrecompile                                      |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract PausePrecompile                                        |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract DeleteTokenPrecompile                                  |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract PausePrecompile                                        |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract FreezePrecompile                                       |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for HTS system contract UnfreezePrecompile                                     |     Y     |           N             |   Y   | Y             |
|    Y     |   N    | operations for IsApprovedForAllPrecompile                                                 |     Y     |           N             |   Y   | N             |
|    Y     |   N    | operations for AllowancePrecompile                                                        |     Y     |           N             |   Y   | N             |
|    Y     |   N    | operations for GetApprovedPrecompile                                                      |     Y     |           N             |   Y   | N             |
|    Y     |   N    | operations for HTS system contract UpdateTokenKeysPrecompile                              |     Y     |           N             |   Y   | Y             |
