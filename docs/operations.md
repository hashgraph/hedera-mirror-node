# Operations

## File Layout

-   `/usr/lib/hedera-mirror-importer` - Binaries
-   `/usr/etc/hedera-mirror-importer` - Configuration files
    -   `application.yml`
-   `/var/lib/hedera-mirror-importer` - Data
    -   `addressbook.bin` - The current address book in use
    -   `accountBalances` - The downloaded balance and signature files
    -   `recordstreams` - The downloaded record and signature files
-   `/etc/systemd/system/hedera-mirror-importer.service` - systemd service definitions

## Starting

```
sudo systemctl start hedera-mirror-importer.service
```

## Stopping

```
sudo systemctl stop hedera-mirror-importer.service
```

## Monitoring logs (tailing)

```
sudo journalctl -fu hedera-mirror-importer.service
```

## Normal system logs

Normal output should include several frequent INFO level messages such as:

-   `RecordFileDownloader Downloaded .* RCD signatures for node .*`
-   `RecordFileParser Loading version .* record file: .*`
-   `AccountBalancesDownloader Downloaded .* BALANCE signatures for node .*`
-   `AccountBalancesFileLoader Starting processing account balances file`
-   `AccountBalancesFileLoader Starting processing account balances file`
-   `BalanceFileParser Completed processing .* balance files`

## State changes

The mirror importer service, if shutdown cleanly will log `Shutting down.....` message
