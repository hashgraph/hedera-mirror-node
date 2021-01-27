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

class RecordFileParserV5PerformanceTest extends AbstractRecordFileParserPerformanceTest {

    // the end running hashes of the 12 v5 files. once we get rid of the filesystem dependency and insert RecordFile
    // to db only in the parser, this will no longer be needed
    private static final String[] endRunningHashes = {
            "b3ce925d89f86cc09bf455385df67201dec6b914e839f3f67a2cc81705589505239d2e6f5540a80c8c67c0798212c634",
            "b5c621dbc2e970687e61bad520dcef273f842f538a00f65811d65ae6a75c232888cdd7763e725dc337c1886af338eb3a",
            "e40cfe4dc161b0807552c57c09e6180044439316f0a0d395efb239d5c81b5327a1becb6c17eff7a11e0ee747139226a7",
            "060f8280d8d252774ceacab94629eb053a5696ce96f0100e3db1556c755dd9b6d823c3a00e7b92d00ccea37652bfb92e",
            "d67f51878813f6e5283c543ac6b2f11eac2f908c988b89c5b46cf1b19e0c559926fdf0cf6bb9847ff30618cfa19cfe9b",
            "4c24fb0aa485b74ebb14d48bb724eee0199aa4d9059d37b397a5afb0a2dd4010a3135acd8d5e46a24dcff8b5090d59b0",
            "39d092e50105634b5963023fede9d461776b688be441c59da23eab5624016268e5980093febe5ac843927ef639a09a85",
            "5f1ede58c280aa1d1f19d0f7671d0d031e79832f9e5763da287aaf360db9bc626ee29c0bc336a215e3d3975db79ce487",
            "cacfedf1e8ce7b4358505ec574cf30890704c3ea132bebcb2da8bfbfd8f6807f8dcc6606dba530abdfee62f91ef77fe4",
            "f1171c51dcdcfdd69cf1861642237b3ed3f3d0d613bf7374f96a1ca6644b975635f38f89f1d6d74b143776c7dececa17",
            "56b0b695361b4596d5700bd9d6150f1eabea8aee54e8c1f0c58bd1c18031455bcf5688269e5811a35290e6be98ccab6f",
            "cafa4b10ec20aecfe30f9c62d4f1d71f2de00a418d8bf0176d67911d086dbe9e3ef572d14bdbe243c4325d13315aad9f"
    };

    public RecordFileParserV5PerformanceTest() {
        super("2021-01-14T20_02_56.025429000Z.rcd", endRunningHashes, 5);
    }
}
