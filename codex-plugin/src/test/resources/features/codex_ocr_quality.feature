Feature: OCR quality — true confidence and doubt signals (OCR-QUALITY-1)

  The confidence carried by an OcrResult used to be a hardcoded 0.7
  placeholder for any non-empty text, making a clean page indistinguishable
  from a doubtful one. These scenarios lock the real signal: Tesseract TSV
  word scores averaged into a normalized confidence, garbage credited with
  no confidence, and a degraded engine that preserves its text while
  honestly flagging the doubt.

  @ocr-quality
  Scenario: Tesseract TSV word scores are averaged into a normalized confidence
    Given a Tesseract TSV output with word confidences "96.0" and "94.0"
    When the Tesseract TSV is parsed for confidence
    Then the parsed confidence is 0.95
    And the parsed word count is 2

  @ocr-quality
  Scenario: A garbage image credited with whitespace words yields no confidence
    Given a Tesseract TSV output with only a blank word at confidence "95.0"
    When the Tesseract TSV is parsed for confidence
    Then the parsed confidence is 0.0
    And the parsed word count is 0

  @ocr-quality
  Scenario: A degraded Tesseract without TSV preserves the text and flags the doubt
    Given a stub tesseract that writes "legacy text" without a TSV file
    When the TesseractOcrEngine processes an image with that stub
    Then the engine text is "legacy text"
    And the engine confidence source is "degraded"
    And the engine confidence is 0.0

  @ocr-quality
  Scenario: The pipeline does not discard a degraded engine's text
    Given a degraded OCR engine that returns "collected text" at confidence 0.0
    When the pipeline processes an image with that degraded engine
    Then the pipeline text is "collected text"
    And the pipeline confidence is 0.0

  # ── OCR-QUALITY-2 — problematic-image detection at acquisition ──

  @ocr-quality
  Scenario: An illisible marker flags the page at acquisition
    Given an acquisition page "073" with text "Some body [ILLISIBLE] tail" and confidence 0.9
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "ILLISIBLE"
    And the acquisition issue count is 1

  @ocr-quality
  Scenario: A near-empty page is flagged too short
    Given an acquisition page "074" with text "few" and confidence 0.8
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "TOO_SHORT"
    And the acquisition issue count is 1

  @ocr-quality
  Scenario: A readable low-confidence page is flagged as doubtful
    Given an acquisition page "075" with text "A long enough readable page body above the short threshold." and confidence 0.30
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "LOW_CONFIDENCE"
    And the acquisition issue detail is "0.30"

  @ocr-quality
  Scenario: A confident readable page raises no issue
    Given an acquisition page "076" with text "A long enough readable page body above the short threshold." and confidence 0.91
    When the acquisition quality is analysed
    Then the acquisition issue count is 0

  @ocr-quality
  Scenario: A ghost image reference is flagged as missing
    Given an acquisition page "061" with text "A long body above the threshold.\nimage::cerveau_gauche_vs_cerveau_droit.jpg[]" and confidence 0.9
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "IMAGE_MISSING"
    And the acquisition issue detail is "cerveau_gauche_vs_cerveau_droit.jpg"

  @ocr-quality
  Scenario: The inline image macro form is detected as a ghost
    Given an acquisition page "083_1" with text "A long body above the threshold.\nBody image:arrow-curved-left[] tail." and confidence 0.9
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "IMAGE_MISSING"
    And the acquisition issue detail is "arrow-curved-left"

  @ocr-quality
  Scenario: A doubtful page can carry several structured issues at once
    Given an acquisition page "084" with text "A long body above the threshold.\nimage::missing.png[]" and confidence 0.40
    When the acquisition quality is analysed
    Then the acquisition issue reasons are "LOW_CONFIDENCE,IMAGE_MISSING"
    And the acquisition issue count is 2
