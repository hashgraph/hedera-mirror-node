/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txn.token;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnpauseLogicTest {
    private final Id id = new Id(1, 2, 3);

    @Mock
    private Token token;

    @Mock
    private Token unpausedToken;

    @Mock
    private Store store;

    private UnpauseLogic subject;

    @BeforeEach
    void setup() {
        subject = new UnpauseLogic();
    }

    @Test
    void followsHappyPathForUnpausing() {
        // given:
        given(token.getId()).willReturn(id);
        given(store.loadPossiblyPausedToken(id.asEvmAddress())).willReturn(token);
        given(token.setPaused(false)).willReturn(unpausedToken);

        // when:
        subject.unpause(token.getId(), store);

        // then:
        verify(token).setPaused(false);
        verify(store).updateToken(unpausedToken);
    }
}
