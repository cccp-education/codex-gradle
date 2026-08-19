@licence @routing
Feature: Routed knowledge base export based on PDF license

  Background:
    Given a knowledge base chunks file with source "routed-book" and license "Apache-2.0"
    And a base directory "kb" under a temporary folder

  Scenario: Apache-licensed PDF routes to the OSS zone
    Given a PDF containing the text "Released under the Apache License 2.0."
    When the deploy knowledge base routed task runs with fallback zone "UNKNOWN"
    Then the knowledge base is exported under "OSS/routed-book"
    And the knowledge base contains a "knowledge-base.json" file
    And the knowledge base contains a "knowledge-base.md" file
    And the knowledge base contains a "knowledge-base.adoc" file

  Scenario: Copyright PDF routes to the office zone
    Given a PDF containing the text "© 2024 Acme Corp. All rights reserved."
    When the deploy knowledge base routed task runs with fallback zone "UNKNOWN"
    Then the knowledge base is exported under "office/routed-book"
    And the knowledge base contains a "knowledge-base.json" file

  Scenario: Unknown PDF falls back to the configured project zone
    Given a PDF containing the text "Plain content with no license marker."
    When the deploy knowledge base routed task runs with fallback zone "OSS"
    Then the knowledge base is exported under "OSS/routed-book"
    And the knowledge base contains a "knowledge-base.adoc" file