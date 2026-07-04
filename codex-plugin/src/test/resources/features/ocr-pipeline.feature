Feature: OCR pipeline (CDX-13)

  Scenario: LLM OCR engine produces structured AsciiDoc from image
    Given an image request with language "fr"
    When the LlmOcrEngine processes the image
    Then the result contains structured AsciiDoc text
    And the result model is "gpt-oss:120b-cloud"
    And the result confidence is greater than zero

  Scenario: OCR pipeline falls back to Tesseract when LLM fails
    Given an LLM engine that returns empty text
    And a Tesseract engine that returns "tesseract fallback text"
    When the OcrPipeline processes the image
    Then the result text is "tesseract fallback text"
    And the result model is "tesseract"

  Scenario: OCR pipeline returns empty result when all engines fail
    Given an LLM engine that returns empty text
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