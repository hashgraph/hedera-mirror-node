@TopicMessagesFilter @FullSuite
Feature: HCS Message Filter Coverage Feature

    Background: User has clients
        Given Config context is loaded
        And User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Then all setup items were configured

    @Sanity
    Scenario Outline: Validate topic filtering with past date and get X previous
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a startDate <startDate> and a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "-60"                     | 5           |

    Scenario Outline: Validate resubscribe topic filtering
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a startDate <startDate> and a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        And I unsubscribe from a topic
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "2000-01-01T00:00:00.00Z" | 5           |

    @Edge
    Scenario Outline: Validate topic filtering with start and end time in between min and max messages (e.g. if 100 messages get 25-30)
        Given I successfully create a new topic id
        And I publish <publishCount> messages
        When I provide a startSequence <startSequence> and endSequence <endSequence> and a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe <numMessages> messages
        Examples:
            | publishCount | startSequence | endSequence | numMessages |
            | 50           | 25            | 30          | 5           |

    Scenario Outline: Validate topic filtering with past date and limit of 10
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a startDate <startDate> and endDate <endDate> and a limit of <limit> messages I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | startDate                 | endDate                   | limit |
            | 10          | "2020-01-01T00:00:00.00Z" | "2020-02-01T00:00:00.00Z" | 5     |
