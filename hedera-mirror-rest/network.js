/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const config = require('./config');
const constants = require('./constants');
const entityId = require('./entityId');
const {NotFoundError} = require('./errors/notFoundError');
const utils = require('./utils');

const totalSupply = 5000000000000000000n;
const unreleasedSupplyAccounts = config.network.unreleasedSupplyAccounts.map((a) =>
  entityId.fromString(a).getEncodedId()
);

const formatResponse = (result) => {
  const {rows} = result;

  if (rows.length !== 1 || !rows[0].consensus_timestamp) {
    throw new NotFoundError('Not found');
  }

  const unreleasedSupply = BigInt(rows[0].unreleased_supply);
  const releasedSupply = totalSupply - unreleasedSupply;

  // Convert numbers to string since Express doesn't support BigInt
  return {
    released_supply: `${releasedSupply}`,
    timestamp: utils.nsToSecNs(rows[0].consensus_timestamp),
    total_supply: `${totalSupply}`,
  };
};

/**
 * Handler function for /network/supply API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise} Promise for PostgreSQL query
 */
const getSupply = async (req, res) => {
  utils.validateReq(req);
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'abf.consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });

  const sqlQuery = `
    select sum(balance) as unreleased_supply, max(consensus_timestamp) as consensus_timestamp
    from account_balance
    where consensus_timestamp = (
      select max(consensus_timestamp)
      from account_balance_file abf
      where ${tsQuery !== '' ? tsQuery : '1=1'}
    )
      and account_id in (${unreleasedSupplyAccounts});`;

  const query = utils.convertMySqlStyleQueryToPostgres(sqlQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getSupply query: ${query} ${JSON.stringify(tsParams)}`);
  }

  return pool.queryQuietly(query, tsParams).then((result) => {
    res.locals[constants.responseDataLabel] = formatResponse(result);
    logger.debug(`getSupply returning ${result.rows.length} entries`);
  });
};

module.exports = {
  getSupply,
};
