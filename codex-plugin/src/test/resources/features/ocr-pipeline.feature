Feature: OCR pipeline (CDX-13)

  Scenario: OCR pipeline falls back to Tesseract when the primary engine fails
    Given a primary engine that returns empty text
    And a Tesseract engine that returns "tesseract fallback text"
    When the OcrPipeline processes the image
    Then the result text is "tesseract fallback text"
    And the result model is "tesseract"

  Scenario: OCR pipeline returns empty result when all engines fail
    Given a primary engine that returns empty text
    And a Tesseract engine that returns ""
    When the OcrPipeline processes the image
    Then the result text is empty
    And the result confidence is zero

  Scenario: collectOcr task concatenates pages into a single AsciiDoc document
    Given an image directory with 3 page images
    When the collectOcr task runs
    Then the output file contains 3 page sections
    And each section is prefixed with "Page"

  Scenario: collectOcr task produces empty output when no images found
    Given an empty image directory
    When the collectOcr task runs with no images
    Then the output file contains a notice that no images were found

  # ── US-CDX-13-3 — Contrat N2↔N2 : pages individuelles pour document-gradle ──

  @n2-bridge
  Scenario: collectOcr writes one adoc file per page in outputDir (N2↔N2 bridge)
    Given an image directory with 3 page images
    When the collectOcr task writes pages to outputDir
    Then the outputDir contains 3 adoc files
    And each file is named with a 3-digit zero-padded numeric prefix
    And each file contains the page structured text without the legacy header

  @n2-bridge
  Scenario: collectOcr outputDir file names are consumable by document-gradle PageOrder
    Given an image directory with 3 page images
    When the collectOcr task writes pages to outputDir
    Then each file name starts with digits parseable as PageOrder
    And the files are ordered lexicographically by numeric prefix

  @n2-bridge
  Scenario: collectOcr writes no pages in outputDir when no images found
    Given an empty image directory
    When the collectOcr task writes pages to outputDir
    Then the outputDir contains 0 adoc files

  # ── CDX-OCR-4 — Boundary BDD: AI injection, degraded, cache hit (Économie d'Encre) ──

  @ocr-boundary
  Scenario: Injected AI engine succeeds directly without calling Tesseract
    Given an AI engine that returns "ai extracted text" with confidence 0.9
    And a counting Tesseract engine that returns "tesseract fallback text"
    When the OcrPipeline processes the image with both engines
    Then the boundary result text is "ai extracted text"
    And the boundary result model is "ai"
    And the counting Tesseract engine was called 0 times

  @ocr-boundary
  Scenario: Degraded Tesseract-only mode when no AI engine is injected
    Given no AI engine is injected
    And a boundary Tesseract engine that returns "tesseract degraded text"
    When the OcrPipeline processes the image with Tesseract only
    Then the boundary result text is "tesseract degraded text"
    And the boundary result model is "tesseract"

  @ocr-boundary
  Scenario: Cache hit skips the OCR engine (Economie d'Encre)
    Given an OCR cache pre-populated for page "page-1" with text "cached text"
    And a counting OCR engine that returns "fresh ai text" with confidence 0.9
    When the pipeline checks the cache for page "page-1"
    Then the cache returns "cached text"
    And the counting OCR engine was called 0 times