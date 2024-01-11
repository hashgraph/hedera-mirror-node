/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;

/**
 * Record used by {@link com.hedera.services.store.contracts.precompile.HTSPrecompiledContract}
 * and {@link com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile}
 * for getting decoded tokenId and serialNumber from transaction input
 * @param tokenId
 * @param serialNumber
 */
public record ApproveDecodedNftInfo(TokenID tokenId, BigInteger serialNumber) {}
