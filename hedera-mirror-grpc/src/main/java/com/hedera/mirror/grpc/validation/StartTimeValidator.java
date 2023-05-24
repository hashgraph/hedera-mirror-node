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

package com.hedera.mirror.grpc.validation;

import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;

public class StartTimeValidator implements ConstraintValidator<StartTime, TopicMessageFilter> {

    @Override
    public void initialize(StartTime startTime) {
        // Nothing needs to be done with startTime upon initialize
    }

    @Override
    public boolean isValid(TopicMessageFilter topicMessageFilter, ConstraintValidatorContext context) {
        if (topicMessageFilter.getStartTime() == null) {
            return false;
        }
        return !topicMessageFilter.getStartTime().isAfter(Instant.now());
    }
}
