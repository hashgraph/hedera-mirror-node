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

import * as common from '../../lib/common.js';

// import test modules
import * as accounts from './accounts.js';
import * as accountsBalanceFalse from './accountsBalanceFalse.js';
import * as accountsBalanceFalsePubkey from './accountsBalanceFalsePubkey.js';
import * as accountsBalanceGt0 from './accountsBalanceGt0.js';
import * as accountsBalanceGt0Pubkey from './accountsBalanceGt0Pubkey.js';
import * as accountsBalanceNe from './accountsBalanceNe.js';
import * as accountsId from './accountsId.js';
import * as accountsIdNe from './accountsIdNe.js';
import * as balances from './balances.js';
import * as contracts from './contracts.js';
import * as contractsId from './contractsId.js';
import * as contractsIdResults from './contractsIdResults.js';
import * as contractsIdResultsLogs from './contractsIdResultsLogs.js';
import * as contractsIdResultsTimestamp from './contractsIdResultsTimestamp.js';
import * as networkSupply from './networkSupply.js';
import * as schedules from './schedules.js';
import * as schedulesAccount from './schedulesAccount.js';
import * as schedulesId from './schedulesId.js';
import * as tokens from './tokens.js';
import * as tokensFungibleCommon from './tokensFungibleCommon.js';
import * as tokensId from './tokensId.js';
import * as tokensIdBalances from './tokensIdBalances.js';
import * as tokensNfts from './tokensNfts.js';
import * as tokensNftsSerial from './tokensNftsSerial.js';
import * as tokensNftsSerialTransactions from './tokensNftsSerialTransactions.js';
import * as tokensNonFungibleUnique from './tokensNonFungibleUnique.js';
import * as tokensTokenIdNe from './tokensTokenIdNe.js';
import * as topicsIdMessages from './topicsIdMessages.js';
import * as topicsIdMessagesSequence from './topicsIdMessagesSequence.js';
import * as topicsIdMessagesSequenceQueryParam from './topicsIdMessagesSequenceQueryParam.js';
import * as topicsMessagesTimestamp from './topicsMessagesTimestamp.js';
import * as transactions from './transactions.js';
import * as transactionsId from './transactionsId.js';

// add test modules here
const tests = {
  accounts,
  accountsBalanceFalse,
  accountsBalanceFalsePubkey,
  accountsBalanceGt0,
  accountsBalanceGt0Pubkey,
  accountsBalanceNe,
  accountsId,
  accountsIdNe,
  balances,
  contracts,
  contractsId,
  contractsIdResults,
  contractsIdResultsLogs,
  contractsIdResultsTimestamp,
  networkSupply,
  schedules,
  schedulesAccount,
  schedulesId,
  tokens,
  tokensFungibleCommon,
  tokensId,
  tokensIdBalances,
  tokensNfts,
  tokensNftsSerial,
  tokensNftsSerialTransactions,
  tokensNonFungibleUnique,
  tokensTokenIdNe,
  topicsIdMessages,
  topicsIdMessagesSequence,
  topicsIdMessagesSequenceQueryParam,
  topicsMessagesTimestamp,
  transactions,
  transactionsId,
};

const {funcs, options, scenarioDurationGauge} = common.getSequentialTestScenarios(tests);

export {funcs, options, scenarioDurationGauge};
