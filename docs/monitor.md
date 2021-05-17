# Monitor

The monitor verifies end-to-end functionality of the Hedera network and generates metrics from the results. It supports
both publishing transactions to HAPI and subscribing to the mirror node API. Configuration is flexible and declarative,
allowing one to express a mixture of transactions and their expected publish rates, and the tool will do its best to
make it so. By default, the monitor is already set up with a basic scenario that creates a topic, submits a message
every 10 seconds to it, and verifies the messages are received via the mirror node's gRPC and REST APIs.

## Configuration

This section covers the higher level properties to consider before diving into the specific publish and subscribe
properties in the next sections. For a full list of configuration options see
the [config](/docs/configuration.md#monitor)
documentation.

First, make sure the monitor is configured to talk to the correct Hedera network by
setting `hedera.mirror.monitor.network` to
`MAINNET`, `PREVIEWNET` or `TESTNET`. If you are not using one of these public environments, the network can be set
to `OTHER`, and `hedera.mirror.monitor.nodes` and `hedera.mirror.monitor.mirrorNode` properties should be filled in.

Additionally, the operator information in `hedera.mirror.monitor.operator` is required and needs to be populated with a
valid payer account ID and its private key. Ensure this account has the necessary funds to publish transactions at the
rate you desire. The following is an example with a custom network configured:

```yaml
hedera:
  mirror:
    monitor:
      mirrorNode:
        grpc:
          host: 34.69.90.179
          port: 5600
        rest:
          host: 35.239.255.55
          port: 80
      network: OTHER
      nodes:
        - accountId: 0.0.3
          host: 34.94.106.61
        - accountId: 0.0.4
          host: 35.237.119.55
        - accountId: 0.0.5
          host: 35.245.27.193
        - accountId: 0.0.6
          host: 34.83.112.116
      operator:
        accountId: 0.0.1234
        privateKey: 104...
```

### Publish

The monitor can be configured to publish multiple transaction types concurrently. It does this by specifying a list of
scenarios that should actively publish. A unique scenario name is used as a label for metrics so that each scenario can
be monitored separately. Each scenario has a target transactions per second (TPS) and uses a client side rate limiter to
achieve the desired rate. Additionally, a percentage of receipts or records can be requested for each transaction to
verify transactions are reaching consensus.

The monitor can be used to publish at very high TPS, with a single monitor being able to max out the current capability
of the Hedera network. To publish at higher rates, the `hedera.mirror.monitor.publish.clients` and
`hedera.mirror.monitor.publish.threads` properties will need to be increased. For example, to achieve 10K TPS we set the
number of clients to 4, and the number of threads to 40. Please adjust accordingly per your needs. The transaction
publisher will round-robin the list of clients to send transactions to the Hedera Network, and every transaction is sent
to a randomly chosen node, ensuring the load is distributed evenly across all nodes.

The `type` property specifies which transaction type to publish. It also affects which `properties` need to be
specified, with different transaction types requiring different properties to be set. See the
[TransactionType](/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier/TransactionType.java)
enum for a list of possible values for `type`. The properties can be seen as fields on the various
[TransactionSupplier](/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier) classes that
the `TransactionType` enum references. Most of these properties have a default and don't need to be explicitly
specified, but some are empty and may need to be populated.

For example, if you want to publish a topic message, you would open the `TransactionType` class,
find `CONSENSUS_SUBMIT_MESSAGE`, then open the
[ConsensusSubmitMessageTransactionSupplier](/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier/consensus/ConsensusSubmitMessageTransactionSupplier.java)
class that it references. From there, you can see that fields `maxTransactionFee`, `message`, `retry`, and `topicId` are
available as properties. Only `topicId` doesn't have a default and will be required. Here's a YAML excerpt that
specifies some of those properties:

```yaml
hedera:
  mirror:
    monitor:
      publish:
        scenarios:
          - name: HCS Pinger
            properties:
              maxTransactionFee: 1000000
              message: Hello world!
              retry: true
              topicId: 0.0.1000
            recordPercent: 1.0
            tps: 0.1
            type: CONSENSUS_SUBMIT_MESSAGE
```

#### Scheduled Transactions

Scheduled transactions require unique configuration as they encompass two transactions during the entity creation. One (
outer) transaction for the `Schedule` entity create and one (inner/scheduled) transaction to eventually be executed. The
inner transaction must be signed by all required signatories as part of the `ScheduleCreate` or `ScheduleSign` scenario
transactions. Due to this the monitor by default will initially support only `ScheduleCreate` scenarios in which the
inner transaction is a `CryptoCreate` with `receiverSignatureRequired` set to true.

By default all required signatures will be provided. However, this can be modified by
setting `hedera.mirror.monitor.publish.scenarios.properties.signatoryCount` to be a number greater than 0 but smaller
than `hedera.mirror.monitor.publish.scenarios.properties.totalSignatoryCount`
To execute a scheduled scenario set the `hedera.mirror.monitor.publish.scenarios` properties similar to the following
example.

```yaml
hedera:
  mirror:
    monitor:
      publish:
        scenarios:
          - name: Scheduled Crypto Create
            properties:
              nodeAccountId: 0.0.3
              operatorAccountId: 0.0.1018
              receiverSignatureRequired: true
              signatoryCount: 4
              totalSignatoryCount: 5
            type: SCHEDULE_CREATE
```

### Expression Syntax

The monitor can automatically create account, token, and topic entities on application startup using a special
expression syntax. This is useful to avoid boilerplate configuration and manual entity creation steps that vary per
environment. The syntax can currently only be used in `hedera.mirror.monitor.publish.scenarios.properties`
and `hedera.mirror.monitor.subscribe.grpc.topicId`.

The syntax takes the form of `${type.name}` where `type` is one of `account`, `schedule`, `token`, or `topic`,
and `name` is a descriptive label. Based upon the entity type, it will create the appropriate entity on the network with
default values. The name label allows the same entity to be referenced in multiple places but only created once.

The following example uses the expression syntax to create the sender and recipient accounts as well as a token. These
same entities are created once and reused in both the token associate and the token transfer transaction.

```yaml
hedera:
  mirror:
    monitor:
      publish:
        scenarios:
          - name: HTS associate
            limit: 1
            properties:
              accountId: ${account.them}
              tokenId: ${token.foobar}
            tps: 1
            type: TOKEN_ASSOCIATE
          - name: HTS transfer
          properties:
            recipientAccountId: ${account.them}
            senderAccountId: ${account.me}
            tokenId: ${token.foobar}
            transferType: BOTH
          tps: 1
          type: CRYPTO_TRANSFER
```

### Subscribe

The monitor can optionally subscribe to the mirror node gRPC and REST APIs simultaneously. Each subscription type can
have one or more scenarios. For the REST API, it can verify that a percentage of individual transactions have made it to
the mirror node by querying the `/api/v1/transactions/{transactionId}` REST endpoint. The exact percentage to verify is
controlled via the `samplePercent` property.

For gRPC, `topicId` is required and controls which topic should be registered for asynchronous notifications of topic
messages. Below is an example of both types:

```yaml
hedera:
  mirror:
    monitor:
      subscribe:
        clients: 4
        grpc:
          - name: HCS Subscribe
            enabled: true
            subscribers: 10
            topicId: ${topic.ping}
        rest:
          - name: REST
            enabled: false
            samplePercent: 1.0
```

For performance testing subscribers, the `hedera.mirror.monitor.subscribe.clients` property should be adjusted higher to
control the pool of client connections to the server. Since the communication is asynchronous, a number between 1-10
should suffice. Additionally, the
`hedera.mirror.monitor.subscribe.grpc.subscribers` property can be adjusted to increase the number of concurrent
subscribers for that scenario.

## Dashboard & Metrics

The monitor includes a Grafana [dashboard](/charts/hedera-mirror-common/dashboards/hedera-mirror-monitor.json) that
displays the various metrics collected by the application visually. The monitor uses [Micrometer](http://micrometer.io)
to export the metrics to either [Elasticsearch](https://www.elastic.io) or [Prometheus](https://prometheus.io),
depending upon configuration. The Prometheus metrics can be scraped at `http://localhost:8082/actuator/prometheus`.

![Grafana Dashboard](https://user-images.githubusercontent.com/17552371/101863000-e4eee180-3b38-11eb-88b8-7841718db2f7.png)
