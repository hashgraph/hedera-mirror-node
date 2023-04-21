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

import EntityId from '../entityId';
import * as utils from '../utils.js';
import {entityTypes} from '../constants.js';
import {proto} from '@hashgraph/proto';
import _ from 'lodash';

/**
 * Contract actions view model
 */
class ContractActionViewModel {
  static callTypes = _.invert(proto.ContractActionType);

  static resultDataTypes = {
    11: 'OUTPUT',
    12: 'REVERT_REASON',
    13: 'ERROR',
  };

  static callOperationTypes = {
    0: 'UNKNOWN',
    1: 'CALL',
    2: 'CALLCODE',
    3: 'DELEGATECALL',
    4: 'STATICCALL',
    5: 'CREATE',
    6: 'CREATE2',
  };

  /**
   * Constructs contractAction view model
   *callTypes
   * @param {ContractAction} contractAction
   */
  constructor(contractAction) {
    const callerId = EntityId.parse(contractAction.caller);
    const callOperationType = contractAction.callOperationType || 0;
    const recipientId = contractAction.recipientAccount || contractAction.recipientContract;
    const recipient = EntityId.parse(recipientId, {isNullable: true});
    const recipientIsAccount = !!contractAction.recipientAccount;

    this.call_depth = contractAction.callDepth;
    this.call_operation_type = ContractActionViewModel.callOperationTypes[callOperationType];
    this.call_type = ContractActionViewModel.callTypes[contractAction.callType];
    this.caller = callerId.toString();
    this.caller_type = contractAction.callerType;
    this.from = callerId.toEvmAddress();
    this.gas = contractAction.gas;
    this.gas_used = contractAction.gasUsed;
    this.index = contractAction.index;
    this.input = utils.toHexStringNonQuantity(contractAction.input);
    this.recipient = recipient.toString();
    this.recipient_type = recipientId && (recipientIsAccount ? entityTypes.ACCOUNT : entityTypes.CONTRACT);
    this.result_data = utils.toHexStringNonQuantity(contractAction.resultData);
    this.result_data_type = ContractActionViewModel.resultDataTypes[contractAction.resultDataType];
    this.timestamp = utils.nsToSecNs(contractAction.consensusTimestamp);
    this.to = contractAction.recipientAddress
      ? utils.toHexString(contractAction.recipientAddress, true, 40)
      : recipient.toEvmAddress();
    this.value = contractAction.value;
  }
}

export default ContractActionViewModel;
