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

import fetch from 'node-fetch';
import JSONBigFactory from 'json-bigint';
import {ReportFile} from "./reportfile.js";

const feeAccount = /^\d+\.\d+\.(98|800|801|[3-9]|[1-3][0-9])$/;
const JSONBig = JSONBigFactory({useNativeBigInt: true});
const prefix = '/api/v1';

const log = (message) => {
  const timestamp = new Date().toISOString();
  console.log(`${timestamp} ${message}`);
};

const toIsoString = timestamp => {
  if (!timestamp) {
    return '';
  }

  const parts = timestamp.split('.');
  const seconds = parts.length >= 1 ? parseInt(parts[0]) : 0;
  const nanos = parts.length > 1 ? parts[1] : "000000000";
  const date = new Date(seconds * 1000).toISOString().slice(0, 20);
  return `${date}${nanos}Z`;
};

const restApi = async (network, path) => {
  const prefixedPath = path.startsWith(prefix) ? path : prefix + path;
  const url = `https://${network}.mirrornode.hedera.com${prefixedPath}`;
  log(`Invoking ${url}`);

  return fetch(url, {compress: true, headers: {'Accept': 'application/json'}})
  .then(async res => JSONBig.parse(await res.text()))
  .then(res => {
    if (res._status != null) {
      log(`Error invoking URL: ${JSONBig.stringify(res._status.messages)}`);
      return null;
    }
    return res;
  })
  .catch(e => log(`Error invoking URL: ${e}`));
};

export const report = async (options) => {
  log(`Running report with options: ${JSON.stringify(options)}`);
  const timestampStart = new Date(options.fromDate).getTime() / 1000;
  const timestampEnd = new Date(options.toDate).getTime() / 1000;
  let reportFile = new ReportFile();

  for (const account of options.account) {
    const accountResponse = await restApi(options.network, `/accounts/${account}?timestamp=${timestampStart}`);

    if (!accountResponse) {
      log(`Unable to find account ${account}`);
      continue;
    }

    let balance = BigInt(accountResponse?.balance?.balance);
    const balanceTimestamp = accountResponse?.balance?.timestamp
    let next = `/transactions?account.id=${account}&limit=100&order=asc&timestamp=gt:${balanceTimestamp}&timestamp=lt:${timestampEnd}`;
    log(`Starting balance of ${balance} for account ${account} at ${balanceTimestamp}`);

    while (next) {
      const transactionsResponse = await restApi(options.network, next);

      if (!transactionsResponse) {
        log(`Unable to find any transactions for account ${account}`);
        continue;
      }

      for (const transaction of transactionsResponse.transactions) {
        const payer = transaction.transaction_id.startsWith(account);
        const fees = payer ? BigInt(transaction.charged_tx_fee) : 0n;
        let amount = 0n;
        let other = "fee accounts";

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
          let receiver = amount < 0n ? other : account;
          const dateTime = toIsoString(transaction.consensus_timestamp);
          const hashscan = `https://hashscan.io/${options.network}/transaction/${transaction.consensus_timestamp}`;
          reportFile.append(`${dateTime},${sender},${receiver},${fees},${amount},${balance},${hashscan}\n`);
        }
      }

      next = transactionsResponse?.links?.next;
    }

    if (options.separate) {
      reportFile.write(`report-${options.fromDate}-${account}.csv`);
    }
  }

  if (!options.separate) {
    reportFile.write(`report-${options.fromDate}.csv`);
  }
};
