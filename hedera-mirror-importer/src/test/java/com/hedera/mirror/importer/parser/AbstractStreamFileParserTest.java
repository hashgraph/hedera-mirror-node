/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({OutputCaptureExtension.class, MockitoExtension.class})
public abstract class AbstractStreamFileParserTest<F extends StreamFile<?>, T extends StreamFileParser<F>> {

    protected T parser;

    protected ParserProperties parserProperties;

    protected abstract T getParser();

    protected abstract F getStreamFile();

    protected abstract StreamFileRepository<F, ?> getStreamFileRepository();

    protected abstract void mockDbFailure(ParserException e);

    @BeforeEach()
    public void before() {
        parser = getParser();
        parserProperties = parser.getProperties();
        parserProperties.setEnabled(true);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void parse(boolean startAndEndSame) {
        // given
        F streamFile = getStreamFile();
        if (startAndEndSame) {
            streamFile.setConsensusStart(streamFile.getConsensusStart());
        }

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, true, false);
    }

    @Test
    void disabled() {
        // given
        parserProperties.setEnabled(false);
        F streamFile = getStreamFile();

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, false, false);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void alreadyExists(boolean startAndEndSame) {
        // given
        F streamFile = getStreamFile();
        if (startAndEndSame) {
            streamFile.setConsensusStart(streamFile.getConsensusStart());
        }
        when(getStreamFileRepository().findLatest()).thenReturn(Optional.of(streamFile));

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, false, false);
    }

    @Test
    void failureShouldRollback(CapturedOutput output) {
        // given
        F streamFile = getStreamFile();
        var e = new ParserException("boom");
        mockDbFailure(e);

        // when
        assertThatThrownBy(() -> parser.parse(streamFile)).isEqualTo(e);

        // then
        assertParsed(streamFile, false, true);
        assertThat(output.getOut()).contains("Error parsing file").contains(e.getMessage());
    }

    protected void assertParsed(F streamFile, boolean parsed, boolean dbError) {
        assertThat(streamFile.getBytes()).isNotNull();
        assertThat(streamFile.getItems()).isNotNull();
    }
}
