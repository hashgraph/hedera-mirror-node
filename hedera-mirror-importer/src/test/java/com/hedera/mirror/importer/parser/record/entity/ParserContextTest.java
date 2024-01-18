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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserContextTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final ParserContext parserContext = new ParserContext();

    @Test
    void add() {
        assertThatThrownBy(() -> parserContext.add(null)).isInstanceOf(NullPointerException.class);
        var domain = domainBuilder.entity().get();
        parserContext.add(domain);

        assertThat(getItems()).containsExactly(List.of(domain));
    }

    @Test
    void addAll() {
        assertThatThrownBy(() -> parserContext.addAll(null)).isInstanceOf(NullPointerException.class);
        var domain1 = List.of(domainBuilder.entity().get());
        var domain2 = List.of(domainBuilder.token().get());
        parserContext.addAll(domain1);
        parserContext.addAll(domain2);
        assertThat(getItems()).containsExactlyInAnyOrder(domain1, domain2);
    }

    @Test
    void clear() {
        parserContext.add(domainBuilder.entity().get());
        parserContext.clear();
        assertThat(getItems()).isEmpty();
    }

    @Test
    void get() {
        assertThat(parserContext.get(Entity.class, 1L)).isNull();
        assertThatThrownBy(() -> parserContext.get(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parserContext.get(Entity.class, null)).isInstanceOf(NullPointerException.class);

        var domain = domainBuilder.entity().get();
        parserContext.merge(domain.getId(), domain, (a, b) -> a);
        assertThat(parserContext.get(Entity.class, domain.getId())).isEqualTo(domain);
    }

    @Test
    void getAll() {
        assertThat(parserContext.get(Entity.class)).isEmpty();
        assertThatThrownBy(() -> parserContext.get(null)).isInstanceOf(NullPointerException.class);

        var domain = domainBuilder.entity().get();
        parserContext.add(domain);
        assertThat(parserContext.get(Entity.class)).containsExactly(domain);
    }

    @Test
    void remove() {
        parserContext.remove(Entity.class);
        assertThatThrownBy(() -> parserContext.remove(null)).isInstanceOf(NullPointerException.class);
        var domain = domainBuilder.entity().get();
        parserContext.add(domain);
        parserContext.remove(Entity.class);
        assertThat(getItems()).containsExactly(List.of());
    }

    private Collection<Collection<?>> getItems() {
        var items = new ArrayList<Collection<?>>();
        parserContext.forEach(items::add);
        return items;
    }
}
