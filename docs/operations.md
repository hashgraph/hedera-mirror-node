# Operations

## File Layout

-   `/usr/lib/mirror-importer` - Binaries
-   `/usr/etc/mirror-importer` - Configuration files
    -   `application.yml`
-   `/var/lib/mirror-importer` - Data
    -   `addressbook.bin` - The current address book in use
    -   `accountBalances` - The downloaded balance and signature files
    -   `recordstreams` - The downloaded record and signature files
-   `/etc/systemd/system/mirror-importer.service` - systemd service definitions

## Starting

```
sudo systemctl start mirror-importer.service
```

## Stopping

```
sudo systemctl stop mirror-importer.service
```

## Monitoring logs (tailing)

```
sudo journalctl -fu mirror-importer.service
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
