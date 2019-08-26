const nodeCache = require('node-cache')

class Cacher {

    constructor(ttl) {
        this.cache = new nodeCache({ stdTTL: ttl });
    }

    /**
     * Get a key for caching API responses using the request URL
     * @param {Request} req HTTP request object
     * @return {String} key Key used for cachcing
     */
    getUrlFromRequest(req) {
        const url = req.protocol + '://' + req.originalUrl;
        return url;
    }

    /**
     * Get a response for the rest api by consulting cache first.
     * If a cached copy doesn't exist, then invoke the handler and update the cache
     * and then send the http response
     * @param {Request} req HTTP request object
     * @param {Response} res HTTP response object
     * @param {function} func handler function
     * @return {} None
     */
    getResponse(req, res, func) {
        logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

        // TODO: Enable intellgent caching later by detecting if the content is 
        // cacheable by checking if the response to the query will not change.
        // For now, we disable caching.
        func(req)
            .then(content => {
                res.json(content);
            })
            .catch(error => {
                logger.error("Error processing " + req.originalUrl +
                    JSON.stringify(error, Object.getOwnPropertyNames(error)));
                res.status(500)
                    .send('Internal error');
            });

        /*
                // TODO: Enable this code for caching later
        
                // If a cached copy exists, return that.
                const url = this.getUrlFromRequest(req);
                const content = this.cache.get(url);
                if (content) {
                    res.json(content);
                } else {
                    // Invoke the function to query the database, and store the results 
                    // before returning from the API
                    func(req)
                        .then(content => {
                            this.cache.set(url, content);
                            res.json(content);
                        })
                        .catch(error => {
                            logger.error("Error processing " + req.originalUrl +
                                JSON.stringify(error, Object.getOwnPropertyNames(error)));
                            res.status(500)
                                .send('Internal error');
                        })
                }
            */
    }
}

module.exports = Cacher
