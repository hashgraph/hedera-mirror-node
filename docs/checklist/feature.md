# New Feature Checklist

New tables or columns are routinely added to the mirror node to support changes in HAPI. Due to the micro-service
nature of the mirror node there are a lot of components that need to be updated for any new feature. This checklist
aims to document the required changes associated with any new feature.

## Common

- [ ] Domain class added/updated
  - [ ] `@AllArgsConstructor(access = AccessLevel.PRIVATE)`, `@Builder(toBuilder = true)` for builder
  - [ ] `@IdClass` for composite keys
- [ ] `TransactionType` updated
- [ ] `DomainBuilder` updated

## Importer

- [ ] V1 migration added
- [ ] `V2.0.0__create_tables.sql` updated
- [ ] `V2.0.1__distribution.sql` updated
- [ ] `V2.0.3__index_init.sql` updated
- [ ] Repository added
  - [ ] `RetentionRepository` added if insert-only
- [ ] `EntityListener` updated
- [ ] `CompositeEntityListener` updated
- [ ] `SqlEntityListener` updated
  - [ ] Merge method added if not insert-only and all fields properly merged
- [ ] `TransactionHandler` added/updated
- [ ] Tests for each of the above
- [ ] `RecordItemBuilder` updated
- [ ] `EntityRecordItemListener*Test` updated
- [ ] `PubSubMessageTest` updated for `TransactionRecord` changes
- [ ] `pub-sub-messages.txt` updated

## Monitor

- [ ] `TransactionSupplier` added
- [ ] `TransactionType` updated

## Rest API

- [ ] `openapi.yml` updated
  - [ ] Transaction type added
  - [ ] Request/response added/updated
- [ ] Model added/updated
- [ ] View model added/updated
- [ ] Route added
- [ ] Controller added/updated
- [ ] Service added/udpated
- [ ] Tests for each of the above
- [ ] Spec tests added
- [ ] Monitor API test added

## Rosetta API

- [ ] ??

## Test

- [ ] k6 performance test added
- [ ] Cucumber scenario added
- [ ] Acceptance REST model added/updated
- [ ] Acceptance client updated
- [ ] Acceptance feature updated

## Documentation

- [ ] [configuration.md](/docs/configuration.md) updated
- [ ] [database.md](/docs/database.md#indexes) table updated
- [ ] [REST README](/docs/rest/README.md#database) table updated

## ETL

- [ ] `transaction-types.csv` updated
- [ ] `transactions-schema.json` updated
