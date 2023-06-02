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

package com.hedera.mirror.importer.migration;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

@Data
public class MigrationProperties {

    private int checksum = 1;

    private boolean enabled = true;

    @NotNull
    private Map<String, String> params = new CaseInsensitiveMap<>();
}
