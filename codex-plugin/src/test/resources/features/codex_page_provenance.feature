@provenance
Feature: Page provenance — the OCR doubt is localised back to the source page

  The chunk knows it is doubtful but not from which page; the acquisition
  report knows which page is doubtful but not which chunks derive from it.
  The page provenance derived join closes the gap (TOC x chunks x report).

  Background:
    Given a page provenance scenario

  Scenario: A chunk is localised to the page of its section
    Given a chunk "chk-1" in section "Chapitre 1 > Organiser le contenu du scénario"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    When the page provenance is resolved
    Then the chunk "chk-1" is localised on page 40
    And the chunk "chk-1" is not doubtful

  Scenario: A multi-page section is localised to all its pages
    Given a chunk "chk-1" in section "Devenir Formateur Professionnel d'Adultes"
    And a toc section "1.0.1" titled "Devenir Formateur Professionnel d'Adultes" on pages 5, 6, 7, 8
    When the page provenance is resolved
    Then the chunk "chk-1" is localised on pages 5, 6, 7, 8

  Scenario: A dot-leader polluted title is normalised before the join
    Given a chunk "chk-1" in section "Chapitre 1 > Organiser le contenu du scénario ..........40"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    When the page provenance is resolved
    Then the chunk "chk-1" is localised on page 40

  Scenario: A polluted leaf is resolved by walking to the matching ancestor
    Given a chunk "chk-1" in section "FPA II > Historique du Titre Professionnel : ...13 > Organiser le contenu du scénario ....40"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    When the page provenance is resolved
    Then the chunk "chk-1" is localised on page 40

  Scenario: An unresolved section degrades silently
    Given a chunk "chk-1" in section "Chapitre 1 > Section absente du TOC"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    When the page provenance is resolved
    Then the chunk "chk-1" is not localised
    And the page provenance join rate is 0

  Scenario: The acquisition doubt is attached to the doubtful page
    Given a chunk "chk-1" in section "Chapitre 1 > Organiser le contenu du scénario"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    And an acquisition doubt on page 40 with reason "ILLISIBLE"
    When the page provenance is resolved
    Then the chunk "chk-1" is doubtful
    And the chunk "chk-1" doubt reason on page 40 is "ILLISIBLE"

  Scenario: A doubt on an unrelated page is not attached
    Given a chunk "chk-1" in section "Chapitre 1 > Organiser le contenu du scénario"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    And an acquisition doubt on page 99 with reason "IMAGE_MISSING"
    When the page provenance is resolved
    Then the chunk "chk-1" is not doubtful

  Scenario: The composite context exposes the page provenance additively
    Given a chunk "chk-1" in section "Chapitre 1 > Organiser le contenu du scénario"
    And a toc section "1.2.1.1" titled "Organiser le contenu du scénario" on page 40
    And a retrieval result in section "Chapitre 1 > Organiser le contenu du scénario"
    When the composite context JSON is built with the page provenance
    Then the composite entry exposes page 40
