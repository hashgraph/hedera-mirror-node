'use strict';

const utils = require('./utils.js');

/**
 * Handler function for /events API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getEvents = function (req, res) {
    logger.debug("--------------------  getEvents --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for timestamp,
    // nodequery and pagination (anchor and limit/offset)
    const [tsQuery, tsParams] =
        utils.parseParams(req, 'timestamp', ['tev.consensus_timestamp_ns'], 'timestamp_ns');

    const [nodeQuery, nodeParams] =
        utils.parseParams(req, 'creatornode', ['tev.creator_node_id']);
    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['tev.consensus_timestamp_ns']);

    anchorQuery = anchorQuery.replace('=', '<=');
    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req);

    let sqlParams = tsParams
        .concat(nodeParams)
        .concat(anchorParams)
        .concat(limitOffsetParams);

    let querySuffix = '';
    querySuffix += (tsQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + tsQuery;
    querySuffix += (nodeQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + nodeQuery;
    querySuffix += (anchorQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + anchorQuery;
    querySuffix += 'order by tev.consensus_timestamp_ns ' + order + '\n';
    querySuffix += limitOffsetQuery;

    let sqlQuery =
        "select  *\n" +
        " from t_events tev\n" +
        querySuffix;

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getEvents query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        let ret = {
            events: [],
            links: {
                next: null
            }
        };

        if (error) {
            logger.error("getEvents error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json(ret);
            return;
        }

        let anchorNs = results.rows.length > 0 ?
            utils.nsToSecNs(results.rows[0].consensus_timestamp_ns) : 0;

        for (let row of results.rows) {
            row.consensus_timestamp = utils.nsToSecNs(row.consensus_timestamp_ns);
            row.created_timestamp = utils.nsToSecNs(row.created_timestamp_ns);
            delete row.consensus_timestamp_ns;
            delete row.created_timestamp_ns;
            ret.events.push(row);
        }

        ret.links = {
            next: utils.getPaginationLink(req,
                (ret.events.length !== limit),
                limit, offset, order, anchorNs)
        }

        logger.debug("getEvents returning " +
            ret.events.length + " entries");

        res.json(ret);
    })
}

/**
 * Handler function for /events/:event_id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneEvent = function (req, res) {
    logger.debug("--------------------  getEvents --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    const eventQuery = 'id = ?\n';
    const sqlParams = [req.params.id];

    let sqlQuery =
        "select  *\n" +
        " from t_events\n" +
        " where " + eventQuery;

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getOneEvent query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        let ret = {
            'events': []
        };

        if (error) {
            logger.error("getOneEvent error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.status(404)
                .send('Not found');
            return;
        }

        for (let row of results.rows) {
            row.consensus_timestamp = utils.nsToSecNs(row.consensus_timestamp_ns);
            row.created_timestamp = utils.nsToSecNs(row.created_timestamp_ns);
            row.delete(consensus_timestamp_ns);
            row.delete(created_timestamp_ns)
            ret.events.push(row);
        }

        if (ret.events.length === 0) {
            res.status(404)
                .send('Not found');
            return;
        }

        logger.debug("getOneEvent returning " +
            ret.events.length + " entries");
        res.json(ret);
    })
}


module.exports = {
    getEvents: getEvents,
    getOneEvent: getOneEvent
}
