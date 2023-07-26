@topicmessagesbase @fullsuite
Feature: HCS Base Coverage Feature

  @critical @release @acceptance @basicsubscribe
  Scenario Outline: Validate Topic message submission
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    Examples:
      | numMessages |
      | 10          |

  @release @acceptance @updatetopic
  Scenario: Validate Topic Updates
    Given I successfully create a new topic id
    Then the mirror node should successfully observe the transaction
    When I successfully update an existing topic
    Then the mirror node should successfully observe the transaction

  @acceptance @deletetopic
  Scenario: Validate topic deletion
    Given I successfully create a new topic id
    Then the mirror node should successfully observe the transaction
    When I successfully delete the topic
    Then the mirror node should successfully observe the transaction

  @acceptance @latency
  Scenario Outline: Validate Topic message listener latency
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a number of messages <numMessages> I want to receive within <latency> seconds
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    Examples:
      | numMessages | latency |
      | 2           | 30      |
      | 5           | 30      |

  @acceptance @negative
  Scenario Outline: Validate topic subscription with missing topic id
    Given I provide a topic id <topicId>
    Then the network should observe an error <errorCode>
    Examples:
      | topicId | errorCode                                                           |
      | ""      | "INVALID_ARGUMENT: subscribeTopic.filter.topicId: must not be null" |
      | "-1"    | "INVALID_ARGUMENT: Invalid entity ID: 0.0.-1"                       |
