package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

class RecordFileParserV2PerformanceTest extends AbstractRecordFileParserPerformanceTest {

    // the file hashes of the 12 v2 files. once we get rid of the filesystem dependency and insert RecordFile
    // to db only in the parser, this will no longer be needed
    private static final String[] fileHashes = {
            "412831df0042154cfc402a604d1c75e5c6142cc6b084b0c9e87228d549e489b60a5c49f82076aab686218693ceef755c",
            "ac0ae697969c7ade1486091460c0d1b52111cb0af5a03a442520abee559a740ece25970a6de8ea69ab33e40a028ad30c",
            "22493cfc48dc21cddf047bdf0d9089afddc6bb8413a611783c9c5f3378b8775f2a8428796636b829cb243249cd8c1991",
            "c0ac4776e34bd02d3fcb17b31c5c655bcf292669b957bc83759f702596bd0cbd5ee6c01d0e0c2d6375bb5e7e89212b01",
            "143e2e16decdae24a55f0cdccd708246bee2a3373d5cbbdff5017b42f467e0bd8f2e98deb8bcc3a47704ec7c071ea138",
            "7323cf312b8bd750d51c345761c0812c660e22d75c9dd0d5b74cd747774d35d52897457cfc00b9cb60acca631097c8d3",
            "de586bf693b4f2104054b80491b62ffcbc2bff63bed7059c0789b4e9b62c693deba7f59830059b7262ff78736906f958",
            "cf5cf858fad28bc9ef5ec8e8869cfaab0154f1275832437f7abff2ae9ca0cc48e981a7cd32c5b47218be46f41b8d63bb",
            "cc85949727a1e92d8fe78b07e4d9cadd92fd8af6436db2e3b78ee992e588bdb03c8f69aeceeaaca41016e2924bfcf3d3",
            "ee2329475d0cc73bbaebaf7f4bfca70be8e2d389551170786743c4b4d9862b8d8a8c70c722334f2393aefa8a24afc760",
            "89ff4e3c1ac315e01b8e80a48d5d6e965395139d27b82138c2322515cf36d7fa9d4b889d15cf7267add682b655c91398",
            "3f2f5cd13b4aef06081b1332479f42afef771c7d9ef0ad62e6458baaa8a8f756eef8542848e3031343f108e567439ca3"
    };

    public RecordFileParserV2PerformanceTest() {
        super("2020-02-09T18_30_00.000084Z.rcd", fileHashes, 2);
    }
}
