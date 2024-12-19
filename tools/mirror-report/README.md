# Mirror Node Report Tool

This tool is a CLI tool that queries the REST API for an account and produces a report showing any transfers to or from
the account and its resulting balance.

## Install

First ensure Node and NPM are installed. If not using MacOS or Linux, please see Node's
install [instructions](https://nodejs.org/en/download/package-manager/current).

## MacOS

```shell
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
nvm install 22
npm install -g @hashgraph/mirror-report
```

### Windows

```shell
winget install Schniz.fnm
fnm env --use-on-cd | Out-String | Invoke-Expression
```

## Running

To generate the report, use the `mirror` command line tool from the installation section.

```shell
$ mirror --help
Usage: mirror [options] [command]

Options:
  -h, --help        display help for command

Commands:
  report [options]  Generate a report for specific accounts
  help [command]    display help for command
```

Run the `mirror report` sub-command to generate the report. Pass at least one account you want to include in the report.
By default, it generates a report for the current date. Pass the `--date YYYY-MM-DD` argument to customize the report
date.

```shell
$ mirror report --help
Usage: mirror report [options]

Generate a report for the given accounts.

Options:
  -a, --account <accountId...>   The accounts to include in the report
  -d, --date <YYYY-MM-DD>        The day the report should cover (default: "2024-12-17")
  -n, --network <network>        The Hedera network to connect to (default: "mainnet")
  -h, --help                     display help for command
```

Example execution :

```shell
$ mirror report -a 0.0.1000 -a 0.0.1001 -d 2024-11-29 -n testnet
2024-12-17T04:52:04.353Z Generating testnet report for the given accounts: ["0.0.1000","0.0.1001"]
2024-12-17T04:52:04.353Z Invoking https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1732838400
2024-12-17T04:52:04.643Z Starting balance of 1300000002 for account 0.0.1000 at 1732837599.823821194
2024-12-17T04:52:04.643Z Invoking https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1732837599.823821194&timestamp=lt:1732924800
2024-12-17T04:52:04.719Z Invoking https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1001?timestamp=1732838400
2024-12-17T04:52:04.800Z Starting balance of 197103815708295 for account 0.0.1001 at 1732837599.823821194
2024-12-17T04:52:04.800Z Invoking https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1001&limit=100&order=asc&timestamp=gt:1732837599.823821194&timestamp=lt:1732924800
2024-12-17T04:52:04.875Z Generated report successfully at report-2024-11-29.csv with 1 entries

$ cat report-2024-11-29.csv
timestamp,sender,receiver,fees,amount,balance
1732901875.430169000,0.0.5190744,0.0.1000,0,100000000,1400000002
```
