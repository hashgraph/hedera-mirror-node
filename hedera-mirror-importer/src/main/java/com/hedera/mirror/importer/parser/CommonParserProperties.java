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

package com.hedera.mirror.importer.parser;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser")
public class CommonParserProperties {

    @Min(8192)
    private int bufferSize = 32768; // tested max byte size of buffer used by PGCopyOutputStream

    @NotNull
    private Collection<TransactionFilter> exclude = new ArrayList<>();

    @NotNull
    private Collection<TransactionFilter> include = new ArrayList<>();

    @Min(1)
    private int tempTableBufferSize; // Default value set in application.yml, size in MB

    @Getter(lazy = true)
    private final Predicate<TransactionFilterFields> filter = includeFilter().and(excludeFilter());

    public boolean hasFilter() {
        return (!exclude.isEmpty()) || (!include.isEmpty());
    }

    private Predicate<TransactionFilterFields> excludeFilter() {
        if (exclude.isEmpty()) {
            return t -> true;
        }
        return exclude.stream().map(f -> f.getFilter().negate()).reduce(a -> true, Predicate::and);
    }

    private Predicate<TransactionFilterFields> includeFilter() {
        if (include.isEmpty()) {
            return t -> true;
        }
        return include.stream().map(TransactionFilter::getFilter).reduce(a -> false, Predicate::or);
    }

    @Data
    @Validated
    public static class TransactionFilter {

        @NotNull
        private Collection<EntityId> entity = new LinkedHashSet<>();

        @NotNull
        private Collection<TransactionType> transaction = new LinkedHashSet<>();

        public Predicate<TransactionFilterFields> getFilter() {
            return t -> (matches(t) && matches(t.getEntities()));
        }

        private boolean matches(TransactionFilterFields t) {
            if (transaction.isEmpty() || t.getTransactionType() == null) {
                return true;
            }

            return transaction.contains(t.getTransactionType());
        }

        private boolean matches(Collection<EntityId> entities) {
            if (entity.isEmpty()) {
                return true;
            }

            return entities != null && CollectionUtils.containsAny(entity, entities);
        }
    }
}
