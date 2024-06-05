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

package com.hedera.mirror.restjava.controller;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.rest.model.Topic;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.mapper.TopicMapper;
import com.hedera.mirror.restjava.service.EntityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
@RestController
public class TopicController {

    private final EntityService entityService;
    private final TopicMapper topicMapper;

    @GetMapping(value = "/{id}")
    Topic getTopic(@PathVariable EntityIdNumParameter id) {
        var entity = entityService.findById(id.id());

        if (entity.getType() != EntityType.TOPIC) {
            throw new EntityNotFoundException("Topic not found: " + id.id());
        }

        return topicMapper.map(entity);
    }
}
