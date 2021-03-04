@ScheduleBase @FullSuite
Feature: Schedule Base Coverage Feature

    @Acceptance @Sanity
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
        And the network confirms the executed schedule is removed from state
        Examples:
            | accountName | httpStatusCode |
            | "CAROL"     | 200            |

    @Acceptance
    Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoAccountCreate and ScheduleDelete
        Given I successfully schedule a crypto account create
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When I successfully delete the schedule
        And the network confirms the executed schedule is removed from state
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        Examples:
            | httpStatusCode |
            | 200            |

    @Acceptance
    Scenario Outline: Validate Base Schedule Flow - MultiSig ScheduleCreate of CryptoAccountCreate and ScheduleDelete
        Given I schedule a crypto transfer with <initialSignatureCount> initial signatures but require an additional signature from <accountName>
        When the network confirms schedule presence
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <accountName>
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        And the network confirms the executed schedule is removed from state
        Examples:
            | initialSignatureCount | accountName | httpStatusCode |
            | 3                     | "ALICE"     | 200            |
            | 10                    | "DAVE"      | 200            |

    @Acceptance
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
        When the network confirms the executed schedule is removed from state
        Examples:
            | sender  | receiver | httpStatusCode |
            | "ALICE" | "DAVE"   | 200            |

#    @Acceptance - sdk bug exists where executed HCS submit message fails
    Scenario Outline: Validate scheduled HCS message - ScheduleCreate of TopicMessageSubmit and ScheduleSign
        Given I successfully schedule a topic message submit with <accountName>'s submit key
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the non executed schedule entity
        When the scheduled transaction is signed by <accountName>
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the mirror node REST API should verify the executed schedule entity
        And the network confirms the executed schedule is removed from state
        Examples:
            | accountName | httpStatusCode |
            | "ALICE"     | 200            |
