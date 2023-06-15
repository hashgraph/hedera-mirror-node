# GraphQL API

The GraphQL API is an implementation of the [GraphQL specification](https://spec.graphql.org/) for the Hedera Mirror
Node. It is considered an alpha API subject to breaking changes at any time, so it's not recommended to depend upon
for production use.

## Configuration

Similar to the other components, the GraphQL API uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                        | Default                                         | Description                                                                                                                                                                                   |
|---------------------------------------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hedera.mirror.graphql.cache.query`         | expireAfterWrite=1h,maximumSize=1000,recordStats | The Caffeine cache expression to use to configure the query parser cache.                                                                                                                     |
| `hedera.mirror.graphql.db.host`             | 127.0.0.1                                       | The IP or hostname used to connect to the database.                                                                                                                                           |
| `hedera.mirror.graphql.db.name`             | mirror_node                                     | The name of the database.                                                                                                                                                                     |
| `hedera.mirror.graphql.db.password`         | mirror_graphql_pass                             | The database password used to connect to the database.                                                                                                                                        |
| `hedera.mirror.graphql.db.port`             | 5432                                            | The port used to connect to the database.                                                                                                                                                     |
| `hedera.mirror.graphql.db.sslMode`          | DISABLE                                         | The ssl level of protection against eavesdropping, man-in-the-middle (MITM) and impersonation on the db connection. Accepts either DISABLE, ALLOW, PREFER, REQUIRE, VERIFY_CA or VERIFY_FULL. |
| `hedera.mirror.graphql.db.statementTimeout` | 10000                                           | The maximum amount of time in seconds to wait for a query to finish                                                                                                                           |
| `hedera.mirror.graphql.db.username`         | mirror_graphql                                  | The username used to connect to the database.                                                                                                                                                 |
