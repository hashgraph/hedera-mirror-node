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

const testutils = require('./testutils.js');

/**
 * This is a mock database for unit testing.
 * It captures SQL query that would have been sent to the database, and parses the
 * relevant parameters such as timestamp, account.id from it.
 */
class Pool {
    /**
     * Dummy constructor
     */
    constructor(dbParams) {
    }

    /**
     * This Pool.query method gets called when the code tries to query the database.
     * This method mocks the real database. It parses the SQL query and extracts the
     * filter clauses of the query, and returns those as part of the query response. 
     * This code can be enhanced to return dummy data for transactions, balances, or 
     * queries. Right now, we return a blank arrays
     * @param {String} sqlquery The SQL query string for postgreSQL
     * @param {Array} sqlparams The array of values for positional parameters  
     * @return {Promise} promise Javascript promise that gets resolved with the response
     *                          with parsed query parameters.
     */
    query(sqlquery, sqlparams) {
        // Since this is a generic mock DB, first find out if this is a query 
        // for transactions, balances, or accounts
        let callerFile;
        try {
            callerFile = (new Error().stack).split("at ")[2].match(/\/(\w+?).js:/)[1];
        } catch (err) {
            callerFile = 'unknown';
        }

        // To parse the sql parameters, we need the 'order by' param used
        let orderprefix = '';
        switch (callerFile) {
            case 'transactions':
                orderprefix = 'consensus_ns';
                break;
            case 'balances':
                orderprefix = 'account_num';
                break;
            case 'accounts':
                orderprefix = 'coalesce\\(ab.account_num, e.entity_num\\)'
                break;
            default:
                    break;
        }

        // Parse the SQL query
        let parsedparams = testutils.parseSqlQueryAndParams(sqlquery, sqlparams, orderprefix);

        // console.log (`IN QUERY: sqlquery: ${sqlquery}, parsed: `);
        // for (const item of parsedparams) {
        //     console.log (JSON.stringify(item));
        // }
        // console.log ("----- done parsed ----")

        let promise = new Promise(function(resolve, reject) {
            resolve ({
                rows: [],
                sqlQuery: {
                    query: sqlquery,
                    params: sqlparams,
                    parsedparams: parsedparams
                }
            });
        })

        return (promise);
    }
}

module.exports = Pool
