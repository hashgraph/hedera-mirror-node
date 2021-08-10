package com.hedera.mirror.monitor.properties;/*
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

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
public class ScenarioPropertiesAggregatorImpl implements ScenarioPropertiesAggregator {

    private static final Pattern LIST_PATTERN_END = Pattern
            .compile("([A-Za-z0-9_]+)\\.[0-9]+");

    @Override
    public Map<String, Object> aggregateProperties(Map<String, String> properties) {
        //List properties are loaded in as property.0, property.1, etc.  This puts them into a list for deserialization.
        Map<String, Object> correctedProperties = new HashMap<>();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Matcher matcher = LIST_PATTERN_END.matcher(entry.getKey());
            if (matcher.matches()) {
                String propertyName = matcher.group(1);
                log.debug("Converting property {} into list {}", entry.getKey(), propertyName);
                correctedProperties
                        .merge(propertyName, Lists.newArrayList(entry.getValue()), (existingList, newList) -> {
                            ((List) existingList).addAll((List) newList);
                            return existingList;
                        });
            } else {
                correctedProperties.put(entry.getKey(), entry.getValue());
            }
        }

        return correctedProperties;
    }
}
