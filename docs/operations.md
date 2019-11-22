# Operations

## File/deployment layout

* `/usr/lib/mirror-node` - binaries
* `/usr/etc/mirror-node` - configuration files
  * `config.json`
  * `0.0.102` - address book
* `/var/lib/mirror-node` - data \(downloaded record streams and account balances\)
* `/etc/systemd/system/mirror-node.service` - systemd service definitions

## Stopping the Mirror Node

Stopping the mirror node involves placing a `stop` file in its working directory and waiting for the process to shutdown.

```text
sudo touch /usr/lib/mirror-node/stop
sleep 10
sudo systemctl stop mirror-node.service
```

## Starting the Mirror Node

Starting the mirror node involves removing any `stop` file in its working directory and starting the service.

```text
sudo rm -f /usr/lib/mirror-node/stop
sudo systemctl start mirror-node.service
```

## Monitoring logs \(tailing\)

```text
sudo journalctl -fu mirror-node.service
```

## Normal system logs

Normal output should include several frequent INFO level messages such as:

* `RecordFileDownloader Downloaded .* RCD signatures for node .*`
* `RecordFileParser Loading version .* record file: .*`
* `AccountBalancesDownloader Downloaded .* BALANCE signatures for node .*`
* `AccountBalancesFileLoader Starting processing account balances file`
* `AccountBalancesFileLoader Starting processing account balances file`
* `BalanceFileLogger Completed processing .* balance files`

## State changes

The mirror node service, if shutdown cleanly will log either `Stop file found, stopping` or `Shutting down` message

