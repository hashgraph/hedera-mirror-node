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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.mirror.rest.model.ContractResponse;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import java.util.List;

public class BaseContractFeature extends AbstractFeature {
    protected DeployedContract deployedParentContract;

    protected ContractResponse verifyContractFromMirror(boolean isDeleted) {
        var mirrorContract =
                mirrorClient.getContractInfo(deployedParentContract.contractId().toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.getDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId())
                .isEqualTo(deployedParentContract.fileId().toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getEvmAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        if (contractClient
                .getSdkClient()
                .getAcceptanceTestProperties()
                .getFeatureProperties()
                .isSidecars()) {
            assertThat(mirrorContract.getRuntimeBytecode()).isNotNull();
        }

        assertThat(mirrorContract.getBytecode())
                .isEqualTo(deployedParentContract.compiledSolidityArtifact().getBytecode());

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient
                            .getSdkClient()
                            .getExpandedOperatorAccountId()
                            .getAccountId()
                            .toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    protected void verifyContractExecutionResultsById() {
        List<ContractResult> contractResults = mirrorClient
                .getContractResultsById(deployedParentContract.contractId().toString())
                .getResults();

        assertThat(contractResults).isNotEmpty().allSatisfy(this::verifyContractExecutionResults);
    }

    protected void verifyContractExecutionResultsByTransactionId() {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(
                networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    protected void verifyContractExecutionResults(ContractResult contractResult) {
        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());

        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();

        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo())
                .isEqualTo(FeatureInputHandler.evmAddress(deployedParentContract.contractId()));
    }
}
