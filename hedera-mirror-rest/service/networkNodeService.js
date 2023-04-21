/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import BaseService from './baseService';
import config from '../config.js';
import {
  AddressBook,
  AddressBookEntry,
  AddressBookServiceEndpoint,
  NetworkNode,
  NetworkStake,
  NodeStake,
} from '../model';
import {OrderSpec} from '../sql';
import entityId from '../entityId.js';

/**
 * Network node business model
 */
class NetworkNodeService extends BaseService {
  static unreleasedSupplyAccounts = (column) =>
    config.network.unreleasedSupplyAccounts
      .map((range) => {
        const from = entityId.parse(range.from).getEncodedId();
        const to = entityId.parse(range.to).getEncodedId();
        return `(${column} >= ${from} and ${column} <= ${to})`;
      })
      .join(' or ');

  // add node filter
  static networkNodesBaseQuery = `with ${AddressBook.tableAlias} as (
      select ${AddressBook.START_CONSENSUS_TIMESTAMP}, ${AddressBook.END_CONSENSUS_TIMESTAMP}, ${AddressBook.FILE_ID}
      from ${AddressBook.tableName} where ${AddressBook.FILE_ID} = $1
      order by ${AddressBook.START_CONSENSUS_TIMESTAMP} desc limit 1
    ),
    ${NodeStake.tableAlias} as (
      select ${NodeStake.MAX_STAKE}, ${NodeStake.MIN_STAKE}, ${NodeStake.NODE_ID}, ${NodeStake.REWARD_RATE},
             ${NodeStake.STAKE}, ${NodeStake.STAKE_NOT_REWARDED}, ${NodeStake.STAKE_REWARDED},
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
      ${NodeStake.getFullName(NodeStake.REWARD_RATE)},
      coalesce(${NodeStake.getFullName(NodeStake.STAKE)}, ${AddressBookEntry.getFullName(
    AddressBookEntry.STAKE
  )}) as stake,
      ${NodeStake.getFullName(NodeStake.STAKE_NOT_REWARDED)},
      ${NodeStake.getFullName(NodeStake.STAKE_REWARDED)},
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

  static networkStakeQuery = `select ${NetworkStake.MAX_STAKING_REWARD_RATE_PER_HBAR},
         ${NetworkStake.NODE_REWARD_FEE_DENOMINATOR},
         ${NetworkStake.NODE_REWARD_FEE_NUMERATOR},
         ${NetworkStake.STAKE_TOTAL},
         ${NetworkStake.STAKING_PERIOD},
         ${NetworkStake.STAKING_PERIOD_DURATION},
         ${NetworkStake.STAKING_PERIODS_STORED},
         ${NetworkStake.STAKING_REWARD_FEE_DENOMINATOR},
         ${NetworkStake.STAKING_REWARD_FEE_NUMERATOR},
         ${NetworkStake.STAKING_REWARD_RATE},
         ${NetworkStake.STAKING_START_THRESHOLD}
      from ${NetworkStake.tableName}
      where ${NetworkStake.CONSENSUS_TIMESTAMP} =
            (select max(${NetworkStake.CONSENSUS_TIMESTAMP}) from ${NetworkStake.tableName})`;

  static networkSupplyQuery = `
    with unreleased as (
      select coalesce(sum(balance), 0) as unreleased_supply
      from entity
      where (${NetworkNodeService.unreleasedSupplyAccounts('id')})
    )
    select unreleased_supply, (select max(consensus_end) from record_file) as consensus_timestamp
    from unreleased`;

  static networkSupplyByTimestampQuery = `
    select coalesce(sum(balance), 0) as unreleased_supply, max(consensus_timestamp) as consensus_timestamp
    from account_balance
    where (${NetworkNodeService.unreleasedSupplyAccounts('account_id')})
      and consensus_timestamp = (
      select max(consensus_timestamp)
      from account_balance_file abf
      where `;

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

  getNetworkStake = async () => {
    const row = await super.getSingleRow(NetworkNodeService.networkStakeQuery, [], 'getNetworkStake');
    return row && new NetworkStake(row);
  };

  getSupply = async (conditions, params) => {
    let query = NetworkNodeService.networkSupplyQuery;

    if (conditions.length > 0) {
      query = `${NetworkNodeService.networkSupplyByTimestampQuery} ${conditions.join(' and ')})`;
    }

    return await super.getSingleRow(query, params, 'getSupply');
  };
}

export default new NetworkNodeService();
