@ontology @derivation
Feature: Ontology derivation from SQL LMD

  Background:
    Given a SQL DDL script with tables linked by foreign keys
    And a JSON LDD file with a section title "Applied AI for Enterprise Java"

  Scenario: Bounded context is named from the LDD title
    When the derive ontology task runs
    Then the ontology mapping contains a bounded context named "Applied AI for Enterprise Java"
    And the bounded context contains the tables "book", "documents" and "paragraphs"

  Scenario: Aggregates are derived from ON DELETE CASCADE foreign keys
    When the derive ontology task runs
    Then the ontology mapping contains an aggregate with root table "book" and dependent "documents"
    And the ontology mapping contains an aggregate with root table "documents" and dependent "paragraphs"

  Scenario: Value objects are tables without outgoing foreign keys
    When the derive ontology task runs
    Then the ontology mapping contains a value object for table "book"
    And the value object "book" has the columns "id", "title" and "created_at"