/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.v030;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class Version030AddressChecks implements AddressChecks {

    @Override
    public boolean isPresent(@NonNull Address address, @NonNull MessageFrame frame) {
        return false;
    }

    @Override
    public boolean isSystemAccount(@NonNull Address address) {
        return false;
    }

    @Override
    public boolean isNonUserAccount(@NonNull Address address) {
        return false;
    }

    @Override
    public boolean isHederaPrecompile(@NonNull Address address) {
        return false;
    }
}
