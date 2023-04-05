# GraphQL Contract Log Subscription

## Purpose

Provide a mechanism equivalent of the Ethereum
JSON-RPC [eth_subscribe](https://docs.infura.io/infura/networks/ethereum/json-rpc-methods/subscription-methods/eth_subscribe)
in order to provide contract log notifications in almost real time.

## Goals

- Stream contract log notifications via a GraphQL subscription

## Architecture

### Importer

1. Update RedisEntityListener to send contract logs to contract_logs.{CONTRACT_ID} topic

### GraphQL

- Use RSocket for transport layer (This provides additional functionality over traditional websockets and is recommended
  for reactive applications)
- Use Bucket4J for rate limiting

1. Create GraphQL schema for Input / Output
2. Create ContractLogEvent Mapper
3. Create Controller with @SubscriptionMapping
4. Create a Redis Subscriber to listen for contract_logs.{CONTRACT_ID}

#### Contract Log to ContractLogEvent Query

```sql
SELECT e.evm_address                                      as address,
       block_number,
       block_hash,
       cl.data,
       cl.index                                           as log_index,
       ARRAY [cl.topic0, cl.topic1, cl.topic2, cl.topic3] as topics,
       cl.transaction_hash,
       cl.transaction_index

from contract_log cl
       join entity e on e.id = cl.contract_id
       join lateral (
  select index as block_number, hash as block_hash
  from record_file
  where consensus_end >= cl.consensus_timestamp
  order by consensus_end asc
  limit 1
  ) block on true
where cl.consensus_timestamp = ?
  and cl.index = ?;
```

#### Class Defs

```java
public class ContractLogController {
  /**
   * Use ContractLogTopicListener to begin streaming data to client
   * */
  @SubscriptionMapping("contractLog")
  public Publisher<ContractLogEvent> subscribe(@Argument @Valid ContractLogFilter filter);
}

@Service
public class ContractLogTopicListener {
  Map<String, Flux<ContractLogEvent>> subscribedTopics;

  /**
   * Verifies can subscribe and returns error response if not
   * Uses cached flux if exists, otherwise creates entry and starts listening.
   * On backpressure overflow, drop messages
   * Filter result via filter param
   * calls unsubscribe on termination.
   * */
  public Flux<ContractLogEvent> listen(ContractLogFilter filter) {
  }

  /**
   * Takes contract log and performs query to obtain additional fields to populate ContractLogEvent
   * */
  private ContractLogEvent toContractLogEvent(ContractLog contractLog) {
  }

  /**
   * Filters messages by contractLog topic fields
   * */
  private boolean filterMessage(ContractLogEvent contractLogEvent, ContractLogFilter filter) {
  }

  /**
   * Can get token from rate limiter
   * contract with evm address in filter exists
   * */
  private boolean canSubscribe(ContractLogFilter filter) {
  }

  /**
   * Remove entry from cached listeners
   * Resupply token to rate limiter
   */
  private void unsubscribe() {
  }

}
```

### application.yml

```yaml
spring:
  rsocket:
    server:
      port: 9191
      mapping: subscribe

hedera:
  mirror:
    graphql:
      listener:
        contractLog:
          maxActiveSubscriptions: 1000
          maxBufferSize: 16384

```

#### GraphQL Schema

```graphql

type ContractLogEvent {
  address: String
  blockHash: String
  blockNumber: Long
  data: String
  logIndex: Long
  topics: [String]
  transactionHash: String
  transactionIndex: Long
}

type ContractLogSubscriptionInput {
  address: String @Pattern(regexp: "^(0x)?[a-fA-F0-9]{40}$")
  topics: [String]
}

type Subscription {
  contractLog(input: ContractLogSubscriptionInput): ContractLogEvent
}
```

#### Testing with RSocket

See [RSC Client](https://github.com/making/rsc) for a grpcurl equivalent

#### Example Request Body

Address is required and topics is an optional filter

```json
{
  "address": "CONTRACT_ENTITY_EVM_ADDRESS",
  "topics": [
    "ARRAY_OF_CONTRACT_TOPIC_IDS"
  ]
}
```

#### Example Event Response

```json
{
  "address": "0x8320fe7702b96808f7bbc0d4a888ed1468216cfd",
  "blockHash": "0x61cdb2a09ab99abf791d474f20c2ea89bf8de2923a2d42bb49944c8c993cbf04",
  "blockNumber": 171655,
  "data": "0x00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003",
  "logIndex": 0,
  "topics": [
    "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902"
  ],
  "transactionHash": "0xe044554a0a55067caafd07f8020ab9f2af60bdfe337e395ecd84b4877a3d1ab4",
  "transactionIndex": 0
}
```

#### Event Response DB Mapping

| Response Field   | Source Field                                                                                              |
|------------------|-----------------------------------------------------------------------------------------------------------|
| address          | entity.evm_address                                                                                        |
| blockHash        | record_file.hash                                                                                          |
| blockNumber      | record_file.index                                                                                         |
| data             | contract_log.data                                                                                         |
| logIndex         | contract_log.index                                                                                        |
| topics           | [contract_log.topic0, contract_log.topic1, contract_log.topic2, contract_log.topic3, contract_log.topic4] |
| transactionHash  | contract_log.transaction_hash                                                                             |
| transactionIndex | contract_log.transaction_index                                                                            |


