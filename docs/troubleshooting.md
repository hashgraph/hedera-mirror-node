# Troubleshooting

## Record files

Record stream files that have successfully been downloaded and parsed will be moved to `${hedera.mirror.dataPath}/recordstreams/parsedRecordFiles`.

## Transient Errors

The record and account balance downloaders are constantly downloading files from AWS and google.
Temporary errors occur and the system will recover quickly.
If they don't self-resolve fairly quickly (files download attempts will be retried rapidly) - escalate.
The record stream should be downloaded every 5-10 seconds, account balances approximately every 15 minutes.
Temporary network/download errors should not persist for more than 30 seconds.

-   `RecordFileDownloader Failed downloading .*`
-   `Failed downloading .* after .*`
-   `Failed to download key .* from cloud`
-   `File watching events may have been lost or discarded`
-   `Unable to set connection to not auto commit`
-   `Error saving file .* in database`

## Messages likely requiring service restart

-   `Error starting watch service`
-   `Unable to fetch entity types`
-   `Unable to fetch transaction types`

If restarting doesn't resolve these errors

1. Diagnose as a database issue
2. Failing that - escalate

## Messages that require immediate escalation

Some of these messages may indicate an integration issue between Hedera services and the mirror node, or a significant defect on either.

-   `Previous file hash not available`
-   `Hash mismatch for file`
-   `Unknown record file delimiter`
-   `Unknown file delimiter`
-   `Error parsing record file`
-   `Expecting previous file hash, not found file delimiter`
-   `Unable to extract hash and signature from file`
-   `Exception .*`
    -   See exception details for more info (attempt to diagnose, including trying to restart the service, but escalate anyway)
-   `Failed to verify record files`
-   `Account balance dataset timestamp mismatch!`
-   `ERRORS processing account balances file`
-   `Application status code .* does not exist in the database`
-   `Long overflow when converting Instant to nanos timestamp : *`

## Messages that may not require escalation

The following issues may be related to a misconfiguration issue. If the mirror node had recently been bounced, see if these
issues are resolvable by updating the application configuration and restarting the service. If not, escalate.

-   `Address book file .* not found`
-   `.* environment variable not set`
-   `Error updating application status`

## Messages that do not require immediate escalation

These should be investigated by Hedera, but require no immediate escalation unless they are occurring frequently (more than 1/min).

-   `Transaction type .* not known to mirror node`
-   `Long overflow when converting time to nanos timestamp`

## Known Issues

### Illegal access

The error below may appear on the console when running the `.jar` file, this is normal and nothing to be concerned about:

```code
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by com.google.protobuf.UnsafeUtil (file:/home/hedera/mirrornode/lib/protobuf-java-3.5.1.jar) to field java.nio.Buffer.address
WARNING: Please consider reporting this to the maintainers of com.google.protobuf.UnsafeUtil
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
```

### Stream Restart

There have been cases in the past before OA where the stream had to be restarted. A stream restart entails a new S3 bucket
and zeroing out the hash of the previous file in the first file in that bucket. If you're using the old bucket, there will
be no errors but you won't receive any new data after the restart event. To recover you need the wipe the files and database:

1 ) Stop Mirror Node

```console
sudo systemctl stop mirror-node
```

2 ) Change Bucket Name and Region

```console
sudo vi /usr/etc/mirror-node/application.yml
```

3 ) Backup files

```console
cd /var/lib/mirror-node
sudo mkdir backup20191114
sudo mv accountBalances eventsStreams recordstreams backup20191114/
```

4 ) Wipe Database

```console
scp src/main/resources/db/scripts/cleanup.sql user@server:~
ssh user@server
psql -h dbhost -d mirror_node -U mirror_node -f cleanup.sql
```

5 ) Start Mirror Node

```console
sudo systemctl start mirror-node
```
