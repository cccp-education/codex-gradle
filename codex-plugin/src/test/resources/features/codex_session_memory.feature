@session-memory
Feature: Session memory RAG bridge — LearnerProfile persistence

  Background:
    Given a session memory bridge with an in-memory fake store

  Scenario: Empty profile produces no embedding signal
    Given a learner profile with no weak points and no annotations
    When the profile embedding text is computed
    Then the embedding text is empty

  Scenario: Weak points are embedded as semantic signal
    Given a learner profile with weak points "gradients" and "backprop"
    When the profile embedding text is computed
    Then the embedding text contains "gradients"
    And the embedding text contains "backprop"

  Scenario: Annotations values are embedded as semantic signal
    Given a learner profile with annotations "mod-3" to "struggled with regularization"
    When the profile embedding text is computed
    Then the embedding text contains "struggled with regularization"

  Scenario: Completed modules are NOT embedded (IDs are not semantic)
    Given a learner profile with completed modules "mod-1" and "mod-2"
    When the profile embedding text is computed
    Then the embedding text is empty

  Scenario: Profile is saved and loaded back by composite key
    Given a learner profile for learner "learner-1" in formation "formation-A" with weak points "gradients"
    When the profile is saved via the session memory bridge
    And the profile is loaded for learner "learner-1" and formation "formation-A"
    Then the loaded profile has weak points "gradients"

  Scenario: Same learner different formations are distinct profiles
    Given a learner profile for learner "learner-3" in formation "formation-X" with weak points "alpha" is saved
    And a learner profile for learner "learner-3" in formation "formation-Y" with weak points "beta" is saved
    When the profile is loaded for learner "learner-3" and formation "formation-X"
    Then the loaded profile has weak points "alpha"
    And loading for learner "learner-3" and formation "formation-Y" returns weak points "beta"

  Scenario: Load returns nothing for an unknown composite key
    When the profile is loaded for learner "unknown" and formation "unknown"
    Then no profile is returned

  Scenario: Save is an upsert — second save overwrites the first
    Given a learner profile for learner "learner-2" in formation "formation-B" with weak points "gradients"
    When the profile is saved via the session memory bridge
    And a learner profile for learner "learner-2" in formation "formation-B" with weak points "backprop" is saved
    And the profile is loaded for learner "learner-2" and formation "formation-B"
    Then the loaded profile has weak points "backprop"

  Scenario: SQL upsert statement uses ON CONFLICT on the composite key
    When the upsert SQL template is inspected
    Then the template inserts into "codex_learner_profiles"
    And the template uses "ON CONFLICT (learner_id, formation_id) DO UPDATE"

  Scenario: SQL schema creates a dedicated learner_profiles table separated from the corpus
    When the schema DDL is inspected
    Then the DDL creates table "codex_learner_profiles"
    And the DDL defines a composite primary key on "learner_id" and "formation_id"
    And the DDL defines an embedding column of dimension 384