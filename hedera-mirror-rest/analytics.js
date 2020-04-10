/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */
'use strict';

/**
 * Handler function for /analytics API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getAnalytics = function (req, res) {
  logger.debug('--------------------  getAnalytics --------------------');
  logger.debug('Client: [' + req.ip + '] URL: ' + req.originalUrl);

  let nodeData = {};
  let analytics = [];

  let query =
    'select tx_time_hour, enode.entity_num as node_account,\n' +
    '    count(enode.entity_num) as numTransactions\n' +
    ' from t_transactions t, t_entities enode\n' +
    ' where t.node_account_id = enode.id\n' +
    ' group by 1,2\n' +
    ' order by tx_time_hour\n' +
    ' desc';

  logger.debug('getAnalytics query-1: ' + query);

  pool.query(query, (error, results) => {
    if (error) {
      logger.error('getAnalytics error: ' + JSON.stringify(error, Object.getOwnPropertyNames(error)));
      res.json({
        analytics: [],
      });
      return;
    }

    results.rows.forEach(function (item) {
      let seconds = new Date(item.tx_time_hour).getTime() / 1000;
      if (!(seconds in nodeData)) {
        nodeData[seconds] = [];
      }

      nodeData[seconds].push({
        hour: item.tx_time_hour,
        seconds: seconds,
        node_account: item.node_account,
        numTransactions: item.numTransactions,
      });
    });

    let query2 =
      'select hour, txCount as txnCount, txPerSec as txnPerSec,\n' +
      '    txPerMin as txnPerMin, txPerHour as txnPerHour, totalHbar,\n' +
      '    txTo99 as txnTo99, hBarTo99 as hbarTo99,\n' +
      '    txFrom99 as txnFrom99, hBarFrom99 as hbarFrom99,\n' +
      '    hBar99InOut as hbarInOut99\n' +
      ' from t_analytics_99';
    pool.query(query2, (error, results) => {
      if (error) {
        logger.error('getAnalytics error: ' + JSON.stringify(error, Object.getOwnPropertyNames(error)));
        res.json({
          analytics: [],
        });
        return;
      }

      results.rows.forEach(function (item) {
        // This assumes that the timezone on this machine is UTC
        let seconds = new Date(item.hour).getTime() / 1000;
        item['seconds'] = seconds;
        item['nodeData'] = nodeData[seconds];

        analytics.push(item);
      });
      logger.debug('getAnalytics returning ' + analytics.length + ' entries');

      res.json({
        analytics: analytics,
      });
    });
  });
};

module.exports = {
  getAnalytics: getAnalytics,
};
