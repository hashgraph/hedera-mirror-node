package com.hedera.mirror.dataset;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirror.exception.InvalidDatasetException;
import com.hedera.mirror.util.TimestampConverter;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.ParameterizedMessage;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads a "V2" format account balance data stream header information and returns the post-header record stream
 * (CSV account balance lines).
 */
@Log4j2
public final class AccountBalancesDatasetV2 implements AccountBalancesDataset {
    public static final Pattern TIMESTAMP_HEADER_PATTERN = Pattern.compile(
            "^\\s*timestamp\\s*:\\s*(?<year>[0-9]{4})-(?<month>[0-9]{1,2})-(?<day>[0-9]{1,2})T(?<hour>[0-9]{1,2}):(?<minute>[0-9]{1,2}):(?<second>[0-9]{2})(\\.(?<subsecond>[0-9]{1,9}))?",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern COLUMN_HEADER_PATTERN = Pattern.compile("shard", Pattern.CASE_INSENSITIVE);

    private static final int MAX_HEADER_ROWS = 10;

    @Getter
    private @Nullable Instant consensusTimestamp;
    private @Nullable BufferedReader reader;
    @Getter
    private String name;
    @Getter
    private int lineNumber;

    private final TimestampConverter timestampConverter = new TimestampConverter();
    private final int bufferSize = ConfigLoader.getAccountBalancesFileBufferSize();

    /**
     * Parses the header in the input stream in preparation for streaming the account balance rows.
     * @param name for logging purposes (an identifier of the stream such as the filename or path)
     * @param in
     * @throws InvalidDatasetException if the file header does not match expectations
     */
    public AccountBalancesDatasetV2(final String name, final InputStream in) throws InvalidDatasetException {
        this.name = name;
        reader = new BufferedReader(new InputStreamReader(in), bufferSize);
        try {
            parseHeader();
        } catch (InvalidDatasetException e) {
            try {
                reader.close();
            } catch (IOException ex) {
                log.debug("Error closing reader on {}", name, ex);
            }
            reader = null;
            throw e;
        }
    }

    private void parseHeader() throws InvalidDatasetException {
        // The file should contain:
        //  - single header row Timestamp:YYYY-MM-DDTHH:MM:SS.NNNNNNNNZ
        //  - shardNum,realmNum,accountNum,balance
        // followed by rows of data.
        // The logic here is a slight bit more lenient. Look at up to MAX_HEADER_ROWS rows ending at any row containing
        // "shard" and requiring that one of the rows had "Timestamp: some value"
        try {
            for (var i = 0; i < MAX_HEADER_ROWS; ++i) {
                ++lineNumber;
                var s = reader.readLine();
                if (null == s) { // EOF
                    throw new InvalidDatasetException("Timestamp and column header not found in account balance dataset");
                }
                var m = TIMESTAMP_HEADER_PATTERN.matcher(s);
                if (m.find()) {
                    try {
                        consensusTimestamp = timestampConverter.toInstant(m);
                    } catch (IllegalArgumentException e) {
                        log.warn("{}:line({}): Invalid timestamp header line", name, lineNumber, e);
                    }
                    continue;
                }
                m = COLUMN_HEADER_PATTERN.matcher(s);
                if (m.find()) {
                    if (null == consensusTimestamp) {
                        throw new InvalidDatasetException(String.format(
                                "%s:line(%d): Found expected last header line containing %s with no prior timestamp found",
                                name, lineNumber, COLUMN_HEADER_PATTERN.pattern()));
                    }
                    return; // The column header is the last line before the datastream.
                }
            }
        } catch (IOException e) {
            throw new InvalidDatasetException("Header processing failed for account balance dataset", e);
        }
        throw new InvalidDatasetException("Timestamp and column header not found in account balance dataset");
    }

    /**
     * Return the post-header stream of lines from the account balances CSV data.
     * @return
     */
    public Stream<NumberedLine> getRecordStream() {
        if (null == reader) {
            return Stream.empty();
        }
        return reader.lines().map(line -> {
            return new NumberedLine(++lineNumber, line);
        });
    }

    @Override
    public void close() throws Exception {
        if (null != reader) {
            reader.close();
            reader = null;
        }
    }
}