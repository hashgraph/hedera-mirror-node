package com.hedera.mirror;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.mirror.domain.HederaNetwork;
import com.hedera.utilities.Utility;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.inject.Named;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Named
@Validated
@ConfigurationProperties("hedera.mirror")
public class MirrorProperties {

    private static final String ADDRESS_BOOK_FILE = "addressbook.bin";

    private Path addressBookPath;

    @NotNull
    private Path dataPath = Paths.get(".", "data");

    @NotNull
    private HederaNetwork network = HederaNetwork.MAINNET;

    @Min(0)
    private long shard = 0L;

    public MirrorProperties() {
        Utility.ensureDirectory(dataPath);
    }

    public Path getAddressBookPath() {
        return addressBookPath != null ? addressBookPath : dataPath.resolve(ADDRESS_BOOK_FILE);
    }

    public void setDataPath(Path dataPath) {
        Utility.ensureDirectory(dataPath);
        this.dataPath = dataPath;
    }
}
