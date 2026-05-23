Feature: Chunk to vector ingestion pipeline

  Scenario: Chunked document is vectorized and stored in pgvector
    Given a document split into 5 semantic chunks
    When the chunks are vectorized via CodexIngestTask with ONNX AllMiniLmL6V2
    Then each chunk is stored in the codex_chunks table
    And each stored chunk has a non-null embedding vector of dimension 384
    And the codex_documents table has a corresponding document entry
