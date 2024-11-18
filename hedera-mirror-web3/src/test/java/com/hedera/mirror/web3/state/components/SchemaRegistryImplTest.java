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

package com.hedera.mirror.web3.state.components;

import static java.util.Collections.EMPTY_MAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.state.merkle.SchemaApplicationType;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.info.NetworkInfo;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaRegistryImplTest {

    private final String serviceName = "testService";
    private final SemanticVersion previousVersion = new SemanticVersion(0, 46, 0, "", "");

    @Mock
    private MirrorNodeState mirrorNodeState;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private Schema schema;

    @Mock
    private MapWritableStates writableStates;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private SchemaApplications schemaApplications;

    @Mock
    private Codec<String> mockCodec;

    private Configuration config;
    private SchemaRegistryImpl schemaRegistry;

    @BeforeEach
    void initialize() {
        schemaRegistry = new SchemaRegistryImpl(schemaApplications);
        config = new ConfigProviderImpl().getConfiguration();
    }

    @Test
    void testRegisterSchema() {
        schemaRegistry.register(schema);
        SortedSet<Schema> schemas = schemaRegistry.getSchemas();
        assertThat(schemas).contains(schema);
    }

    @Test
    void testMigrateWithNoSchemas() {
        schemaRegistry.migrate(serviceName, mirrorNodeState, networkInfo);
        verify(mirrorNodeState, never()).getWritableStates(any());
    }

    @Test
    void testMigrateWithSingleSchema() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.noneOf(SchemaApplicationType.class));

        schemaRegistry.register(schema);

        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, networkInfo, config, new HashMap<>(), new AtomicLong());
        verify(mirrorNodeState, times(1)).getWritableStates(serviceName);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testMigrateWithMigrations() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.MIGRATION));
        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, networkInfo, config, new HashMap<>(), new AtomicLong());

        verify(schema).migrate(any());
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testMigrateWithStateDefinitions() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.STATE_DEFINITIONS, SchemaApplicationType.MIGRATION));

        StateDefinition stateDefinitionSingleton =
                new StateDefinition("KEY", mockCodec, mockCodec, 123, false, true, false);

        StateDefinition stateDefinitionQueue =
                new StateDefinition("KEY_QUEUE", mockCodec, mockCodec, 123, false, false, true);

        StateDefinition stateDefinition = new StateDefinition("STATE", mockCodec, mockCodec, 123, true, false, false);

        when(schema.statesToCreate(config))
                .thenReturn(Set.of(stateDefinitionSingleton, stateDefinitionQueue, stateDefinition));

        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, networkInfo, config, new HashMap<>(), new AtomicLong());
        verify(mirrorNodeState, times(1)).getWritableStates(serviceName);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
        verify(schema).migrate(any());
        verify(writableStates, times(1)).commit();
        verify(mirrorNodeState, times(1)).addService(any(), any());
    }

    @Test
    void testMigrateWithRestartApplication() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.RESTART));

        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, networkInfo, config, new HashMap<>(), new AtomicLong());

        verify(schema).restart(any());
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testNewMigrationContext() {
        MigrationContext context = schemaRegistry.newMigrationContext(
                previousVersion, readableStates, writableStates, config, networkInfo, new AtomicLong(1), EMPTY_MAP);

        assertThat(context).satisfies(c -> {
            assertThat(c.previousVersion()).isEqualTo(previousVersion);
            assertThat(c.previousStates()).isEqualTo(readableStates);
            assertThat(c.newStates()).isEqualTo(writableStates);
            assertThat(c.configuration()).isEqualTo(config);
            assertThat(c.networkInfo()).isEqualTo(networkInfo);
            assertThat(c.newEntityNum()).isEqualTo(1);
            assertThat(c.sharedValues()).isEqualTo(EMPTY_MAP);
        });
    }
}
