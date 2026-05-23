Feature: Pipeline auto-detection PDF/EPUB

  Scenario: Auto-detect pipeline processes mixed corpus
    Given a corpus directory containing "doc.pdf" and "book.epub"
    When the CodexPipelineTask auto-detect pipeline runs
    Then the PDF file is processed via ExtractBookStructureTask
    And the EPUB file is processed via ExtractEpubStructureTask
    And all results are combined into a single JSON output
    And the output file contains chunks from both documents
