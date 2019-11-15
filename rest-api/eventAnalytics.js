/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
const utils = require('./utils.js');
const transactions = require('./transactions.js');

/**
 * Handler function for /eventanalytics/ API.
 * @param {Request} req Request object
 * @param {Response} res Response object
 * @return {} None.
 */
const getEventAnalytics = function(req, res) {
  logger.debug('Client: [' + req.ip + '] URL: ' + req.originalUrl);

  let ret = {
    createdEventsCount: [],
    consensusEventsCount: [],
    latencies: [],
    sysAppTxsCountInConsensusEvents: []
  };

  // getCreatedEventsCount
  const [getCreatedEventsCountTsQuery, getCreatedEventsCountTsParams] = utils.parseParams(req, 'timestamp', [
    'created_timestamp_ns'
  ]);

  const [getCreatedEventsCountNodeQuery, getCreatedEventsCountNodeParams] = utils.parseParams(req, 'node.id', [
    'creator_node_id'
  ]);

  const getCreatedEventsCountQuery =
    'select creator_node_id, count(*)\n' +
    ' from t_events\n' +
    (getCreatedEventsCountTsQuery === '' ? '' : ' where ' + getCreatedEventsCountTsQuery) +
    '\n' +
    (getCreatedEventsCountNodeQuery === ''
      ? ''
      : (getCreatedEventsCountTsQuery === '' ? ' where ' : ' and ') + getCreatedEventsCountNodeQuery) +
    '\n' +
    ' group by creator_node_id';

  const getCreatedEventsCountParams = getCreatedEventsCountTsParams.concat(getCreatedEventsCountNodeParams);

  const pgCreatedEventsCountQuery = utils.convertMySqlStyleQueryToPostgress(
    getCreatedEventsCountQuery,
    getCreatedEventsCountParams
  );
  logger.debug(
    'getEventAnalytics: getCreatedEventsCount query: ' +
      pgCreatedEventsCountQuery +
      JSON.stringify(getCreatedEventsCountParams)
  );
  // Execute query & get a promise
  const getCreatedEventsCountPromise = pool.query(pgCreatedEventsCountQuery, getCreatedEventsCountParams);

  //getConsensusEventsCount and Latencies
  const [getConsensusEventsCountTsQuery, getConsensusEventsCountTsParams] = utils.parseParams(req, 'timestamp', [
    'consensus_timestamp_ns'
  ]);

  const [getConsensusEventsCountNodeQuery, getConsensusEventsCountNodeParams] = utils.parseParams(req, 'node.id', [
    'creator_node_id'
  ]);

  const getConsensusEventsCountQuery =
    'select creator_node_id, avg(latency_ns), count(*)\n' +
    ' from t_events\n' +
    (getConsensusEventsCountTsQuery === '' ? '' : ' where ' + getConsensusEventsCountTsQuery) +
    '\n' +
    (getConsensusEventsCountNodeQuery === ''
      ? ''
      : (getConsensusEventsCountTsQuery === '' ? ' where ' : ' and ') + getConsensusEventsCountNodeQuery) +
    '\n' +
    ' group by creator_node_id';

  const getConsensusEventsCountParams = getConsensusEventsCountTsParams.concat(getConsensusEventsCountNodeParams);

  const pgConsensusEventsCountQuery = utils.convertMySqlStyleQueryToPostgress(
    getConsensusEventsCountQuery,
    getConsensusEventsCountParams
  );
  logger.debug(
    'getEventAnalytics: getConsensusEventsCount query: ' +
      pgConsensusEventsCountQuery +
      JSON.stringify(getConsensusEventsCountParams)
  );
  // Execute query & get a promise
  const getConsensusEventsCountPromise = pool.query(pgConsensusEventsCountQuery, getConsensusEventsCountParams);

  //getSysAppTxsCountInConsensusEventsForNode
  const [
    getSysAppTxsCountInConsensusEventsCountTsQuery,
    getSysAppTxsCountInConsensusEventsCountTsParams
  ] = utils.parseParams(req, 'timestamp', ['consensus_timestamp_ns']);

  const [
    getSysAppTxsCountInConsensusEventsCountNodeQuery,
    getSysAppTxsCountInConsensusEventsCountNodeParams
  ] = utils.parseParams(req, 'node.id', ['creator_node_id']);

  const getSysAppTxsCountInConsensusEventsCountQuery =
    'select sum(platform_tx_count) as platform_tx_cnt, ' +
    'sum(app_tx_count) as app_tx_cnt\n' +
    ' from t_events\n' +
    (getSysAppTxsCountInConsensusEventsCountTsQuery === ''
      ? ''
      : ' where ' + getSysAppTxsCountInConsensusEventsCountTsQuery) +
    '\n' +
    (getSysAppTxsCountInConsensusEventsCountNodeQuery === ''
      ? ''
      : (getSysAppTxsCountInConsensusEventsCountTsQuery === '' ? ' where ' : ' and ') +
        getSysAppTxsCountInConsensusEventsCountNodeQuery);

  const getSysAppTxsCountInConsensusEventsCountParams = getSysAppTxsCountInConsensusEventsCountTsParams.concat(
    getSysAppTxsCountInConsensusEventsCountNodeParams
  );

  const pgSysAppTxsCountInConsensusEventsCountQuery = utils.convertMySqlStyleQueryToPostgress(
    getSysAppTxsCountInConsensusEventsCountQuery,
    getSysAppTxsCountInConsensusEventsCountParams
  );
  logger.debug(
    'getEventAnalytics: getSysAppTxsCountInConsensusEventsForNode query: ' +
      pgSysAppTxsCountInConsensusEventsCountQuery +
      JSON.stringify(getSysAppTxsCountInConsensusEventsCountParams)
  );
  // Execute query & get a promise
  const getSysAppTxsCountInConsensusEventsCountPromise = pool.query(
    pgSysAppTxsCountInConsensusEventsCountQuery,
    getSysAppTxsCountInConsensusEventsCountParams
  );

  // After all 3 of the promises (for all three queries) have been resolved...
  Promise.all([
    getCreatedEventsCountPromise,
    getConsensusEventsCountPromise,
    getSysAppTxsCountInConsensusEventsCountPromise
  ])
    .then(function(values) {
      const getCreatedEventsCountResults = values[0];
      const getConsensusEventsCountResults = values[1];
      const getSysAppTxsCountInConsensusEventsCountResults = values[2];

      // Process the results of t_account_balance_refresh_time query
      if (getCreatedEventsCountResults.rows.length < 1) {
        res.status(500).send('Error: Could not get CreatedEventsCounResults');
        return;
      }
      ret.createdEventsCount = getCreatedEventsCountResults.rows;

      // Process the results of t_account_balance_refresh_time query
      if (getConsensusEventsCountResults.rows.length < 1) {
        res.status(500).send('Error: Could not get ConsensusEventsCountResults');
        return;
      }
      for (let row of getConsensusEventsCountResults.rows) {
        ret.latencies.push({
          creator_node_id: row.creator_node_id,
          average_latency: row.avg
        });
        ret.consensusEventsCount.push({
          creator_node_id: row.creator_node_id,
          count: row.count
        });
      }

      // Process the results of t_account_balance_refresh_time query
      if (getSysAppTxsCountInConsensusEventsCountResults.rows.length !== 1) {
        res.status(500).send('Error: Could not get SysAppTxsCountInConsensusEventsCountResults');
        return;
      }
      ret.sysAppTxsCountInConsensusEvents = {
        transaction_count: {
          platform: getSysAppTxsCountInConsensusEventsCountResults.rows[0].platform_tx_cnt,
          app: getSysAppTxsCountInConsensusEventsCountResults.rows[0].app_tx_cnt
        }
      };

      logger.debug('getEventAnalytics returning ' + JSON.stringify(ret));
      res.json(ret);
    })
    .catch(err => {
      logger.error('getEventAnalytics error: ' + JSON.stringify(err.stack));
      res.status(500).send('Internal error');
    });
};

module.exports = {
  getEventAnalytics: getEventAnalytics
};
