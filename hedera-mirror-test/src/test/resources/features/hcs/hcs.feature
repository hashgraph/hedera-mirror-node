@TopicMessagesBase @FullSuite
Feature: HCS Base Coverage Feature

    @Sanity @BasicSubscribe
    Scenario Outline: Validate Topic message submission
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 100         |
#            | 7           |

    @Sanity @OpenSubscribe
    Scenario Outline: Validate Topic message submission
        Given I successfully create a new open topic
        And I publish and verify <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 1           |

    @SubscribeOnly
    Scenario Outline: Validate topic message subscription
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
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | numBatches | numMessages | milliSleep |
            | "30950" | 10         | 1000        | 2000       |

    @PublishAndVerify
    Scenario Outline: Validate topic message subscription
        Given I provide a topic id <topicId>
        And I publish and verify <numMessages> messages
        And I subscribe with a filter to retrieve these published messages
        Then the network should successfully observe these messages
        Examples:
            | topicId  | numMessages |
            | "171231" | 340         |

    @UpdateTopic
    Scenario: Validate Topic Updates
        Given I successfully create a new topic id
        Then I successfully update an existing topic

    @DeleteTopic
    Scenario: Validate topic deletion
        Given I successfully create a new topic id
        Then I successfully delete the topic

    Scenario Outline: Validate Topic message listener
        Given I successfully create a new topic id
        And I publish and verify <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive within <latency> seconds
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    @Negative
    Scenario Outline: Validate topic subscription with missing topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | ""      | "Missing required topicID"                                                             |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |
