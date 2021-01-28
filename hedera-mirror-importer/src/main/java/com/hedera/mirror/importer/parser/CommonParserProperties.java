package com.hedera.mirror.importer.parser;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser")
public class CommonParserProperties {

    @NotNull
    private Collection<TransactionFilter> exclude = new ArrayList<>();

    @NotNull
    private Collection<TransactionFilter> include = new ArrayList<>();

    public Predicate<TransactionFilterFields> getFilter() {
        return includeFilter().and(excludeFilter());
    }

    private Predicate<TransactionFilterFields> excludeFilter() {
        if (exclude.isEmpty()) {
            return t -> true;
        }
        return exclude.stream()
                .map(f -> f.getFilter().negate())
                .reduce(a -> true, Predicate::and);
    }

    private Predicate<TransactionFilterFields> includeFilter() {
        if (include.isEmpty()) {
            return t -> true;
        }
        return include.stream()
                .map(f -> f.getFilter())
                .reduce(a -> false, Predicate::or);
    }

    @Data
    @Validated
    public static class TransactionFilter {

        @NotNull
        private Collection<EntityId> entity = new LinkedHashSet<>();

        @NotNull
        private Collection<TransactionTypeEnum> transaction = new LinkedHashSet<>();

        public Predicate<TransactionFilterFields> getFilter() {
            return t -> (matches(t) && matches(t.getEntity()));
        }

        private boolean matches(TransactionFilterFields t) {
            if (transaction.isEmpty()) {
                return true;
            }

            return transaction.contains(t.getTransactionType());
        }

        private boolean matches(EntityId e) {
            if (entity.isEmpty()) {
                return true;
            }

            return e != null && entity.contains(e);
        }
    }
}
