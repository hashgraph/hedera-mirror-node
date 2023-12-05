/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.common;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import lombok.Data;
import org.hyperledger.besu.datatypes.Address;

@Data
public class PrecompileContext {
    public static final String PRECOMPILE_CONTEXT = "PrecompileContext";

    /** Boolean flag which determines whether the transaction is estimate gas or not */
    private boolean estimate;

    /** HTS Precompile field keeping the precompile which is going to be executed at a given point in time */
    private Precompile precompile;

    /** HTS Precompile field keeping the gas amount, which is going to be charged for a given precompile execution */
    private long gasRequirement;

    /** HTS Precompile field keeping the transactionBody needed for a given precompile execution */
    private TransactionBody.Builder transactionBody;

    /** HTS Precompile field keeping the sender address of the account that initiated a given precompile execution */
    private Address senderAddress = Address.ZERO;

    public AccountID getSenderAddressAsProto() {
        return EntityIdUtils.accountIdFromEvmAddress(senderAddress);
    }
}
