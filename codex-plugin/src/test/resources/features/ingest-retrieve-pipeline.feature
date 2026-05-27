Feature: Ingestion to semantic retrieval pipeline

  Scenario: Query pgvector and retrieve relevant chunks
    Given pgvector contains documents about "machine learning"
    When a query "What is gradient descent?" is performed via CodexRetrieveTask
    Then results contain at least one chunk with similarity > 0.5
    And results are ordered by similarity score descending
    And each result has a source document reference and section path

  Scenario: Empty query returns empty results
    Given pgvector contains indexed documents
    When an empty query string is used
    Then no results are returned
