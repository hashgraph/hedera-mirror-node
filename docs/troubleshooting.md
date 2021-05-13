# Troubleshooting

This troubleshooting guide lists methods for detecting and handling issues with mirror node.

## Handling issues

### Importer

#### First step

Any time there is any disruption on the mirrornet at all, the first thing to do is decide whether the failover is
healthy. If it is, we want to switch DNS immediately while we troubleshoot what is happening.

Similarly, any time mirror node needs to be restarted, switch DNS to the failover first if it is healthy.

#### Log-based issues

Following is list of error messages and how to begin handling issues when they are encountered.

- `Encountered unknown transaction type`

  Ideally, this should not happen because new transaction types are released after updating mirror node to be able to
  handle them. Alerted only when impacting significant number of transactions.

  Actions:

  - There is no immediate fix. Bring to team's attention immediately (during reasonable hours, otherwise next morning).

- `Error closing connection`

  If this happens, it's possible that database will eventually run out of open connections.

  Actions:

  - Check Cloud SQL console to figure out if connection limit is being reached. If so, restarting importer would be one
    way to temporarily fix it.
  - Ensure no service outage happens due to connection limit, restart as needed.

- `Error parsing record file` \
  `previous hash is null` \
  `Hash mismatch for file` \
  `Previous file hash not available` \
  `Unable to extract hash and signature from file` \
  `Unknown file delimiter` \
  `Unknown record file delimiter`

  All of the above errors happen when data inside stream files is not as expected. It can be because of new bug
  introduced on the importer side, or due to mainnet node publishing bad data.

  Actions:

  - Notify devops immediately
  - Check if any recent changes were made to the code related to the error

- `Error saving file in database` \
  `Unable to connect to database` \
  `Unable to fetch entity types` \
  `Unable to prepare SQL statements` \
  `Unable to set connection to not auto commit`

  All of the above errors have some SQLException as the root cause.

  Actions:

  - Check Cloud SQL instance is up and running correctly (by executing some SQL queries). If the problem seems on Cloud
    SQL side, escalate to devops.

- `ERRORS processing account balances file`

  Can be caused either by bad data in account balances stream or due to SQL exceptions. In either case, exception are
  logged and the action is retried.

  Actions:

  - If bad data in the stream, escalate to devops
  - If due to sql exception, check if next try is successful. If the error continues, investigate.

- `Exception`

  Actions:

  - See exception details for more info (attempt to diagnose, including trying to restart the service, but escalate
    anyway)

- `Failed downloading`

  These errors happen even in a perfectly running mirror node, but are pretty infrequent (one every few minutes). Failed
  download attempts will be retried rapidly. If they happen two much, investigate. Many possible causes - S3 may be
  down, or there maybe other connection issues.

  Actions:

  - Try downloading failing files locally (checks S3 is up)
  - Check socket usage, packet loss, etc on importer instance

- `Failed to parse NodeAddressBook from`

  Actions:

  - There is no immediate fix. Bring to team's attention immediately (during reasonable hours, otherwise next morning).

- `Insufficient downloaded signature file count, requires at least`
  This can happen if

  1. Some mainnet nodes are still in the process of uploading their signatures for the latest file (benign case).
     Logging rate will be at most 20/min.
  2. Bad signatures by some mainnet nodes, halts the downloader progress. Logging rate in this case can reach 100/min.

  Effect: In case of bad signatures, it'll halt system progress.

  Actions:

  - If happens due to bad signatures, escalate to devops.

- `Long overflow when converting time to nanos timestamp`

  Importer assumes all timestamps can be converted into nanos-since-epoch and stored as `Long`. This error will halt the
  progress of parser

  Actions:

  - There is no immediate fix. Bring to team's attention immediately (during reasonable hours, otherwise next morning).

- `Unable to copy address book from`

  Actions:

  - For emergency fix, manually copy known good address book to the destination.

- `Unable to guess correct transaction type since there's not exactly one`

  Ideally, this should not happen because new transaction types are released after updating mirror node to be able to
  handle them. However, occurrence of this error means parser will keep retrying and will never make progress

  Actions:

  - There is no immediate fix. Bring to team's attention immediately (during reasonable hours, otherwise next morning).

#### Stream Restart

There have been cases in the past before OA where the stream had to be restarted. A stream restart entails a new S3
bucket and zeroing out the hash of the previous file in the first file in that bucket. If you're using the old bucket,
there will be no errors but you won't receive any new data after the restart event. To recover you need the wipe the
files and database:

1 ) Stop Mirror Node Importer

```console
sudo systemctl stop hedera-mirror-importer
```

2 ) Change Bucket Name and Region

```console
sudo vi /usr/etc/hedera-mirror-importer/application.yml
```

3 ) Wipe Database

```console
scp hedera-mirror-importer/src/main/resources/db/scripts/cleanup.sql user@server:~
ssh user@server
psql -h dbhost -d mirror_node -U mirror_node -f drop.sql
```

> **_NOTE:_** The `cleanup.sql` script was previously used to wipe the database as it truncates the tables and resets identities.
> However, it was not designed to account for bootstrap data such as address books which are persisted during flyway migrations.
> Hence, `drop.sql` is favored over `cleanup.sql` as it clears the schema and allows migrations to be re-run accordingly.

4 ) Start Mirror Node Importer

```console
sudo systemctl start hedera-mirror-importer
```

## Detecting and alerting issues

This section lists alerts to detect issues when a mirror node might not be functioning normally, and guiding rules to
help prioritize new alerts.

The priorities below are based on
[PagerDuty's Alert Priorities](https://response.pagerduty.com/oncall/alerting_principles/) and warrant same responses as
mentioned in that link.

### Priorities

#### High Priority

If a message signals any of the following, then it qualifies as high priority:

1. Service is down
2. A scenario which will certainly halt system progress \
   For example, parser encounters badly formatted file which will certainly halt parser's progress.
3. Data loading delayed more than accepted SLA
4. Anything that adversely impacts many transactions \
   For example, timestamp overflow affecting many transactions

Alerts: High-Priority PagerDuty Alert 24/7/365 Response: Requires immediate human action

#### Medium Priority

If a message signals any of the following, then it qualifies as medium priority:

1. Service is lagging
2. A scenario which may halt system progress \
   For example, many badly formatted files are encountered by downloader and it is possible that progress may halt.

Alerts: High-Priority PagerDuty Alert during business hours only Response: Requires human action within 24 hours.

#### Low Priority

If a message signals any of the following, then it qualifies as low priority:

1. An unexpected scenario, which if continues for sufficient time can eventually lead to medium/high priority scenarios
2. Non-critical system assumption are broken but no real or very limited impact (say few transactions) \
   For example, required field missing in transaction/receipt which only impacts that transaction. For instance, topicId
   missing in update/delete topic transaction. \
   For example, invalid/Missing signatures but it doesn't halt progress since it's only from one of the many nodes.

Alerts: Low-Priority PagerDuty Alert during business hours only Response: Requires human action at some point.

### Importer

#### Log-based alerts

| Log Message                                                                                 | Default Priority | Conditional Priority            |
| ------------------------------------------------------------------------------------------- | ---------------- | ------------------------------- |
| `Error parsing record file`                                                                 | HIGH             |                                 |
| `Error starting watch service`                                                              | HIGH             |                                 |
| `ERRORS processing account balances file`                                                   | HIGH             |                                 |
| `previous hash is null`                                                                     | HIGH             |                                 |
| `Failed to parse NodeAddressBook from`                                                      | HIGH             |                                 |
| `Hash mismatch for file`                                                                    | HIGH             |                                 |
| `Long overflow when converting time to nanos timestamp`                                     | HIGH             |                                 |
| `Previous file hash not available`                                                          | HIGH             |                                 |
| `Unable to extract hash and signature from file`                                            | HIGH             |                                 |
| `Unable to guess correct transaction type since there's not exactly one`                    | HIGH             |                                 |
| `Unknown file delimiter`                                                                    | HIGH             |                                 |
| `Unknown record file delimiter`                                                             | HIGH             |                                 |
| `Error processing balances files after`                                                     | MEDIUM           |                                 |
| `Exception processing account balances file`                                                | MEDIUM           |                                 |
| `Encountered unknown transaction type`                                                      | LOW              | HIGH (if 10 entries over 10 min |
| `Error closing connection`                                                                  | LOW              | HIGH (if 10 entries over 10 min |
| `Account balance dataset timestamp mismatch!`                                               | LOW              |                                 |
| `Error decoding hex string`                                                                 | LOW              |                                 |
| `Failed to verify`                                                                          | LOW              |                                 |
| `Input parameter is not a folder`                                                           | LOW              |                                 |
| `Failed to verify signature with public key`                                                | LOW              |                                 |
| `Missing signature for file`                                                                | LOW              |                                 |
| `Error saving file in database`                                                             | NONE             | HIGH (if 30 entries in 1 min)   |
| `Failed downloading`                                                                        | NONE             | HIGH (if 30 entries in 1 min)   |
| `Insufficient downloaded signature file count, requires at least`                           | NONE             | HIGH (if 30 entries in 1 min)   |
| `Signature verification failed`                                                             | NONE             | HIGH (if 30 entries in 1 min)   |
| `Unable to connect to database`                                                             | NONE             | HIGH (if 30 entries in 1 min)   |
| `Unable to fetch entity types`                                                              | NONE             | HIGH (if 30 entries in 1 min)   |
| `Unable to prepare SQL statements`                                                          | NONE             | HIGH (if 30 entries in 1 min)   |
| `Unable to set connection to not auto commit`                                               | NONE             | HIGH (if 30 entries in 1 min)   |

Anything that wakes up a human in the middle of the night should be immediately actionable. For all `HIGH` priority
alerts, there should be a section in the guide above listing immediate actionable steps someone can take to reduce
issue's severity of or to fix it.
