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

package com.hedera.mirror.importer.parser.record.entity;

import static com.hedera.mirror.importer.parser.record.entity.DomainClassComparator.ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.DissociateTokenTransfer;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class DomainClassComparatorTest {

    private static final DomainClassComparator COMPARATOR = new DomainClassComparator();

    @Test
    void compare() {
        assertThat(COMPARATOR.compare(Entity.class, TokenAccount.class)).isNegative();
        assertThat(COMPARATOR.compare(TransactionSignature.class, TokenAccount.class))
                .isNegative();
        assertThat(COMPARATOR.compare(Token.class, TokenAccount.class)).isNegative();
        assertThat(COMPARATOR.compare(TokenAccount.class, Token.class)).isPositive();
        assertThat(COMPARATOR.compare(Token.class, Token.class)).isZero();
        assertThat(COMPARATOR.compare(TokenAccount.class, TokenAccount.class)).isZero();
        assertThat(COMPARATOR.compare(TokenAccount.class, DissociateTokenTransfer.class))
                .isNegative();
    }

    @Test
    void compareOrdered() {
        var randomOrder = new ArrayList<>(ORDER);
        Collections.shuffle(randomOrder);
        var sortedOrder = new TreeSet<>(COMPARATOR);
        sortedOrder.addAll(randomOrder);
        assertThat(sortedOrder).containsExactlyElementsOf(ORDER);
    }

    @Test
    void sortedMap() {
        var map = new TreeMap<Class<?>, Integer>(COMPARATOR);
        map.put(TokenAccount.class, 0);
        map.put(Entity.class, 1);
        map.put(CryptoTransfer.class, 2);
        map.put(DissociateTokenTransfer.class, 3);
        map.put(Contract.class, 4);
        assertThat(map.keySet())
                .containsExactly(
                        Contract.class,
                        CryptoTransfer.class,
                        Entity.class,
                        TokenAccount.class,
                        DissociateTokenTransfer.class);
    }
}
