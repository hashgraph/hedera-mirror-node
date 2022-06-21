/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const {AddressBook, AddressBookEntry, AddressBookServiceEndpoint, NetworkNode, NodeStake} = require('../model');
const BaseService = require('./baseService');
const {OrderSpec} = require('../sql');

/**
 * Network node business model
 */
class NetworkNodeService extends BaseService {
  // add node filter
  static networkNodesBaseQuery = `with ${AddressBook.tableAlias} as (
      select ${AddressBook.START_CONSENSUS_TIMESTAMP}, ${AddressBook.END_CONSENSUS_TIMESTAMP}, ${AddressBook.FILE_ID}
      from ${AddressBook.tableName} where ${AddressBook.FILE_ID} = $1
      order by ${AddressBook.START_CONSENSUS_TIMESTAMP} desc limit 1
    ),
    ${NodeStake.tableAlias} as (
      select ${NodeStake.MAX_STAKE}, ${NodeStake.MIN_STAKE}, ${NodeStake.NODE_ID}, ${NodeStake.STAKE},
             ${NodeStake.STAKE_NOT_REWARDED}, ${NodeStake.STAKE_REWARDED}, ${NodeStake.STAKE_TOTAL},
             ${NodeStake.STAKING_PERIOD}
      from ${NodeStake.tableName}
      where ${NodeStake.CONSENSUS_TIMESTAMP} =
        (select max(${NodeStake.CONSENSUS_TIMESTAMP}) from ${NodeStake.tableName})
    )
    select ${AddressBookEntry.getFullName(AddressBookEntry.DESCRIPTION)},
      ${AddressBookEntry.getFullName(AddressBookEntry.MEMO)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ACCOUNT_ID)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_CERT_HASH)},
      ${AddressBookEntry.getFullName(AddressBookEntry.PUBLIC_KEY)},
      ${AddressBook.getFullName(AddressBook.FILE_ID)},
      ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)},
      ${AddressBook.getFullName(AddressBook.END_CONSENSUS_TIMESTAMP)},
      ${NodeStake.getFullName(NodeStake.MAX_STAKE)},
      ${NodeStake.getFullName(NodeStake.MIN_STAKE)},
      ${NodeStake.getFullName(NodeStake.STAKE)},
      ${NodeStake.getFullName(NodeStake.STAKE_NOT_REWARDED)},
      ${NodeStake.getFullName(NodeStake.STAKE_REWARDED)},
      ${NodeStake.getFullName(NodeStake.STAKE_TOTAL)},
      ${NodeStake.getFullName(NodeStake.STAKING_PERIOD)},
      coalesce((
        select jsonb_agg(jsonb_build_object(
        '${AddressBookServiceEndpoint.IP_ADDRESS_V4}', ${AddressBookServiceEndpoint.IP_ADDRESS_V4},
        '${AddressBookServiceEndpoint.PORT}', ${AddressBookServiceEndpoint.PORT}
        ) order by ${AddressBookServiceEndpoint.IP_ADDRESS_V4} asc, ${AddressBookServiceEndpoint.PORT} asc)
        from ${AddressBookServiceEndpoint.tableName} ${AddressBookServiceEndpoint.tableAlias}
        where ${AddressBookServiceEndpoint.getFullName(AddressBookServiceEndpoint.CONSENSUS_TIMESTAMP)} =
          ${AddressBookEntry.getFullName(AddressBookEntry.CONSENSUS_TIMESTAMP)} and
          ${AddressBookServiceEndpoint.getFullName(AddressBookServiceEndpoint.NODE_ID)} =
          ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)}
      ), '[]') as service_endpoints
    from ${AddressBookEntry.tableName} ${AddressBookEntry.tableAlias}
    join ${AddressBook.tableAlias} on ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)} =
      ${AddressBookEntry.getFullName(AddressBookEntry.CONSENSUS_TIMESTAMP)}
    left join ${NodeStake.tableAlias} on ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)} =
      ${NodeStake.getFullName(NodeStake.NODE_ID)}`;

  getNetworkNodes = async (whereConditions, whereParams, order, limit) => {
    const [query, params] = this.getNetworkNodesWithFiltersQuery(whereConditions, whereParams, order, limit);

    const rows = await super.getRows(query, params, 'getNetworkNodes');
    return rows.map((x) => new NetworkNode(x));
  };

  getNetworkNodesWithFiltersQuery = (whereConditions, whereParams, nodeOrder, limit) => {
    const params = whereParams;
    params.push(limit);
    const query = [
      NetworkNodeService.networkNodesBaseQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      `${super.getOrderByQuery(OrderSpec.from(AddressBookEntry.getFullName(AddressBookEntry.NODE_ID), nodeOrder))}`,
      super.getLimitQuery(params.length),
    ].join('\n');

    return [query, params];
  };
}

module.exports = new NetworkNodeService();
