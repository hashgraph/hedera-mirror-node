/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.client;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;

public abstract class EncoderDecoderFacade {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    protected ObjectMapper mapper;

    protected CompiledSolidityArtifact readCompiledArtifact(InputStream in) throws IOException {
        return mapper.readValue(in, CompiledSolidityArtifact.class);
    }

    protected InputStream getResourceAsStream(String resourcePath) throws IOException {
        return resourceLoader.getResource(resourcePath).getInputStream();
    }

    protected byte[] encodeDataToByteArray(ContractResource resource, SelectorInterface method, Object... args) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        ByteBuffer byteBuffer = function.encodeCallWithArgs(args);
        return byteBuffer.array();
    }

    private Tuple decodeResult(ContractResource resource, SelectorInterface method, final byte[] result) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        return function.decodeReturn(result);
    }
}
