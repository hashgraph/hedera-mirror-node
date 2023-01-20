package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.TESTNET;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ContextConfiguration;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ContextConfiguration(classes = {DummyMigrationTest.DummyMigration.class})
@Tag("migration")
class DummyMigrationTest extends IntegrationTest {

    private final DummyMigration dummyMigration;

    private final MirrorProperties mirrorProperties;


    @BeforeEach
    void setup() {
        mirrorProperties.setNetwork(TESTNET);
    }

    @Test
    void checksum() {
        assertThat(dummyMigration.getChecksum()).isEqualTo(5);
    }

    static class DummyMigration extends RepeatableMigration {

        @Getter
        private boolean migrated = false;


        @Lazy
        public DummyMigration(MirrorProperties mirrorProperties) {
            super(mirrorProperties.getMigration());
        }

        @Override
        protected void doMigrate() {
            migrated = true;
        }

        @Override
        public String getDescription() {
            return "Dummy migration";
        }
    }
}
