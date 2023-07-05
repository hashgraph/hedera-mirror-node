/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import {addHexPrefix, toHexStringNonQuantity, toHexStringQuantity} from '../utils';
import ContractResultViewModel from "./contractResultViewModel";

/**
 * Detailed contract results view model
 */
class DetailedContractResultViewModel extends ContractResultViewModel {
    /**
     * Detailed constructs contractResult view model
     *
     * @param {ContractResult} cr
     */
    constructor(cr) {
        super(cr);
        this.access_list = toHexStringNonQuantity(cr.accessList);
        this.block_gas_used = cr.blockGasUsed;
        this.block_hash = addHexPrefix(cr.blockHash);
        this.block_number = cr.blockNumber;
        this.chain_id = toHexStringQuantity(cr.chainId);
        this.gas_price = toHexStringQuantity(cr.gasPrice);
        this.max_fee_per_gas = toHexStringQuantity(cr.maxFeePerGas);
        this.max_priority_fee_per_gas = toHexStringQuantity(cr.maxPriorityFeePerGas);
        this.nonce = cr.etNonce;
        this.r = toHexStringNonQuantity(cr.r);
        this.s = toHexStringNonQuantity(cr.s);
        this.transaction_index = cr.transactionIndex;
        this.type = cr.transactionType;
        this.v = cr.v;
    }
}

export default DetailedContractResultViewModel;
