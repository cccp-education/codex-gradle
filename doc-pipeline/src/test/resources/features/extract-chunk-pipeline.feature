Feature: Extract to chunk pipeline

  Scenario: Extract text then chunk document semantically
    Given a source PDF document at "samples/book.pdf"
    When the text is extracted via ExtractTextTask
    And the extracted text is chunked via ChunkDocumentTask
    Then each chunk has a heading level greater than zero
    And each chunk has a non-empty section path
    And all chunks together cover the original document content
