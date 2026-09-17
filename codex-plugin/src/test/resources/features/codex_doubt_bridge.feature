@doubt-bridge
Feature: Doubt bridge — acquisition OCR doubt reaches the RAG ingestion and the augmented context

  Background:
    Given a doubt bridge scenario

  Scenario: An illisible chunk is ingested flagged doubtful by the socle policy
    Given a chunk with content "Chapter body" and marker "[ILLISIBLE]"
    When the chunks are ingested through the doubt bridge
    Then the ingested doubt marks the chunk doubtful
    And the ingested confidence is zero

  Scenario: A clean chunk keeps full confidence at ingestion
    Given a clean chunk with content "A clean readable chapter body"
    When the chunks are ingested through the doubt bridge
    Then the ingested doubt does not mark the chunk doubtful
    And the ingested confidence is full

  Scenario: The Docs channel annotates a doubtful chunk by default
    Given a clean retrieved chunk "clean section"
    And a doubtful retrieved chunk "shaky OCR section"
    When the Docs channel is composed without exclusion
    Then the Docs content contains "shaky OCR section"
    And the Docs content contains the doubt marker

  Scenario: The Docs channel excludes a doubtful chunk when opted in
    Given a clean retrieved chunk "clean section"
    And a doubtful retrieved chunk "shaky OCR section"
    When the Docs channel is composed with exclusion
    Then the Docs content contains "clean section"
    And the Docs content does not contain "shaky OCR section"

  Scenario: The composite context retrieval is doubt-aware
    Given a clean retrieved chunk "clean section"
    When the composite context retrieval runs
    Then the socle doubt-aware search was used
