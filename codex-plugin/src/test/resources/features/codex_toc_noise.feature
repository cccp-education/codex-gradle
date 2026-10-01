@toc-noise
Feature: TOC noise — table-of-contents entries are flagged doubtful at ingestion

  The acquired corpus rendered the book table of contents as headings with dot
  leaders. The chunker turns them into near-empty chunks that pollute retrieval.
  The ingestion overlays an acquisition-local detector so such a chunk is
  flagged doubtful (zero confidence) through the existing doubt transport —
  no re-chunking, no re-vectorisation, no N1 change.

  Background:
    Given a toc noise scenario

  Scenario: A TOC entry rendered as a dot-leader heading is flagged doubtful
    Given a toc noise chunk "## Merci : ......................3"
    When the chunks are ingested through the toc noise bridge
    Then the toc noise ingested doubt is doubtful
    And the toc noise ingested confidence is zero

  Scenario: A clean chunk keeps full confidence
    Given a toc noise chunk "## Real section"
    When the chunks are ingested through the toc noise bridge
    Then the toc noise ingested doubt is not doubtful
    And the toc noise ingested confidence is full

  Scenario: The illisible OCR policy is preserved
    Given a toc noise chunk "## Real section" and marker "[ILLISIBLE]"
    When the chunks are ingested through the toc noise bridge
    Then the toc noise ingested doubt is doubtful

  Scenario: A real title ending with an ellipsis is not TOC noise
    Given a toc noise title "## 1-1.2 Différencier : activité et séquences..."
    Then the toc noise title is not a table-of-contents heading

  Scenario: A real title with dotted coordinates is not TOC noise
    Given a toc noise title "## 3.3.6 Modérer son exposition aux ondes"
    Then the toc noise title is not a table-of-contents heading

  Scenario: A TOC entry heading is detected
    Given a toc noise title "### Les compétences. ......................"
    Then the toc noise title is a table-of-contents heading
