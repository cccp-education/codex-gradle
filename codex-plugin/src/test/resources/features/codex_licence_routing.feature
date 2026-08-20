Feature: CodexPipelineTask licence routing
  As a codex pipeline operator
  I want the pipeline output to be routed to OSS/ or office/ based on the source PDF licence
  So that open-source documents land in the public tree and copyrighted ones in the private tree

  @licence @routing
  Scenario: Routing enabled with Apache-licensed PDF routes output to OSS directory
    Given a pipeline source PDF containing the text "Released under the Apache License 2.0."
    And a pipeline base directory "routed" under a temporary folder
    And the codex pipeline is configured with licence routing enabled and fallback zone "UNKNOWN"
    When the transform corpus to pdf pipeline runs
    Then the pipeline output is written under zone "OSS"
    And the pipeline output file has a non-blank content

  @licence @routing
  Scenario: Routing enabled with copyright PDF routes output to office directory
    Given a pipeline source PDF containing the text "All rights reserved. Copyright 2024 Acme Corp."
    And a pipeline base directory "routed" under a temporary folder
    And the codex pipeline is configured with licence routing enabled and fallback zone "UNKNOWN"
    When the transform corpus to pdf pipeline runs
    Then the pipeline output is written under zone "office"
    And the pipeline output file has a non-blank content

  @licence @routing
  Scenario: Routing disabled writes output to the configured output file
    Given a pipeline source PDF containing the text "Released under the Apache License 2.0."
    And the codex pipeline is configured with licence routing disabled
    When the transform corpus to pdf pipeline runs
    Then the pipeline output is written to the configured output file
    And the pipeline output file has a non-blank content