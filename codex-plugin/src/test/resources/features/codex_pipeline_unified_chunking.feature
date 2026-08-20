Feature: CodexPipelineTask unified chunking (CDX-UNIFY-2)
  As a codex pipeline operator
  I want the pipeline to use the canonical SemanticChunker for chunking
  So that the pipeline no longer duplicates chunking logic and stays DRY

  Background:
    Given a unified pipeline source PDF containing the text "Released under the Apache License 2.0. Some heading and body content for chunking."

  @pipeline @unify
  Scenario: Pipeline with routing disabled writes AsciiDoc output to configured file
    And the unified pipeline is configured with licence routing disabled
    When the unified transform corpus to pdf pipeline runs
    Then the unified pipeline output is written to the configured output file
    And the unified pipeline output file has non-blank AsciiDoc content

  @pipeline @unify
  Scenario: Pipeline with routing enabled and Apache PDF routes to OSS
    And a unified pipeline base directory "routed" under a temporary folder
    And the unified pipeline is configured with licence routing enabled and fallback zone "UNKNOWN"
    When the unified transform corpus to pdf pipeline runs
    Then the unified pipeline output is written under zone "OSS"
    And the unified pipeline output file has non-blank AsciiDoc content

  @pipeline @unify
  Scenario: Pipeline output is deterministic across two runs on the same input
    And the unified pipeline is configured with licence routing disabled
    When the unified transform corpus to pdf pipeline runs twice on the same input
    Then the two unified pipeline outputs are identical