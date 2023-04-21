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

import * as utils from '../utils';
import ContractViewModel from './contractViewModel';

/**
 * Contract with bytecode view model
 */
class ContractBytecodeViewModel extends ContractViewModel {
  /**
   * Constructs contract view model
   *
   * @param {Contract} contract
   * @param {Entity} entity
   */
  constructor(contract, entity) {
    super(contract, entity);
    this.bytecode = utils.addHexPrefix(contract.bytecode);
    this.runtime_bytecode = utils.toHexString(contract.runtimeBytecode, true);
  }
}

export default ContractBytecodeViewModel;
