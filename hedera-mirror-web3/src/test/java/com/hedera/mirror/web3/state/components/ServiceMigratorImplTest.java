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

import static com.hedera.mirror.web3.state.components.ServiceMigratorImpl.NAME_OF_ENTITY_ID_SERVICE;
import static com.hedera.mirror.web3.state.components.ServiceMigratorImpl.NAME_OF_ENTITY_ID_SINGLETON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistry.Registration;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.info.NetworkInfo;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceMigratorImplTest {

    @Mock
    private MapWritableStates mapWritableStates;

    @Mock
    private Metrics metrics;

    @Mock
    private MirrorNodeState mirrorNodeState;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private SchemaRegistryImpl schemaRegistry;

    @Mock
    private SchemaRegistry mockSchemaRegistry;

    @Mock
    private ServicesRegistryImpl servicesRegistry;

    @Mock
    private ServicesRegistry mockServicesRegistry;

    @Mock
    private State mockState;

    @Mock
    private WritableSingletonState writableSingletonState;

    private VersionedConfiguration bootstrapConfig;

    private ServiceMigratorImpl serviceMigrator;

    @BeforeEach
    void initialize() {
        serviceMigrator = new ServiceMigratorImpl();
        bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
    }

    @Test
    void testCreationVersionOfWithMirrorNodeState() {
        assertDoesNotThrow(() -> serviceMigrator.creationVersionOf(mirrorNodeState));
    }

    @Test
    void testCreationVersionOfWithInvalidState() {
        assertThrows(IllegalArgumentException.class, () -> serviceMigrator.creationVersionOf(mockState));
    }

    @Test
    void doMigrations() {
        final var mockServiceRegistration = mock(Registration.class);
        final var mockService = mock(Service.class);
        when(mockServiceRegistration.service()).thenReturn(mockService);
        when(mockService.getServiceName()).thenReturn(NAME_OF_ENTITY_ID_SERVICE);
        when(servicesRegistry.registrations()).thenReturn(Set.of(mockServiceRegistration));
        when(mockServiceRegistration.registry()).thenReturn(schemaRegistry);
        when(mirrorNodeState.getWritableStates(NAME_OF_ENTITY_ID_SERVICE)).thenReturn(mapWritableStates);
        when(mapWritableStates.getSingleton(NAME_OF_ENTITY_ID_SINGLETON)).thenReturn(writableSingletonState);

        serviceMigrator.doMigrations(
                mirrorNodeState,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                metrics);

        verify(mapWritableStates, times(1)).commit();
    }

    @Test
    void doMigrationsWithMultipleRegistrations() {
        Service service1 = mock(Service.class);
        when(service1.getServiceName()).thenReturn(NAME_OF_ENTITY_ID_SERVICE);

        Service service2 = mock(Service.class);
        when(service2.getServiceName()).thenReturn("testService2");

        SchemaRegistry registry1 = mock(SchemaRegistryImpl.class);
        SchemaRegistry registry2 = mock(SchemaRegistryImpl.class);

        Registration registration1 = new Registration(service1, registry1);
        Registration registration2 = new Registration(service2, registry2);
        when(servicesRegistry.registrations()).thenReturn(Set.of(registration1, registration2));
        when(mirrorNodeState.getWritableStates(NAME_OF_ENTITY_ID_SERVICE)).thenReturn(mapWritableStates);
        when(mapWritableStates.getSingleton(NAME_OF_ENTITY_ID_SINGLETON)).thenReturn(writableSingletonState);

        serviceMigrator.doMigrations(
                mirrorNodeState,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                metrics);

        verify(mapWritableStates, times(1)).commit();
    }

    @Test
    void doMigrationsWithMultipleRegistrationsWithInvalidSchemaRegistry() {
        Service service1 = mock(Service.class);
        when(service1.getServiceName()).thenReturn(NAME_OF_ENTITY_ID_SERVICE);

        Service service2 = mock(Service.class);

        SchemaRegistry registry1 = mock(SchemaRegistryImpl.class);

        // Invalid schema registry type
        SchemaRegistry registry2 = mock(SchemaRegistry.class);

        Registration registration1 = new Registration(service1, registry1);
        Registration registration2 = new Registration(service2, registry2);
        when(servicesRegistry.registrations()).thenReturn(Set.of(registration1, registration2));
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> serviceMigrator.doMigrations(
                        mirrorNodeState,
                        servicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));

        assertThat(exception.getMessage()).isEqualTo("Can only be used with SchemaRegistryImpl instances");
    }

    @Test
    void doMigrationsInvalidRegistrations() {
        assertThrows(
                NoSuchElementException.class,
                () -> serviceMigrator.doMigrations(
                        mirrorNodeState,
                        servicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));
    }

    @Test
    void doMigrationsInvalidState() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> serviceMigrator.doMigrations(
                        mockState,
                        servicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));

        assertThat(exception.getMessage()).isEqualTo("Can only be used with MirrorNodeState instances");
    }

    @Test
    void doMigrationsInvalidServicesRegistry() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> serviceMigrator.doMigrations(
                        mirrorNodeState,
                        mockServicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));

        assertThat(exception.getMessage()).isEqualTo("Can only be used with ServicesRegistryImpl instances");
    }

    @Test
    void doMigrationsInvalidSchemaRegistry() {
        final var mockServiceRegistration = mock(Registration.class);
        final var mockService = mock(Service.class);
        when(mockServiceRegistration.service()).thenReturn(mockService);
        when(mockService.getServiceName()).thenReturn(NAME_OF_ENTITY_ID_SERVICE);
        when(servicesRegistry.registrations()).thenReturn(Set.of(mockServiceRegistration));
        when(mockServiceRegistration.registry()).thenReturn(mockSchemaRegistry);
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> serviceMigrator.doMigrations(
                        mirrorNodeState,
                        servicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));

        assertThat(exception.getMessage()).isEqualTo("Can only be used with SchemaRegistryImpl instances");
    }

    @Test
    void doMigrationsInvalidWritableStates() {
        final var mockServiceRegistration = mock(Registration.class);
        final var mockService = mock(Service.class);
        when(mockServiceRegistration.service()).thenReturn(mockService);
        when(mockService.getServiceName()).thenReturn(NAME_OF_ENTITY_ID_SERVICE);
        when(servicesRegistry.registrations()).thenReturn(Set.of(mockServiceRegistration));
        when(mockServiceRegistration.registry()).thenReturn(schemaRegistry);
        final var mockMapWritableStates = mock(EmptyWritableStates.class);
        when(mirrorNodeState.getWritableStates(NAME_OF_ENTITY_ID_SERVICE)).thenReturn(mockMapWritableStates);

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> serviceMigrator.doMigrations(
                        mirrorNodeState,
                        servicesRegistry,
                        null,
                        new ServicesSoftwareVersion(bootstrapConfig
                                .getConfigData(VersionConfig.class)
                                .servicesVersion()),
                        new ConfigProviderImpl().getConfiguration(),
                        networkInfo,
                        metrics));

        assertThat(exception.getMessage()).isEqualTo("Can only be used with MapWritableStates instances");
    }
}
