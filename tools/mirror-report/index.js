/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#!/usr/bin/env node

import fetch from 'node-fetch';
import fs from 'fs';
import { program } from 'commander';

const feeAccount = /^\d+\.\d+\.(98|800|801|[3-9]|[1-3][0-9])$/;
const prefix = '/api/v1';

const log = (message) => {
    const timestamp = new Date().toISOString();
    console.log(`${timestamp} ${message}`);
};

const restApi = async (network, path) => {
    const prefixedPath = path.startsWith(prefix) ? path : prefix + path;
    const url = `https://${network}.mirrornode.hedera.com${prefixedPath}`;
    log(`Invoking ${url}`);
    return fetch(url, {compress: true, headers: {'Accept': 'application/json'}})
        .then(res => res.json())
        .then(res => {
            if (res._status != null) {
               log(`Error invoking URL: ${JSON.stringify(res._status.messages)}`);
               return null;
            }
            return res;
        })
        .catch(e => log(`Error invoking URL: ${e}`));
};

const report = async (options) => {
    const filename = `report-${options.date}.csv`;
    const timestampStart = new Date(options.date).getTime() / 1000;
    const timestampEnd = timestampStart + 86400;
    let csv = "timestamp,sender,receiver,fees,amount,balance\n";
    log(`Generating ${filename} for ${options.network}: ${JSON.stringify(options.accounts)}`);

    for (const account of options.accounts) {
        const accountResponse = await restApi(options.network, `/accounts/${account}?timestamp=${timestampStart}`);
        if (!accountResponse) {
           log(`Unable to find account ${account}`);
           continue;
        }

        let balance = BigInt(accountResponse?.balance?.balance);
        const balanceTimestamp = accountResponse?.balance?.timestamp
        let next = `/transactions?account.id=${account}&limit=100&order=asc&timestamp=gt:${balanceTimestamp}&timestamp=lt:${timestampEnd}`;
        log(`Starting balance for account ${account} at time ${balanceTimestamp}: ${balance}`);

        while (next) {
            const transactionsResponse = await restApi(options.network, next);
            if (!transactionsResponse) {
               log(`Unable to get the transactions for account ${account}`);
               continue;
            }

            transactionsResponse.transactions.forEach(transaction => {
                const payer = transaction.transaction_id.startsWith(account);
                const fees = payer ? transaction.charged_tx_fee : 0;
                let amount = 0n;
                let other;

                transaction?.transfers.forEach(transfer => {
                    if (transfer.account === account) {
                        amount = BigInt(transfer.amount);
                    } else if (!feeAccount.test(transfer.account)) {
                        other = transfer.account;
                    }
                });

                balance += amount;
                const consensusTimestampSeconds = transaction.consensus_timestamp.split('.')[0];

                if (consensusTimestampSeconds >= timestampStart) {
                    const sender = amount < 0n ? account : other;
                    const receiver =  amount < 0n ? other : account;
                    csv += `${transaction.consensus_timestamp},${sender},${receiver},${fees},${amount},${balance}\n`;
                }
            });

            next = transactionsResponse?.links?.next;
        }

        fs.writeFile(filename, csv, (err) => {if (err) throw err;});
    }
};

const today = new Date().toISOString().slice(0, 10);

program.command('report')
    .description('Generate a report for specific accounts')
    .requiredOption('-a, --accounts <accountId...>', 'The accounts to consider for report generation')
    .requiredOption('-d, --date <YYYY-MM-DD>', 'The day the report should be generated', today)
    .requiredOption('-n, --network <network>', 'The Hedera network to connect to', 'mainnet')
    .action(report);

program.parse();

