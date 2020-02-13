@TopicMessagesBase @FullSuite
Feature: HCS Base Coverage Feature

    @Sanity @BasicSubscribe @Acceptance
    Scenario Outline: Validate Topic message submission
        Given I successfully create a new topic id
        And I publish and verify <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 100         |

    @OpenSubscribe @Acceptance
    Scenario Outline: Validate Topic message submission to an open submit topic
        Given I successfully create a new open topic
        And I publish and verify <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 1           |

    @SubscribeOnly @Acceptance
    Scenario Outline: Validate topic message subscription only
        Given I provide a topic id <topicId>
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId  | numMessages |
            | "169223" | 680         |

    @PublishOnly
    Scenario Outline: Validate topic message subscription
        Given I provide a topic id <topicId>
        And I publish <numBatches> batches of <numMessages> messages every <milliSleep> milliseconds
        And I subscribe with a filter to retrieve these published messages
        Then the network should successfully observe these messages
        Examples:
            | topicId  | numBatches | numMessages | milliSleep |
            | "171231" | 2          | 3           | 2000       |

    @PublishAndVerify
    Scenario Outline: Validate topic message subscription
        Given I provide a topic id <topicId>
        And I publish and verify <numMessages> messages
        And I subscribe with a filter to retrieve these published messages
        Then the network should successfully observe these messages
        Examples:
            | topicId  | numMessages |
            | "171231" | 340         |

    @UpdateTopic @Acceptance
    Scenario: Validate Topic Updates
        Given I successfully create a new topic id
        Then I successfully update an existing topic

    @DeleteTopic @Acceptance
    Scenario: Validate topic deletion
        Given I successfully create a new topic id
        Then I successfully delete the topic

    @Acceptance
    Scenario Outline: Validate Topic message listener latency
        Given I successfully create a new topic id
        And I publish and verify <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive within <latency> seconds
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    @Negative @Acceptance
    Scenario Outline: Validate topic subscription with missing topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | ""      | "Missing required topicID"                                                             |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |
