@topic @fullsuite @acceptance
Feature: HCS Base Coverage Feature

  @critical @release @basicsubscribe
  Scenario Outline: Validate Topic message submission
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I successfully update an existing topic
    Then the mirror node should successfully observe the transaction
    When I provide a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    When I successfully delete the topic
    Then the mirror node should successfully observe the transaction
    Examples:
      | numMessages |
      | 10          |

  @negative
  Scenario Outline: Validate topic subscription with missing topic id
    Given I provide a topic id <topicId>
    Then the network should observe an error <errorCode>
    Examples:
      | topicId | errorCode                                                           |
      | ""      | "INVALID_ARGUMENT: subscribeTopic.filter.topicId: must not be null" |
      | "-1"    | "INVALID_ARGUMENT: Invalid entity ID: 0.0.-1"                       |
