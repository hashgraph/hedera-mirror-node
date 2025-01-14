/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@Named
class FileDataBuilder extends AbstractEntityBuilder<FileData, FileData.FileDataBuilder> {
    private static final List<Integer> ALWAYS_HEX_FILES = List.of(111, 112);

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("fileData", HEX_OR_BASE64_CONVERTER);

    FileDataBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> {
            var entities = specSetup.fileData();

            if (entities == null) {
                return entities;
            }

            return entities.stream()
                    .peek(entity -> {
                        var entityId = (Integer) entity.get("entity_id");
                        if (ALWAYS_HEX_FILES.contains(entityId)) {
                            var cleanedFileData = Optional.ofNullable((entity.get("file_data")))
                                    .map(fileDataOptional -> {
                                        if (fileDataOptional instanceof String fileDataString) {
                                            return fileDataString.replaceAll("[^0-9a-fA-F]", "");
                                        }
                                        return fileDataOptional;
                                    })
                                    .orElse(null);
                            entity.put("file_data", cleanedFileData);
                        }
                    })
                    .toList();
        };
    }

    @Override
    protected FileData.FileDataBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return FileData.builder().transactionType(17);
    }

    @Override
    protected FileData getFinalEntity(FileData.FileDataBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
