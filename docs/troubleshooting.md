# Troubleshooting

## Record files

* Recordstream files that have successfully been downloaded and parsed will be moved to `"downloadToDir"/recordstreams/parsedRecordFiles`

## Known issue [Record downloader stops working with "Unable to connect to database" error](https://github.com/hashgraph/hedera-mirror-node/issues/197)

This is likely a known resource leak.

On seeing basically a nonstop rapid stream of `Unable to connect to database. Will retry in .*` or a brief period of frequent `Address book file .* not found`

1. Verify the database (as configured in config.json) is up and you can connect to it via psql from the mirror-node host
2. If unreachable - diagnose the issue as a postgresql database issue or network issue.
3. If reachable - it may be this issue - follow the steps for stopping then starting the mirror node (reboot it) and monitor the journal for errors.
4. If that doesn't resolve the problem - escalate

## AWS/Google-cloud downloading errors and other likely temporary errors

The record and account balance downloaders are constantly downloading files from AWS and google.

Temporary errors occur and the system will recover quickly.

The following errors may be temporary and resolve themselves:

- `RecordFileDownloader Failed downloading .*`
- `Failed downloading .* after .*` 
- `Failed to download key .* from cloud`
- `File watching events may have been lost or discarded`
- `Unable to set connection to not auto commit`
- `Error saving file .* in database`

If they don't self-resolve fairly quickly (files download attempts will be retried rapidly) - escalate.

The record stream should be downloaded every 5-10 seconds, account balances approximately every 15 minutes.

Temporary network/download errors should not persist for more than 30 seconds.

## Messages likely requiring service restart

- `Error starting watch service`
- `Unable to fetch entity types`
- `Unable to fetch transaction types`

If restarting doesn't resolve these errors

1. diagnose as a database issue
2. failing that - escalate

## Messages that require immediate escalation

Some of these messages may indicate an integration issue between Hedera services and the mirror node, or a significant defect on either.

- `Previous file hash not available`
- `Hash mismatch for file`
- `Unknown record file delimiter`
- `Unknown file delimiter`
- `Error parsing record file`
- `Expecting previous file hash, not found file delimiter`
- `Unable to extract hash and signature from file`
- `Exception .*`
  - see exception details for more info (attempt to diagnose, including trying to restart the service, but escalate anyway)
- `Failed to verify record files`
- `Account balance dataset timestamp mismatch!`
- `ERRORS processing account balances file`
- `Application status code .* does not exist in the database`

## Messages that may not require escalation

The following issues may be related to misconfiguration in config.json.
If the mirror node had recently been bounced - see if these issues are resolvable by fixing config.json and restarting the service. If not, escalate.

- `Address book file .* not found`
- `.* environment variable not set`
- `Cannot load configuration from`
- `Error updating application status`

## Messages that do not require immediate escalation

These should be investigated by Hedera, but require no immediate escalation unless they are occurring frequently (more than 1/min).

- `Transaction type .* not known to mirror node`
