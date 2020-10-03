package com.hedera.mirror.importer.reader.balance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Stream;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.input.BoundedInputStream;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
@RequiredArgsConstructor
public class CompositeBalanceFileReader implements BalanceFileReader {

    static final int BUFFER_SIZE = 16;
    private final BalanceFileReaderImplV1 version1Reader;
    private final BalanceFileReaderImplV2 version2Reader;

    @Override
    public Stream<AccountBalance> read(File file) {
        return getReader(file).read(file);
    }

    private BalanceFileReader getReader(File file) {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(new BoundedInputStream(new FileInputStream(file),
                             BUFFER_SIZE)), BUFFER_SIZE)) {
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
