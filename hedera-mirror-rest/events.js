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

const utils = require('./utils.js');

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Array} row One row of the SQL query result
 * @return {Array} row Processed row
 */
const processRow = function(row) {
  row.consensus_timestamp = utils.nsToSecNs(row.consensus_timestamp_ns);
  row.created_timestamp = utils.nsToSecNs(row.created_timestamp_ns);
  delete row.consensus_timestamp_ns;
  delete row.created_timestamp_ns;

  row.id = Number(row.id);
  row.self_parent_id = Number(row.self_parent_id);
  row.other_parent_id = Number(row.other_parent_id);
  row.creator_node_id = Number(row.creator_node_id);
  row.other_node_id = Number(row.other_node_id);
  row.consensus_order = Number(row.consensus_order);
  row.creator_seq = Number(row.creator_seq);
  row.other_seq = Number(row.other_seq);
  row.self_parent_generation = Number(row.self_parent_generation);
  row.other_parent_generation = Number(row.other_parent_generation);
  row.generation = Number(row.generation);
  row.latency_ns = Number(row.latency_ns);

  return row;
};

/**
 * Handler function for /events API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getEvents = function(req) {
  // Parse the filter parameters for timestamp, nodequery and pagination
  const [tsQuery, tsParams] = utils.parseParams(req, 'timestamp', ['tev.consensus_timestamp_ns'], 'timestamp_ns');

  const [nodeQuery, nodeParams] = utils.parseParams(req, 'creatornode', ['tev.creator_node_id']);

  const {limitQuery, limitParams, order, limit} = utils.parseLimitAndOrderParams(req);

  let sqlParams = tsParams.concat(nodeParams).concat(limitParams);

  let sqlQuery =
    'select  *\n' +
    ' from t_events tev\n' +
    ' where ' +
    [tsQuery, nodeQuery].map(q => (q === '' ? '1=1' : q)).join(' and ') +
    'order by tev.consensus_timestamp_ns ' +
    order +
    '\n' +
    limitQuery;

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);

  logger.debug('getEvents query: ' + pgSqlQuery + JSON.stringify(sqlParams));

  // Execute query
  return pool.query(pgSqlQuery, sqlParams).then(results => {
    let ret = {
      events: [],
      links: {
        next: null
      }
    };

    for (let row of results.rows) {
      ret.events.push(processRow(row));
    }

    const anchorSecNs = results.rows.length > 0 ? results.rows[results.rows.length - 1].consensus_timestamp : 0;

    ret.links = {
      next: utils.getPaginationLink(req, ret.events.length !== limit, 'timestamp', anchorSecNs, order)
    };

    logger.debug('getEvents returning ' + ret.events.length + ' entries');

    return ret;
  });
};

/**
 * Handler function for /events/:event_id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneEvent = function(req, res) {
  logger.debug('--------------------  getEvents --------------------');
  logger.debug('Client: [' + req.ip + '] URL: ' + req.originalUrl);

  const eventQuery = 'id = ?\n';
  const sqlParams = [req.params.id];

  let sqlQuery = 'select  *\n' + ' from t_events\n' + ' where ' + eventQuery;

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);

  logger.debug('getOneEvent query: ' + pgSqlQuery + JSON.stringify(sqlParams));

  // Execute query
  pool.query(pgSqlQuery, sqlParams, (error, results) => {
    let ret = {
      events: []
    };

    if (error) {
      logger.error('getOneEvent error: ' + JSON.stringify(error, Object.getOwnPropertyNames(error)));
      res.status(404).send('Not found');
      return;
    }

    for (let row of results.rows) {
      ret.events.push(processRow(row));
    }

    if (ret.events.length === 0) {
      res.status(404).send('Not found');
      return;
    }

    logger.debug('getOneEvent returning ' + ret.events.length + ' entries');
    res.json(ret);
  });
};

module.exports = {
  getEvents: getEvents,
  getOneEvent: getOneEvent
};
