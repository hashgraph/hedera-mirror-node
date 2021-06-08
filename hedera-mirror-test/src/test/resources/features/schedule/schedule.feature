@schedulebase @fullsuite
Feature: Schedule Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
        Given I successfully schedule a treasury HBAR disbursement to <accountName>
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <accountName>
        And the network confirms some signers have provided their signatures
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by treasuryAccount
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        And the network confirms the schedule is executed
        Examples:
            | accountName | httpStatusCode |
            | "CAROL"     | 200            |

    Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoAccountCreate and ScheduleDelete
        Given I successfully schedule a crypto account create
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When I successfully delete the schedule
        And the network confirms the schedule is deleted
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the deleted schedule entity
        Examples:
            | httpStatusCode |
            | 200            |

    @acceptance
    Scenario Outline: Validate Base Schedule Flow - MultiSig ScheduleCreate of CryptoAccountCreate and ScheduleSign
        Given I schedule a crypto transfer with <initialSignatureCount> initial signatures but require an additional signature from <accountName>
        When the network confirms schedule presence
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <accountName>
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        And the network confirms the schedule is executed
        Examples:
            | initialSignatureCount | accountName | httpStatusCode |
            | 10                    | "ALICE"     | 200            |

    @release @acceptance
    Scenario Outline: Validate scheduled Hbar and Token transfer - ScheduleCreate of TokenTransfer and multi ScheduleSign
        Given I successfully schedule a token transfer from <sender> to <receiver>
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <sender>
        And the network confirms some signers have provided their signatures
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <receiver>
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        When the network confirms the schedule is executed
        Examples:
            | sender  | receiver | httpStatusCode |
            | "ALICE" | "DAVE"   | 200            |

    @acceptance
    Scenario Outline: Validate scheduled HCS message - ScheduleCreate of TopicMessageSubmit and ScheduleSign
        Given I successfully schedule a topic message submit with <accountName>'s submit key
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <accountName>
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        And the network confirms the schedule is executed
        Examples:
            | accountName | httpStatusCode |
            | "ALICE"     | 200            |
