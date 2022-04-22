package com.hedera.mirror.common.domain.transaction;

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

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigInteger;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class EthereumTransaction implements Persistable<Long> {

    @ToString.Exclude
    private byte[] accessList;

    @ToString.Exclude
    private byte[] callData;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId callDataId;

    @ToString.Exclude
    private byte[] chainId;

    @Id
    private Long consensusTimestamp;

    @ToString.Exclude
    private byte[] data;

    @ToString.Exclude
    private byte[] fromAddress;

    private Long gasLimit;

    private byte[] gasPrice;

    @ToString.Exclude
    private byte[] hash;

    private byte[] maxFeePerGas;

    private Long maxGasAllowance;

    private byte[] maxPriorityFeePerGas;

    private Long nonce;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId payerAccountId;

    private Integer recoveryId;

    @Column(name = "signature_r")
    @ToString.Exclude
    private byte[] signatureR;

    @Column(name = "signature_s")
    @ToString.Exclude
    private byte[] signatureS;

    @Column(name = "signature_v")
    @ToString.Exclude
    private byte[] signatureV;

    @ToString.Exclude
    private byte[] toAddress;

    private Integer type;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    /**
     * RLP Encode ethereum transaction. Logic differs per Eip155, between Eip155 and Eip1559 and after Eip1559
     *
     * @return encoded message
     */
    @JsonIgnore
    @Transient
    public byte[] getRLPEncodedMessage() {
        switch (getType()) {
            case 0:
                return (getChainId() != null && getChainId().length > 0)
                        ? RLPEncoder.encodeAsList(
                        Integers.toBytes(getNonce()),
                        getGasPrice(),
                        Integers.toBytes(getGasLimit()),
                        getToAddress(),
                        Integers.toBytesUnsigned(new BigInteger(getValue())),
                        getCallData(),
                        getChainId(),
                        Integers.toBytes(0),
                        Integers.toBytes(0))
                        : RLPEncoder.encodeAsList(
                        Integers.toBytes(getNonce()),
                        getGasPrice(),
                        Integers.toBytes(getGasLimit()),
                        getToAddress(),
                        Integers.toBytesUnsigned(new BigInteger(getValue())),
                        getCallData());
            case 2:
                return RLPEncoder.encodeSequentially(
                        Integers.toBytes(2),
                        new Object[] {
                                getChainId(),
                                Integers.toBytes(getNonce()),
                                getMaxPriorityFeePerGas(),
                                getMaxFeePerGas(),
                                Integers.toBytes(getGasLimit()),
                                getToAddress(),
                                Integers.toBytesUnsigned(new BigInteger(getValue())),
                                getCallData(),
                                new Object[0]
                        });
            case 1:
                throw new IllegalArgumentException("Unsupported transaction type " + getType());
        }
        return new byte[0];
    }
}
