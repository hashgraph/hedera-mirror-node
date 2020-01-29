@TopicMessages
Feature: HCS Coverage Feature

    Background: User has clients
        Given User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Then all setup items were configured

    #Verified
    Scenario Outline: Validate Topic message submission
        Given I attempt to create a new topic id
        And I provide a number of messages <numMessages> I want to receive
        When I publish random messages
        Then the network should confirm valid transaction receipts for this operation
        Examples:
            | numMessages |
            | 1           |
            | 7           |

    #Verified
    Scenario: Validate Topic Updates
        Given I attempt to create a new topic id
        When I attempt to update an existing topic
        Then the network should confirm valid transaction receipts for this operation

    #Verified
    Scenario Outline: Validate Topic message listener
        Given I attempt to create a new topic id
        And I provide a number of messages <numMessages> I want to receive within <latency> seconds
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    #Verified
    Scenario Outline: Validate topic filtering with past date and get X previous
        Given I attempt to create a new topic id
        And I provide a date <startDate> and a number of messages <numMessages> I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "2000-01-01T00:00:00.00Z" | 5           |

    #Verified
    Scenario Outline: Validate resubscribe topic filtering
        Given I attempt to create a new topic id
        And I provide a date <startDate> and a number of messages <numMessages> I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        And I unsubscribe from a topic
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "2000-01-01T00:00:00.00Z" | 5           |

    #Verified
    Scenario Outline: Validate topic filtering with start and end time in between min and max messages (e.g. if 100 messages get 25-30)
        Given I attempt to create a new topic id
        And I provide a startDate <startDate> and endDate <endDate> and a number of messages <numMessages> I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | endDate                   | numMessages |
            | "2020-01-01T00:00:00.00Z" | "2020-02-01T00:00:00.00Z" | 5           |

    #Verified
    Scenario Outline: Validate topic filtering with past date and limit of 10
        Given I attempt to create a new topic id
        And I provide a startDate <startDate> and endDate <endDate> and a limit of <limit> messages I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | endDate                   | limit |
            | "2020-01-01T00:00:00.00Z" | "2020-02-01T00:00:00.00Z" | 5     |


    Scenario Outline: Validate topic subscription with missing topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                  |
            | ""      | "Missing required topicID" |


    Scenario Outline: Validate topic subscription with invalid topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |

#    # Discussions still out on this
#    Scenario Outline: Validate topic subscription with no matching topic id
#        Given I provide a topic id <topicId>
#        Then the network should observe an error <errorCode>
#        Examples:
#            | topicId   | errorCode |
#            | "0"         | "bgf"     |
#            | "123456789" | "bgfbg"   |

    #Verified
    Scenario: Validate topic deletion
        Given I attempt to create a new topic id
        When I attempt to delete the topic
        Then the network should confirm valid transaction receipts for this operation

    # Must be last scenario in file
    @TopicClientClose
    Scenario: Client close place holder

