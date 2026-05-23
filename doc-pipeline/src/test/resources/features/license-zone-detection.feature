Feature: License zone detection in pipeline

  Scenario: OSS zone documents are tagged Apache-2.0
    Given a project at path "/workspace/foundry/public/codex-gradle"
    When the CodexPlugin applies
    Then the extension zone is OSS
    And chunks are tagged with license "Apache-2.0"

  Scenario: CSS zone documents are tagged PROPRIETARY
    Given a project at path "/workspace/foundry/private/internal"
    When the LicenseZoneDetector detects the zone
    Then the detected zone is CSS
    And the license name is "PROPRIETARY"
