package com.hedera.mirror.dataset;

import com.hedera.mirror.exception.InvalidDatasetException;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class AccountBalancesFileLoaderIT {
    @Test
    public void positiveSmallFile() throws FileNotFoundException, InvalidDatasetException, URISyntaxException, SQLException {
        // The test has a 2 line header and 2 data lines.
        final var fileName = "2019-08-20T21_30_00.147998006Z_Balances.csv";
        final var path = getClass().getResource(Paths.get("/account_balances", fileName).toString());
        final var cut = new AccountBalancesFileLoader(Paths.get(path.toURI()));
        cut.loadAccountBalances();
        assertAll(
                () -> assertEquals(2, cut.getValidRowCount())
                ,() -> assertFalse(cut.isInsertErrors())
        );
        // TODO assert the rows actually added to the database.
    }
}