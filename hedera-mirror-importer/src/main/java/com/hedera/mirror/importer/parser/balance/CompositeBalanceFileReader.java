package com.hedera.mirror.importer.parser.balance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.balance.v1.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.parser.balance.v2.BalanceFileReaderImplV2;

@Named
public class CompositeBalanceFileReader implements BalanceFileReader {
    private static final String VERSION_010_HEADER_PREFIX = "# 0.1.0";

    private final int fileBufferSize;

    private final BalanceFileReaderImplV1 version1Reader;
    private final BalanceFileReaderImplV2 version2Reader;

    public CompositeBalanceFileReader(BalanceParserProperties balanceParserProperties,
                                      BalanceFileReaderImplV1 balanceFileReaderImplV1,
                                      BalanceFileReaderImplV2 balanceFileReaderImplV2) {
        this.fileBufferSize = balanceParserProperties.getFileBufferSize();
        this.version1Reader = balanceFileReaderImplV1;
        this.version2Reader = balanceFileReaderImplV2;
    }

    @Override
    public Stream<AccountBalance> read(File file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)),
                    fileBufferSize);
            String line = Optional.of(reader.readLine()).get().trim();
            String lineLowered = line.toLowerCase();
            reader.close();
            if (lineLowered.startsWith(VERSION_010_HEADER_PREFIX)) {
                return version2Reader.read(file);
            } else {
                return version1Reader.read(file);
            }
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }
}
