export default {
  defaultMochaStatements: (jest, integrationDbOps, integrationDomainOps) => {
    jest.setTimeout(40000);
    let dbConfig;
    const defaultBeforeAllTimeoutMillis = 240 * 1000;

    beforeAll(async () => {
      dbConfig = await integrationDbOps.instantiateDatabase();
      await integrationDomainOps.setUp({}, dbConfig.sqlConnection);
      global.pool = dbConfig.sqlConnection;
    }, defaultBeforeAllTimeoutMillis);

    afterAll(async () => {
      await integrationDbOps.closeConnection(dbConfig);
    });

    beforeEach(async () => {
      if (!dbConfig.sqlConnection) {
        logger.warn(`sqlConnection undefined, acquire new connection`);
        dbConfig.sqlConnection = integrationDbOps.getConnection(dbConfig.dbSessionConfig);
      }

      await integrationDbOps.cleanUp(dbConfig.sqlConnection);
    });
  },
};
