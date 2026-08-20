@enrichment
Feature: JSON LDD enrichment with RAG chunks and Graphify nodes

  Background:
    Given a JSON LDD file with sections "Architecture" and "Testing"
    And a chunks file with one chunk in section "Architecture" and one chunk in section "Testing"
    And a graph json file with nodes labelled "Architecture" and "Testing"

  Scenario: Enrichment attaches RAG chunks, Graphify nodes, density and entities
    When the enrich json ldd task runs
    Then the enriched json contains 2 sections
    And the section "Architecture" has 1 rag chunk attached
    And the section "Architecture" has the graphify node "node-arch" resolved
    And the section "Architecture" has a semantic density of 0.5
    And the section "Architecture" has extracted entities

  Scenario: Enrichment degrades silently when the graph json file is missing
    Given a missing graph json file
    When the enrich json ldd task runs
    Then the enriched json contains 2 sections
    And every section has no graphify nodes resolved

  Scenario: Enrichment degrades silently when the graph json is invalid
    Given an invalid graph json file
    When the enrich json ldd task runs
    Then the enriched json contains 2 sections
    And every section has no graphify nodes resolved

  Scenario: Enrichment is idempotent - same inputs yield same output
    When the enrich json ldd task runs twice
    Then the two enriched outputs are identical