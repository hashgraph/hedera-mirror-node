/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.components.NetworkInfoImpl;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class SystemFileLoaderTest {

    private final NetworkInfo networkInfo = new NetworkInfoImpl();
    private final SystemFileLoader systemFileLoader = new SystemFileLoader(networkInfo, new MirrorNodeEvmProperties());

    @Test
    void loadNonSystemFile() {
        var file = systemFileLoader.load(fileId(1000));
        assertThat(file).isNull();
    }

    @Test
    void loadAddressBook() throws Exception {
        var fileId = fileId(101);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress())
                .hasSize(networkInfo.addressBook().size());
    }

    @Test
    void loadNodeDetails() throws Exception {
        var fileId = fileId(102);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress())
                .hasSize(networkInfo.addressBook().size());
    }

    @Test
    void loadFeeSchedule() throws Exception {
        var fileId = fileId(111);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var feeSchedule = CurrentAndNextFeeSchedule.PROTOBUF.parse(file.contents());
        assertThat(feeSchedule).isNotNull();
        assertThat(feeSchedule.currentFeeSchedule())
                .isNotNull()
                .isNotEqualTo(CurrentAndNextFeeSchedule.DEFAULT)
                .extracting(FeeSchedule::transactionFeeSchedule, InstanceOfAssertFactories.LIST)
                .hasSizeGreaterThanOrEqualTo(72);
    }

    @Test
    void loadExchangeRate() throws Exception {
        var fileId = fileId(112);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull();
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
    }

    @Test
    void loadNetworkProperties() throws Exception {
        var fileId = fileId(121);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadHapiPermissions() throws Exception {
        var fileId = fileId(122);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadThrottleDefinitions() throws Exception {
        var fileId = fileId(123);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(file.contents());
        assertThat(throttleDefinitions).isNotNull().isNotEqualTo(ServicesConfigurationList.DEFAULT);
        assertThat(throttleDefinitions.throttleBuckets()).hasSizeGreaterThanOrEqualTo(5);
    }

    private FileID fileId(long fileNum) {
        return FileID.newBuilder().fileNum(fileNum).build();
    }

    private void assertFile(File file, FileID fileId) {
        assertThat(file)
                .isNotNull()
                .returns(fileId, File::fileId)
                .returns(false, File::deleted)
                .matches(f -> f.contents() != null)
                .matches(f -> Instant.ofEpochSecond(f.expirationSecondSupplier().get())
                        .isAfter(Instant.now().plus(92, ChronoUnit.DAYS)));
    }
}
