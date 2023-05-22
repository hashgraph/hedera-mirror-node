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

package com.hedera.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hedera.mirror.common.domain.token.NftTransfer;
import java.io.IOException;
import java.util.List;
import org.apache.logging.log4j.LogManager; // MYK
import org.apache.logging.log4j.Logger; // MYK

// each NftTransfer will be kept as a JSON element; the list of them is (for now) just a list of Strings.
public class NftTransferListToStringSerializer extends JsonSerializer<List<NftTransfer>> {
    protected final Logger log = LogManager.getLogger(getClass()); // MYK

    @Override
    public void serialize(List<NftTransfer> nftTransfer, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (nftTransfer == null) {
            gen.writeStartArray();
            gen.writeEndArray();
            log.warn("MYK: NftTransferListToStringSerializer::serialize(null)");
        } else {
            gen.writeStartArray();
            for (NftTransfer nt : nftTransfer) {
                gen.writeObject(nt);
                /* MYK
                                gen.writeStartObject();
                                gen.writeBooleanField("is_approval", nt.getIsApproval());
                                gen.writeNumberField("payer_account_id", nt.getPayerAccountId());
                                gen.writeNumberField("receiver_account_id", nt.getReceiverAccountId());
                                gen.writeNumberField("sender_account_id", nt.getSenderAccountId());
                                gen.writeEndObject();
                MYK */
            }
            gen.writeEndArray();
            log.warn("MYK: NftTransferListToStringSerializer::serialize(<List of " + nftTransfer.size()
                    + " NftTransfer(s)>)");
        }
    }
}
