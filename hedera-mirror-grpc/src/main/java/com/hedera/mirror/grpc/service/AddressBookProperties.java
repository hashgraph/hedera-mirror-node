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

package com.hedera.mirror.grpc.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.grpc.addressbook")
public class AddressBookProperties {

    @DurationMin(millis = 500L)
    @NotNull
    private Duration cacheExpiry = Duration.ofSeconds(60);

    @Min(0)
    private long cacheSize = 50L;

    @DurationMin(millis = 100L)
    @NotNull
    private Duration maxPageDelay = Duration.ofMillis(250L);

    @DurationMin(millis = 100L)
    @NotNull
    private Duration minPageDelay = Duration.ofMillis(100L);

    @Min(1)
    private int pageSize = 10;
}
