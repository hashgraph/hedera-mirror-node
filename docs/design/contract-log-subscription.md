# GraphQL Contract Log Subscription

## Purpose

Provide a GraphQL equivalent of the Ethereum
JSON-RPC [eth_subscribe](https://docs.infura.io/infura/networks/ethereum/json-rpc-methods/subscription-methods/eth_subscribe)
in order to provide contract log notifications in almost real time.

## Goals

- Stream contract log notifications via a GraphQL subscription
- Ensure at most once delivery. Will drop messages on backpressure overflow

## Non-Goals

- Will not query historical logs

## Non-Functional Requirements

- Need to ensure resource consumption is within reasonable bounds (reasonable bounds TBD via experimentation)
- Ensure performance is within reasonable bounds (reasonable bounds TBD via experimentation)

## Architecture

### Importer

1. Update RedisEntityListener (or create new listener) to send contract logs to contract_logs topic
- Populate the ContractLogEvent for topic message submission
- Messages will be serialized via RedisSerializer using msgpack

### GraphQL

- Use reactive websockets
- We should implement rate limiting in the load balancer
- Use bucket4j to enforce global max subscriptions
- Uses msgpack for topic message serialization

1. Create GraphQL schema for Input / Output
2. Create ContractLogEvent Mapper
3. Create Controller with @SubscriptionMapping
4. Create RedisConfiguration to configure msgpack serializer
5. Create a Redis Subscriber to listen for messages on contract_logs topic
6. Capture metrics
- subscriber consumption rate
- Active subscriptions counter
- Messages received (From redis topic) rate

#### Class Defs

```java
public class ContractController {
  /**
   * Use ContractLogTopicListener to begin streaming data to client
   * */
  @SubscriptionMapping
  public Publisher<ContractLogEvent> contractLogs(@Argument @Valid ContractLogSubscription subscription);
}

@Service
public class ContractLogTopicListener {
  private final Flux<ContractLogEvent> contractLogs;

  /**
   * Verifies can subscribe and returns error response if not
   * Uses shared lazy subscription to contractLogs
   * On backpressure overflow, drop messages
   * Filter events via addresses and/or topics param
   * calls unsubscribe on termination.
   * */
  public Flux<ContractLogEvent> listen(ContractLogSubscription subscription) {
  }

  /**
   * Takes contract log and performs query to obtain additional fields to populate ContractLogEvent
   * */
  private ContractLogEvent toContractLogEvent(ContractLog contractLog) {
  }

  /**
   * Filters messages by addresses and topics
   * */
  private boolean filterMessage(ContractLogEvent contractLogEvent, ContractLogFilter filter) {
  }

  /**
   * Can get token from rate limiter
   * contract with evm address in filter exists
   * we detect if this address input is account-num alias or evm address
   * alias and query the entity table for the latter to map it to its account-num alias.
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

#### application.yml

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
          maxActiveSubscriptions: 10
          maxBufferSize: 16384
          keepAlive: 1m

```

#### GraphQL Schema

```graphql

type ContractLogEvent {
  "The contract entity id"
  contractId: EntityId!
  "The address from which this log originated in the account-num alias format"
  address: String!
  "The hash of the block in which the transaction was included"
  blockHash: String!
  "The number of the block in which the transaction was included"
  blockNumber: Int64!
  "Contains one or more 32 Bytes non-indexed arguments of the log"
  data: String!
  "The index or position of the log entry within the block"
  logIndex: Int64!
  """
  An array of 0 to 4 32 bytes topic hashes of indexed log arguments.
  We should preserve null values in their correct index (i.e contract_log.[topic0...topic3])
  """
  topics: [String]!
  "The hash of the transaction"
  transactionHash: String!
  "The index or position of the transaction within the block"
  transactionIndex: Int64!
}

type ContractLogSubscription {
  "The addresses we want to stream logs for (Pattern may not work this way)"
  addresses: [String!] @Pattern(regexp: "^(0x)?[a-fA-F0-9]{40}$")
  """
  An array of topic specifiers.
  Each topic specifier is either null, or an array of strings.
  For every non null topic, a log will be emitted when activity associated with that topic occurs.
  []: Any topics allowed.
  [[A]]: A in first position (and anything after).
  [null, [B]]: Anything in first position and B in second position (and anything after).
  [[A], [B]]: A in first position and B in second position (and anything after).
  [[A, B], [A, B]]: (A or B) in first position and (A or B) in second position (and anything after).
  """
  topics: [[String]] @Pattern(regexp: "^(0x)?[a-fA-F0-9]{64}$")
}

type Subscription {
  contractLogs(subscription: ContractLogSubscription!): ContractLogEvent
}
```

#### Example Request Body

Addresses and topics are both optional filters

```graphql
{
  contractLogs(subscription: {
    addresses: ["EVM_ADDRESS_ALIAS OR ACCOUNT_NUM_ALIAS"],
    topics: [
      "ARRAY_OF_CONTRACT_TOPIC_IDS"
    ]
  }) {
    address
    blockHash
    blockNumber
    data
    logIndex
    topics
    transactionHash
    transactionIndex
  }
}
```

#### Example Event Response

```json
{
  "data": {
    "address": "0x000000000000000000000000000000000000000f",
    "blockHash": "0x61cdb2a09ab99abf791d474f20c2ea89bf8de2923a2d42bb49944c8c993cbf04",
    "blockNumber": "0x29e87",
    "data": "0x00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003",
    "logIndex": "0x0",
    "topics": [
      "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902"
    ],
    "transactionHash": "0xe044554a0a55067caafd07f8020ab9f2af60bdfe337e395ecd84b4877a3d1ab4",
    "transactionIndex": "0x0"
  }
}
```

#### Event Response DB Mapping

| Response Field   | Source Field                                                                                              |
|------------------|-----------------------------------------------------------------------------------------------------------|
| address          | account_num alias for contract entity                                                                     |
| blockHash        | record_file.hash                                                                                          |
| blockNumber      | record_file.index                                                                                         |
| data             | contract_log.data                                                                                         |
| logIndex         | contract_log.index                                                                                        |
| topics           | [contract_log.topic0, contract_log.topic1, contract_log.topic2, contract_log.topic3, contract_log.topic4] |
| transactionHash  | contract_log.transaction_hash                                                                             |
| transactionIndex | contract_log.transaction_index                                                                            |


