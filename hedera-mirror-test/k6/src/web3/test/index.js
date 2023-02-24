/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as contractCallAllowance from './contractCallAllowance.js';
import * as contractCallApproved from './contractCallApproved.js';
import * as contractCallApprovedForAll from './contractCallApprovedForAll.js';
import * as contractCallBalance from './contractCallBalance.js';
import * as contractCallBalanceOf from './contractCallBalanceOf.js';
import * as contractCallDecimals from './contractCallDecimals.js';
import * as contractCallFungibleTokenInfo from './contractCallFungibleTokenInfo.js';
import * as contractCallIdentifier from './contractCallIdentifier.js';
import * as contractCallIsFrozen from './contractCallIsFrozen.js';
import * as contractCallIsKyc from './contractCallIsKyc.js';
import * as contractCallIsToken from './contractCallIsToken.js';
import * as contractCallMultiply from './contractCallMultiply.js';
import * as contractCallName from './contractCallName.js';
import * as contractCallNonFungibleTokenInfo from './contractCallNonFungibleTokenInfo.js';
import * as contractCallOwnerOf from './contractCallOwnerOf.js';
import * as contractCallReceive from './contractCallReceive.js';
import * as contractCallSender from './contractCallSender.js';
import * as contractCallSymbol from './contractCallSymbol.js';
import * as contractCallTokenCustomFees from './contractCallTokenCustomFees.js';
import * as contractCallTokenDefaultFreezeStatus from './contractCallTokenDefaultFreezeStatus.js';
import * as contractCallTokenDefaultKycStatus from './contractCallTokenDefaultKycStatus.js';
import * as contractCallTokenExpiryInfo from './contractCallTokenExpiryInfo.js';
import * as contractCallTokenInfo from './contractCallTokenInfo.js';
import * as contractCallTokenKey from './contractCallTokenKey.js';
import * as contractCallTokenType from './contractCallTokenType.js';
import * as contractCallTokenURI from './contractCallTokenURI.js';
import * as contractCallTotalSupply from './contractCallTotalSupply.js';

// add test modules here
const tests = {
  contractCallAllowance,
  contractCallApproved,
  contractCallApprovedForAll,
  contractCallBalance,
  contractCallBalanceOf,
  contractCallDecimals,
  contractCallFungibleTokenInfo,
  contractCallIdentifier,
  contractCallIsFrozen,
  contractCallIsKyc,
  contractCallIsToken,
  contractCallMultiply,
  contractCallName,
  contractCallNonFungibleTokenInfo,
  contractCallOwnerOf,
  contractCallReceive,
  contractCallSender,
  contractCallSymbol,
  contractCallTokenCustomFees,
  contractCallTokenDefaultFreezeStatus,
  contractCallTokenDefaultKycStatus,
  contractCallTokenExpiryInfo,
  contractCallTokenInfo,
  contractCallTokenKey,
  contractCallTokenType,
  contractCallTokenURI,
  contractCallTotalSupply,
};

const {funcs, options, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests);

export {funcs, options, scenarioDurationGauge, scenarios};
