# Parser design

## Problems in current design

SQL Database client is tightly coupled with transaction & record's processor which makes:

-   ingesting mirror node date into other types of database like Cassandra, Bigtable, etc. very hard
-   benchmarking only parser's or only database ingestion performance in impossible

## Goal

1. Decouple parsing of stream from ingestion into a database
1. Abstractions should support measuring (a) parser's performance and (b) database ingestion performance, in isolation

## Non-goals

-   Change importer from filesystem based to in-memory streaming
-   Parsing multiple rcd/balance/etc files in parallel. Parser is far from being bottleneck, there is no need to optimize it
-   Accommodate possibility of publishing transactions/topic messages/etc to GRPC server directly
-   Support writing to multiple databases from single importer
-   Support filtering of transactions in parser
-   Update balance file parser code immediately

## Architecture

#### Data Flow

![Data Flow](images/parser-events-hander-data-flow.png)

-   Record files --> RecordFileReader --> RecordFileParser --> RecordItemParser --> RecordStreamEventsHandler --> DB

#### Control Flow

![Control Flow](images/parser-events-hander-control-flow.png)

### EventsHandler

![StreamEventsHandler](images/parser-events-handler-class-diagram.png)

```java
public interface StreamEventsHandler {
    void onStart();
    void onShutdown();
    void onFileStart(String filename) throws ImporterException;
    void onFileComplete(String filename) throws ImporterException;
    void onError(Throwable e);
}

public interface RecordStreamEventsHandler extends StreamEventsHandler {
    void onTransaction(c.h.m.i.d.Transaction) throws ImporterException;
    void onEntity(c.h.m.i.d.Entities) throws ImporterException;
    void onEntityUpdate(c.h.m.i.d.Entities) throws ImporterException;
    void onCryptoTransferList(c.h.m.i.d.CryptoTransfer) throws ImporterException;
    void onTopicMessage(c.h.m.i.d.TopicMessage) throws ImporterException;
}

public interface BalanceEventsHandler extends StreamEventsHandler {
    void onBalance(c.h.m.i.d.Balance) throws ImporterException;
}
```

1. There will be following implementations for `RecordStreamEventsHandler`:
    1. `PostgresWritingRecordStreamEventsHandler`:
        - For writing stream data to Postgres database
        - For `data-generator` to test database insert performance in isolation (from parser)
    1. `NullRecordStreamEventsHandler`: Discards all events and do nothing.
        - For micro-benchmarking parser performance
    1. `StreamFileWriterRecordStreamEventsHandler`: Package stream data into .rcd/balance/etc file.
        - For `data-generator` to generate custom stream files for testing: end-to-end importer perf test, parser + db
          perf test, isolated parser micro-benchmark, etc

### RecordItemParser

```java
public class RecordItemParser {
    private final RecordStreamEventsHandler RecordStreamEventsHandler;  // injected dependency

    public void onRecordItem(RecordItem recordItem) throws ImporterException {
        // process recordItem
    }
}
```

```java
@Value
public class RecordItem {
    private final Transaction transaction;
    private final TransactionRecord record;
}
```

1. Parse `Transaction` and `TransactionRecord` in the `recordItem`
1. Calls `onTransaction`/`onEntity`/`onEntityUpdate`/`onTopicMessage`/`onCryptoTransferLists` etc

### RecordFileParser

```java
public class RecordFileParser {

    private final RecordItemParser recordItemParser;  // injected dependency
    private final StreamEventsHandler streamEventsHandler;  // injected dependency

    public void onFile(String fileName) {
        // process stream file
    }

    @PostConstruct
    public void postConstruct() {
        streamEventsHandler.onStart();
    }

    @PreDestroy
    public void preDestroy() {
        streamEventsHandler.onShutdown();
    }
}
```

1. Triggers `streamEventsHandler.onStart()` and `streamEventsHandler.onShutdown()` on bean's creation and destruction
1. On each call to `onFile(filename)`:
    1. Call `streamEventsHandler.onFileStart(filename)`
    1. Validate prev hash
    1. For each set of `Transaction` and `TransactionRecord` in record file, call `recordItemParser.onRecordItem(recordItem)`.
    1. Finally call `streamEventsHandler.onFileComplete(filename)`
    1. On exceptions, call `streamEventsHandler.onError(error)`

### RecordFileReader

```java
public class RecordFileReader extends FileWatcher {

    private final RecordFileParser recordFileParser; // injected dependency

    @Override
    public void onCreate() {
        // List files
        recordFileParser.onFile(filename);
    }
}
```

## Outstanding questions:

1. Does Spring Data repository has support for Postgres COPY command? Couldn't find sources that suggest it does. If
   that indeed turns out to be the case, then I see at least two possibilities: - Use manual connection(s) to COPY to t_transactions, t_cryptotransferlists, topic_message, other write heavy tables.
   And use Spring Repositories for other tables. However, that raises the question of consistency of data across multiple
   transactions (since there are multiple connections). - Use COPY and PreparedStatement over single connection
1. How to do entity updates using Spring data?

## Tasks (in suggested order):

1. Finalize design
1. Refactoring
    1. Add the interfaces `StreamEventsHandler` and `RecordStreamEventsHandler`
    1. Create `PostgresWritingRecordStreamEventsHandler`. Move existing postgres writer code to new class as-is.
1. Perf benchmarks
    1. Move database abstractions to `hedera.mirror.repository` package from where they can be shared by importer, grpc, data-generator.
        - Replace `PostgresCSVDomainWriter` by `PostgresWritingRecordStreamEventsHandler` to test db insert performance and establish baseline
1. Optimize `PostgresWritingRecordStreamEventsHandler`
    - Use of `COPY`
    - Concurrency using multiple jdbc connections

#### Followup tasks to tie loose ends

-   Update `RecordStreamReader` to use `FileWatcher` and share as much as possible code with `BalanceFileParser` (to be renamed to `BalanceStreamReader`)
-   Remove event parser code: Doesn't have tests. Not used in last 6 months. No likelihood of needed in next couple months
    There is no need to pay tech-rent on this debt. Can be dont right once when it is really needed
-   Update balance file parser code to new design
