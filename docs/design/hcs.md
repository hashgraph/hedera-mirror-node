# Hedera Consensus Service Design

## Purpose

The Hedera Consensus Service (HCS) provides decentralized consensus on the validity and order of messages submitted to
a topic on the network and transparency into the history of these events over time. Since the Hedera mainnet does
not store history, the persistence and retrieval of these messages needs to be handled by the Mirror Node. This document
attempts to design a scalable solution to provide such functionality.

## Goals

- Ingest HCS related transactions from the mainnet and persist to the database
- Provide a streaming GRPC API to subscribe to HCS topics
- Persist a subset of entities or transaction types due to the potential increase in the volume of data caused by HCS
- Provide a listener interface so third parties can add custom code to be notified of validated transactions
- Make the Mirror Node easier for users to run to enable wider adoption by the community

## Non-Goals

These items are not currently in scope at this time in order to deliver a MVP, but can be revisited later as the need arises:

- Provide a HCS REST API
- Provide a HCS WebSocket API
- Provide a non-HCS GRPC API
- Provide a "relay" service to publish to other downstream systems
- Provide higher level functionality like encryption, fragmentation or reassembly
- Gossip based Mirror Node

## Architecture

![Architecture](hcs-architecture.png)

1. Downloader retrieves transactions from S3 and validates them
2. Parser persists to PostgreSQL
3. Client queries GRPC API for messages from a specific topic
4. GRPC API retrieves the current messages from PostgreSQL and returns to client
5. GRPC API is notified of new `ConsensusSubmitMessage` transactions via PostgreSQL Notify

### Alternatives

- Replace step #5 above with the GRPC layer polling for new data
- Replace step #5 above with a message broker that parser publishes to and GRPC API consumes

## GRPC API

### Setup

- Create a new Maven sub-project `mirror-api-grpc`
- Use `com.github.os72:protoc-jar-maven-plugin` for protoc
- Use `net.devh:grpc-spring-boot-starter` to manage GRPC component lifecycle
- Use `spring-boot-starter-data-r2dbc` and `io.r2dbc:r2dbc-postgresql` for reactive database access and asynchronous pg_notify support
- Create a Systemd unit file `mirror-grpc.sevice`
- Add to CircleCI and deployment script

### Protobuf

```proto
message ConsensusTopicQuery {
    .proto.TopicID topicID = 1; // A required topic ID to retrieve messages for.
    .proto.Timestamp consensusStartTime = 2; // Include messages which reached consensus on or after this time. Defaults to current time if not set.
    .proto.Timestamp consensusEndTime = 3; // Include messages which reached consensus before this time. If not set it will receive indefinitely.
    uint32 limit = 4; // The maximum number of messages to receive before stopping. If not set or set to zero it will return messages indefinitely.
}

message ConsensusTopicResponse {
    .proto.Timestamp consensusTimestamp = 1; // The time at which the transaction reached consensus
    bytes message = 2; // The message body originally in the ConsensusSubmitMessageTransactionBody. Message size will be less than 4K.
    bytes runningHash = 3; // The running hash (SHA384) of every message.
    uint64 sequenceNumber = 4; // Starts at 1 for first submitted message. Incremented on each submitted message.
}

service ConsensusService {
    rpc subscribeTopic (ConsensusTopicQuery) returns (stream ConsensusTopicResponse);
}
```

### Consensus Service

![Sequence diagram](hcs-consensus-service.png)

- Implement `TopicMesage` domain class
- Implement `TopicMessageRepository`:
```java
public interface TopicMessageRepository extends ReactiveCrudRepository<TopicMessage, Long>, TopicMessageRepositoryCustom {
}
```
- Implement custom repository method that uses R2DBC's [Fluent Data Access API](https://docs.spring.io/spring-data/r2dbc/docs/1.0.x/reference/html/#r2dbc.datbaseclient.fluent-api)
to build a dynamic query based upon the filter:
```java
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {
  public Flux<TopicMessage> findByFilter(TopicMessageFilter filter) {
    ...
  }
}
```
> _Note:_ A [Flux](https://projectreactor.io/docs/core/release/reference/#flux) is a reactive publisher that emits an
asynchronous sequence of 0..N items.
- Implement `TopicSubscriber` that wraps `StreamObserver` in a `org.reactivestreams.Subscriber`:
```java
public class TopicSubscriber implements Subscriber<TopicMessage> {
  public void onSubscribe(Subscription subscription) {
  }

  public void onNext(TopicMessage topicMessage) {
  }

  public void onError(Throwable throwable) {
  }

  public void onComplete() {
  }
}
```
- Implement `ConsensusService`:
```java
@GrpcService
public class ConsensusService extends ConsensusServiceGrpc.ConsensusServiceImplBase {
    public void subscribeTopic(ConsensusTopicQuery consensusTopicQuery, StreamObserver<ConsensusTopicResponse> streamObserver) {
      ...
    }
}
```
- Map `ConsensusTopicQuery` to `TopicMessageFilter`
- Perform `topicMessageRepository.findByFilter(filter)`
- Create and subscribe a `TopicSubscriber` to the results from the repository
- Map `TopicMessage` to `ConsensusTopicResponse`
- There can be multiple subscribers on the same topic for the same GRPC server and we should consider optimizations
around that

### Topic Listener

Provide the ability to stream new topic messages from the database and notify open GRPC streams of new data:
- Implement a `TopicListener` that uses R2DBC's notify/listen support:
```java
public interface TopicListener {
    Flux<TopicMessage> listen(TopicMessageFilter filter);
}
```
- Register `ConsensusService` with `TopicListener` to receive notifications of topic messages, filtering and buffering appropriately until
current query finishes

## Parser

### Persist

Modify `RecordFileLogger` to handle HCS transactions
- Insert HCS transaction into `t_transactions`
- Insert `ConsensusSubmitMessage` transactions into `topic_message`

### Filter

Provide the ability to filter transactions before persisting via configuration:
- Add a new option `hedera.mirror.parser.include`
- Add a new option `hedera.mirror.parser.exclude` with higher priority than includes
- The structure of both will be a list of transaction filters and get turned into a single Predicate
at runtime:
```yaml
include:
  - transaction: [ConsensusCreateTopic, ConsensusDeleteTopic, ConsensusUpdateTopic, ConsensusSubmitMessage]
    entity: [0.0.1001]
```
- Remove options `hedera.mirror.parser.record.persist*` and convert defaults to newer format

### Notify

Provide the ability to notify components of new transactions:
- Provide a `TransactionListener.notify(new TransactionEvent(Transaction, Record))` interface to be notified of incoming transactions
- Rework `RecordFileLogger` to implement `TransactionListener`
- Decouple `RecordFileParser` and `RecordFileLogger` so that `RecordFileParser` invokes `TransactionListener`

## Database

- Add new `t_entity_types` row with name `topic`
- Add new column `submit_key` to t_entities
- Add new `t_transaction_types`:
  - `CONSENSUSCREATETOPIC=24`
  - `CONSENSUSUPDATETOPIC=25`
  - `CONSENSUSDELETETOPIC=26`
  - `CONSENSUSSUBMITMESSAGE=27`
- Add new `t_transaction_result`:
  - `INVALID_TOPIC_ID = 150`
  - `TOPIC_DELETED = 151`
  - `MESSAGE_TOO_LONG = 152`
  - `TOPIC_NOT_ENABLED = 153`
  - `INVALID_TOPIC_VALID_START_TIME = 154`
  - `INVALID_TOPIC_EXPIRATION_TIME = 155`
  - `INVALID_TOPIC_ADMIN_KEY = 156`
  - `INVALID_TOPIC_SUBMIT_KEY = 157`
  - `UNAUTHORIZED = 158`
  - `INVALID_TOPIC_MESSAGE = 159`
- Add new table `topic_message`:
```postgres-sql
create table if not exists topic_message (
  consensus_timestamp nanos_timestamp primary key not null,
  entity_id bigint not null references t_entities (id) on delete cascade on update cascade,
  message bytea not null,
  running_hash bytea not null,
  sequence_number bigint not null
);

create index if not exists topic_message_entity_id_timestamp
on topic_message (entity_id, consensus_timestamp);
```
- Create a trigger that calls a new function on every insert of topic_message, serializes it to JSON and calls pg_notify
- Alternative would be dynamically create trigger for each GRPC call, but deleting trigger would be racy and duplicate traffic for same topic
- TODO: Support a retention of X days for `topic_message`?
