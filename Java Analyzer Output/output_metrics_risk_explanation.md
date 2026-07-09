# SMDSL Risk Explanation

**Migration:** JUnit4-to-JUnit5-SMDSL-v2  
**Overall risk:** CRITICAL (score: 111)

## Risk model

This report explains why the analyzer assigned risk scores. Scores are heuristic and configurable.

| Risk factor | Weight |
|---|---:|
| NoDirectMapping | 3 |
| OperatorHelpManual | 2 |
| OperatorHelp | 1 |
| FullAutomationComplex | 1 |
| MissingStrategy | 1 |
| ManualHeavyStrategy | 1 |
| CleanupRequiredBridge | 1 |
| SyntacticPreservation | 1 |
| RiskIntroduced | 3 |
| ValidationRequired | 2 |
| BehaviorChange | 2 |
| SupportRemoved | 2 |
| SupportExternalized | 1 |
| SupportDeprecated | 1 |
| ConfigurationSemanticsChanged | 1 |
| DefaultValueChanged | 1 |

| Level | Threshold |
|---|---:|
| MEDIUM | 5+ |
| HIGH | 12+ |
| CRITICAL | 25+ |

## Mapping-level risk explanation

### Runner Suite Category and Ordering Migration

| Metric | Value |
|---|---:|
| Risk score | 31 |
| Risk level | CRITICAL |
| Manual step ratio | 65.4% |
| NoDirectMappings | 2 |
| OperatorHelp transformations | 11 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 2 |
| Missing strategies | 0 |

Risk reasons:

- Transformation 'General @RunWith to @ExtendWith': OperatorHelp (+1)
- Transformation 'General @RunWith to @ExtendWith': Manual-heavy strategy (+1)
- Transformation 'MockitoJUnitRunner to MockitoExtension': OperatorHelp (+1) x 2
- Transformation 'SpringRunner to SpringExtension': OperatorHelp (+1) x 2
- Transformation 'Custom Runner to Jupiter Extension Redesign': NoDirectMapping (+3)
- Transformation 'Custom Runner to Jupiter Extension Redesign': Manual-heavy strategy (+1)
- Transformation 'JUnit 4 Suite Runner to JUnit Platform Suite': OperatorHelp (+1)
- Transformation 'JUnit 4 Suite Runner to JUnit Platform Suite': Manual-heavy strategy (+1)
- Transformation 'Suite Selected Classes to SelectPackages': OperatorHelp (+1)
- Transformation 'Suite Selected Classes to SelectPackages': Manual-heavy strategy (+1)
- Transformation 'Categories to Tags': OperatorHelp (+1)
- Transformation 'Categories to Tags': Manual-heavy strategy (+1)
- Transformation 'Method-level Category to Method-level Tag': OperatorHelp (+1)
- Transformation '@FixMethodOrder to @TestMethodOrder': OperatorHelp (+1)
- Transformation '@FixMethodOrder to @TestMethodOrder': Manual-heavy strategy (+1)
- Transformation 'MethodSorters.DEFAULT Approximation': OperatorHelp (+1)
- Transformation 'MethodSorters.DEFAULT Approximation': Manual-heavy strategy (+1)
- Transformation 'MethodSorters.JVM Approximation': NoDirectMapping (+3)
- Transformation 'MethodSorters.JVM Approximation': Manual-heavy strategy (+1)
- SemanticEffect 'JUnit 4 Runners Not Natively Supported By Jupiter': Support removed (+2)
- SemanticEffect 'Category Marker Interfaces Become String Tags': Behavior change (+2)
- SemanticEffect 'Method Orderer Approximation Requires Validation': Validation required (+2)

### Rules ClassRules and Extension Model Migration

| Metric | Value |
|---|---:|
| Risk score | 22 |
| Risk level | HIGH |
| Manual step ratio | 76.9% |
| NoDirectMappings | 1 |
| OperatorHelp transformations | 7 |
| Cleanup-required bridges | 1 |
| Risky semantic effects | 1 |
| Missing strategies | 0 |

Risk reasons:

- Transformation 'TemporaryFolder Rule to @TempDir': OperatorHelp (+1)
- Transformation 'ExternalResource Rule to Extension Callbacks': OperatorHelp (+1)
- Transformation 'ExternalResource Rule to Extension Callbacks': Manual-heavy strategy (+1)
- Transformation 'Verifier and ErrorCollector Rules': OperatorHelp (+1)
- Transformation 'Verifier and ErrorCollector Rules': Manual-heavy strategy (+1)
- Transformation 'ErrorCollector Rule to assertAll': OperatorHelp (+1)
- Transformation 'ErrorCollector Rule to assertAll': Manual-heavy strategy (+1)
- Transformation 'TestName Rule to TestInfo Parameter': OperatorHelp (+1)
- Transformation 'TestWatcher Rule to Jupiter TestWatcher Extension': OperatorHelp (+1)
- Transformation 'TestWatcher Rule to Jupiter TestWatcher Extension': Manual-heavy strategy (+1)
- Transformation 'Custom @Rule to @RegisterExtension or @ExtendWith': NoDirectMapping (+3)
- Transformation 'Custom @Rule to @RegisterExtension or @ExtendWith': Manual-heavy strategy (+1)
- Transformation '@ClassRule to Static @RegisterExtension or Class-Level Extension': OperatorHelp (+1)
- Transformation '@ClassRule to Static @RegisterExtension or Class-Level Extension': Manual-heavy strategy (+1)
- Bridge 'JUnit Jupiter Migration Support Bridge for Selected Rules': Cleanup-required bridge (+1)
- SemanticEffect 'JUnit 4 Rules Not Natively Supported By Jupiter': Support removed (+2)
- SemanticEffect 'Custom Rule Redesign Risk': Risk introduced (+3)

### Parameterized Theories and Advanced Test Forms

| Metric | Value |
|---|---:|
| Risk score | 18 |
| Risk level | HIGH |
| Manual step ratio | 62.5% |
| NoDirectMappings | 1 |
| OperatorHelp transformations | 4 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 1 |
| Missing strategies | 3 |

Risk reasons:

- Transformation 'JUnit 4 Parameterized Runner to Jupiter @ParameterizedTest': OperatorHelp (+1)
- Transformation 'JUnit 4 Parameterized Runner to Jupiter @ParameterizedTest': Manual-heavy strategy (+1)
- Transformation '@Parameters factory to @MethodSource': OperatorHelp (+1)
- Transformation '@Parameters factory to @MethodSource': Manual-heavy strategy (+1)
- Transformation '@Parameter field injection to method parameters': OperatorHelp (+1)
- Transformation '@Parameter field injection to method parameters': Manual-heavy strategy (+1)
- Transformation 'JUnit 4 Theories to Jupiter Parameterized or Dynamic Tests': NoDirectMapping (+3)
- Transformation 'JUnit 4 Theories to Jupiter Parameterized or Dynamic Tests': Manual-heavy strategy (+1)
- Transformation 'JUnit 4 Parameterized Class to Jupiter @ParameterizedClass': OperatorHelp (+1)
- Transformation '@BeforeParam to @BeforeParameterizedClassInvocation': Missing strategy (+1)
- Transformation '@AfterParam to @AfterParameterizedClassInvocation': Missing strategy (+1)
- Transformation '@Parameter to Jupiter Parameter': Missing strategy (+1)
- SemanticEffect 'Parameterized Runner Migration May Restructure Test Shape': Behavior change (+2)
- SemanticEffect 'Theories Have No Direct Jupiter Equivalent': Support removed (+2)

### Assertions and Assumptions Migration

| Metric | Value |
|---|---:|
| Risk score | 15 |
| Risk level | HIGH |
| Manual step ratio | 35.7% |
| NoDirectMappings | 3 |
| OperatorHelp transformations | 2 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 1 |
| Missing strategies | 0 |

Risk reasons:

- Transformation 'JUnit 4 Assert Imports to Jupiter Assertions': OperatorHelp (+1)
- Transformation 'Message-first Assertion Arguments to Message-last Assertion Arguments': OperatorHelp (+1)
- Transformation 'Assume.assumeNoException Approximation': NoDirectMapping (+3)
- Transformation 'Assume.assumeNotNull Approximation': NoDirectMapping (+3)
- Transformation 'Assume.assumeThat and Hamcrest Assumptions': NoDirectMapping (+3)
- Transformation 'Assume.assumeThat and Hamcrest Assumptions': Manual-heavy strategy (+1)
- SemanticEffect 'Failure Message Argument Order Changed': Configuration semantics changed (+1)
- SemanticEffect 'Matcher-Based Assumption Requires Validation': Validation required (+2)

### Exception and Timeout Migration

| Metric | Value |
|---|---:|
| Risk score | 12 |
| Risk level | HIGH |
| Manual step ratio | 62.5% |
| NoDirectMappings | 0 |
| OperatorHelp transformations | 4 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 1 |
| Missing strategies | 0 |

Risk reasons:

- Transformation '@Test(expected) to assertThrows': OperatorHelp (+1)
- Transformation '@Test(expected) to assertThrows': Manual-heavy strategy (+1)
- Transformation 'ExpectedException Rule to assertThrows or Migration Support': OperatorHelp (+1)
- Transformation 'ExpectedException Rule to assertThrows or Migration Support': Manual-heavy strategy (+1)
- Transformation '@Test(timeout) to Timeout Constructs': OperatorHelp (+1)
- Transformation '@Test(timeout) to Timeout Constructs': Manual-heavy strategy (+1)
- Transformation 'Timeout Rule to @Timeout or Timeout Assertions': OperatorHelp (+1)
- Transformation 'Timeout Rule to @Timeout or Timeout Assertions': Manual-heavy strategy (+1)
- SemanticEffect 'Expected Exception Attribute Removed From @Test': Support removed (+2)
- SemanticEffect 'Timeout Annotation Semantics Need Selection': Validation required (+2)

### Build Runtime and Gradual Migration

| Metric | Value |
|---|---:|
| Risk score | 5 |
| Risk level | MEDIUM |
| Manual step ratio | 35.7% |
| NoDirectMappings | 0 |
| OperatorHelp transformations | 3 |
| Cleanup-required bridges | 1 |
| Risky semantic effects | 0 |
| Missing strategies | 0 |

Risk reasons:

- Transformation 'JUnit 4 Runtime to JUnit Vintage Engine': OperatorHelp (+1)
- Transformation 'JUnit 4 Build Dependencies to Jupiter Dependencies': OperatorHelp (+1)
- Bridge 'JUnit Vintage Engine Coexistence Bridge': Cleanup-required bridge (+1)
- SemanticEffect 'Vintage Engine Temporary and Deprecated Support': Support deprecated (+1)
- Transformation 'JUnit 4 Build Execution to JUnit Platform Execution': OperatorHelp (+1)

### Core Test Annotation and Lifecycle Migration

| Metric | Value |
|---|---:|
| Risk score | 5 |
| Risk level | MEDIUM |
| Manual step ratio | 29.2% |
| NoDirectMappings | 0 |
| OperatorHelp transformations | 3 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 0 |
| Missing strategies | 0 |

Risk reasons:

- Transformation '@Test Annotation to Jupiter @Test': OperatorHelp (+1)
- Transformation '@BeforeClass and @AfterClass to @BeforeAll and @AfterAll': OperatorHelp (+1)
- Transformation '@Ignore to @Disabled or Migration-Support IgnoreCondition': OperatorHelp (+1)
- Transformation '@Ignore to @Disabled or Migration-Support IgnoreCondition': Manual-heavy strategy (+1)
- SemanticEffect 'Test Member Visibility Requirement Relaxed': Default value changed (+1)

### Legacy TestCase and Tool-Assisted Migration

| Metric | Value |
|---|---:|
| Risk score | 3 |
| Risk level | LOW |
| Manual step ratio | 28.6% |
| NoDirectMappings | 0 |
| OperatorHelp transformations | 1 |
| Cleanup-required bridges | 0 |
| Risky semantic effects | 1 |
| Missing strategies | 0 |

Risk reasons:

- Transformation 'JUnit 4 TestCase Inheritance to Annotation-Based Jupiter Tests': OperatorHelp (+1)
- SemanticEffect 'Automated Tooling Has Partial Coverage': Validation required (+2)

