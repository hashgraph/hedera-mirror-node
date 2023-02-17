/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as accounts from './accounts.js';
import * as accountsBalanceFalse from './accountsBalanceFalse.js';
import * as accountsBalanceFalsePubkey from './accountsBalanceFalsePubkey.js';
import * as accountsBalanceGt0 from './accountsBalanceGt0.js';
import * as accountsBalanceGt0Pubkey from './accountsBalanceGt0Pubkey.js';
import * as accountsBalanceNe from './accountsBalanceNe.js';
import * as accountsCryptoAllowance from './accountsCryptoAllowance.js';
import * as accountsCryptoAllowanceSpender from './accountsCryptoAllowanceSpender.js';
import * as accountsId from './accountsId.js';
import * as accountsIdNe from './accountsIdNe.js';
import * as accountsNfts from './accountsNfts.js';
import * as accountsTokens from './accountsTokens.js';
import * as accountsTokenAllowance from './accountsTokenAllowance.js';
import * as balances from './balances.js';
import * as balancesAccount from './balancesAccount.js';
import * as blocks from './blocks.js';
import * as blocksNumber from './blocksNumber.js';
import * as contracts from './contracts.js';
import * as contractsId from './contractsId.js';
import * as contractsIdResults from './contractsIdResults.js';
import * as contractsIdResultsLogs from './contractsIdResultsLogs.js';
import * as contractsIdResultsTimestamp from './contractsIdResultsTimestamp.js';
import * as contractsIdState from './contractsIdState.js';
import * as contractsResults from './contractsResult.js';
import * as contractsResultsId from './contractsResultsId.js';
import * as contractsResultsIdActions from './contractsResultsIdActions.js';
import * as contractsResultsLogs from './contractsResultsLogs.js';
import * as networkExchangeRate from './networkExchangeRate.js';
import * as networkFees from './networkFees.js';
import * as networkNodes from './networkNodes.js';
import * as networkStake from './networkStake.js';
import * as networkSupply from './networkSupply.js';
import * as rampUp from './rampUp.js';
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
import * as transactionsAccountId from './transactionsAccountId.js';
import * as transactionsHash from './transactionsHash.js';
import * as transactionsId from './transactionsId.js';
import * as transactionsIdStateproof from './transactionsIdStateproof.js';
import * as transactionsTransactionTypeAscending from './transactionsTransactionTypeAscending.js';

// add test modules here
const tests = {
  accounts,
  accountsBalanceFalse,
  accountsBalanceFalsePubkey,
  accountsBalanceGt0,
  accountsBalanceGt0Pubkey,
  accountsBalanceNe,
  accountsCryptoAllowance,
  accountsCryptoAllowanceSpender,
  accountsId,
  accountsIdNe,
  accountsNfts,
  accountsTokens,
  accountsTokenAllowance,
  balances,
  balancesAccount,
  blocks,
  blocksNumber,
  contracts,
  contractsId,
  contractsIdResults,
  contractsIdResultsLogs,
  contractsIdResultsTimestamp,
  contractsIdState,
  contractsResults,
  contractsResultsId,
  contractsResultsIdActions,
  contractsResultsLogs,
  networkExchangeRate,
  networkFees,
  networkNodes,
  networkStake,
  networkSupply,
  rampUp,
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
  transactionsAccountId,
  transactionsHash,
  transactionsId,
  transactionsIdStateproof,
  transactionsTransactionTypeAscending,
};

const {funcs, options, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests);

export {funcs, options, scenarioDurationGauge, scenarios};
