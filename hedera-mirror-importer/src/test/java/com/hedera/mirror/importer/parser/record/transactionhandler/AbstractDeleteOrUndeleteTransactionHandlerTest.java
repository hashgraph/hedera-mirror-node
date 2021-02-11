package com.hedera.mirror.importer.parser.record.transactionhandler;

/*
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

import java.util.List;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.Entities;

@RequiredArgsConstructor
abstract class AbstractDeleteOrUndeleteTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final boolean deleteOrUndelete;

    AbstractDeleteOrUndeleteTransactionHandlerTest() {
        this(true);
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        String description = deleteOrUndelete ? "delete entity transaction, expect entity deleted" :
                "undelete entity transaction, expect entity undeleted";
        Entities expected = new Entities();
        expected.setDeleted(deleteOrUndelete);
        Entities input = new Entities();
        input.setDeleted(!deleteOrUndelete);
        return List.of(
                UpdateEntityTestSpec.builder()
                        .description(description)
                        .expected(expected)
                        .input(input)
                        .recordItem(getRecordItem(getDefaultTransactionBody().build()))
                        .build()
        );
    }
}
