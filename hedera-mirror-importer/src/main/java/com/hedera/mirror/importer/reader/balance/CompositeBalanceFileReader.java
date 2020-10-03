package com.hedera.mirror.importer.reader.balance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Stream;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
@RequiredArgsConstructor
public class CompositeBalanceFileReader implements BalanceFileReader {

    private static final int FIRST_LINE_BUFFER_SIZE = 20;
    private final BalanceFileReaderImplV1 version1Reader;
    private final BalanceFileReaderImplV2 version2Reader;

    @Override
    public Stream<AccountBalance> read(File file) {
        return getReader(file).read(file);
    }

    private BalanceFileReader getReader(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)),
                FIRST_LINE_BUFFER_SIZE)) {
            String line = reader.readLine();
            if (version2Reader.isFirstLineFromFileVersion(line)) {
                return version2Reader;
            } else {
                return version1Reader;
            }
        } catch (IOException | NullPointerException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }
}
