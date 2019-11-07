# Operations

## File Layout

- `/usr/lib/mirror-node` - Binaries
- `/usr/etc/mirror-node` - Configuration files
  - `application.yml`
- `/var/lib/mirror-node` - Data
  - `addressbook.bin` - The current address book in use
  - `accountBalances` - The downloaded balance and signature files
  - `recordstreams` - The downloaded record and signature files
- `/etc/systemd/system/mirror-node.service` - systemd service definitions

## Starting

```
sudo systemctl start mirror-node.service
```

## Stopping

```
sudo systemctl stop mirror-node.service
```

## Monitoring logs (tailing)

```
sudo journalctl -fu mirror-node.service
```

## Normal system logs

Normal output should include several frequent INFO level messages such as:

- `RecordFileDownloader Downloaded .* RCD signatures for node .*`
- `RecordFileParser Loading version .* record file: .*`
- `AccountBalancesDownloader Downloaded .* BALANCE signatures for node .*`
- `AccountBalancesFileLoader Starting processing account balances file`
- `AccountBalancesFileLoader Starting processing account balances file`
- `BalanceFileParser Completed processing .* balance files`

## State changes

The mirror node service, if shutdown cleanly will log `Shutting down.....` message
