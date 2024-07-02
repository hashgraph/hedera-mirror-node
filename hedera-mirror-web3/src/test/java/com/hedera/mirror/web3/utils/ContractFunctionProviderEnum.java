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

package com.hedera.mirror.web3.utils;

import com.hedera.mirror.web3.viewmodel.BlockType;
import org.springframework.lang.Nullable;

public interface ContractFunctionProviderEnum {

    /**
     * @return the function name
     */
    String getName();

    /**
     * @return the function parameters
     */
    Object[] getFunctionParameters();

    /**
     * @return the expected result fields or {@code null} if not applicable
     */
    @Nullable
    default Object[] getExpectedResultFields() {
        return null;
    }

    /**
     * @return the expected error message or {@code null} if not applicable
     */
    @Nullable
    default String getExpectedErrorMessage() {
        return null;
    }

    /**
     * @return the block type to use for the call
     */
    default BlockType getBlock() {
        return BlockType.LATEST;
    }
}
