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

const _ = require('lodash');

const {AddressBook, AddressBookEntry, AddressBookServiceEndpoint} = require('../model');
const BaseService = require('./baseService');

/**
 * CryptoAllowance business model
 */
class AddressBookEntryService extends BaseService {
  static networkNodeseQuery = `with ${AddressBook.tableAlias} as (
    select ${AddressBook.START_CONSENSUS_TIMESTAMP}, ${AddressBook.FILE_ID}
    from ${AddressBook.tableName} where ${AddressBook.FILE_ID} = $1
   )
   select ${AddressBookEntry.getFullName(AddressBookEntry.DESCRIPTION)}, 
    ${AddressBook.getFullName(AddressBook.FILE_ID)}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.MEMO)}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ACCOUN_ID)}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.NODE_CERT_HASH)},
    jsonb_agg(jsonb_build_object(
      '${AddressBookServiceEndpoint.IP_ADDRESS_V4}', ${AddressBookServiceEndpoint.IP_ADDRESS_V4}, 
      '${AddressBookServiceEndpoint.PORT}', ${AddressBookServiceEndpoint.PORT}
      ) 
      order by ${AddressBookServiceEndpoint.PORT} desc
    ) as service_endpoints, ${AddressBookEntry.getFullName(AddressBookEntry.PUBLIC_KEY)}
   from ${AddressBookEntry.tableName} ${AddressBookEntry.tableAlias}
   left join ${AddressBook.tableAlias} on 
    ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)} = ${AddressBookEntry.getFullName(
    AddressBookEntry.CONSENSUS_TIMESTAMP
  )}
   left join ${AddressBookServiceEndpoint.tableName} ${AddressBookServiceEndpoint.tableAlias} on 
    ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)} = ${AddressBookServiceEndpoint.getFullName(
    AddressBookServiceEndpoint.CONSENSUS_TIMESTAMP
  )}
   group by ${AddressBookEntry.getFullName(AddressBookEntry.DESCRIPTION)}, ${AddressBook.getFullName(
    AddressBook.FILE_ID
  )}, ${AddressBookEntry.getFullName(AddressBookEntry.MEMO)}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)}, ${AddressBookEntry.getFullName(
    AddressBookEntry.NODE_ACCOUN_ID
  )}, 
    ${AddressBookEntry.getFullName(AddressBookEntry.NODE_CERT_HASH)}, ${AddressBookEntry.getFullName(
    AddressBookEntry.PUBLIC_KEY
  )}`;

  async getNetworkNodes(conditions, initParams, order, limit) {
    const [query, params] = this.getNetworkNodesWithFiltersQuery(conditions, initParams, order, limit);

    const rows = await super.getRows(query, params, 'getNetworkNodes');
    return rows.map((ca) => new CryptoAllowance(ca));
  }

  getNetworkNodesWithFiltersQuery(whereConditions, whereParams, spenderOrder, limit) {
    const params = whereParams;
    const query = [
      AddressBookEntryService.networkNodeseQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(CryptoAllowance.SPENDER, spenderOrder),
      super.getLimitQuery(params.length + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }
}

module.exports = new AddressBookEntryService();
