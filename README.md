# SMDSL Artifacts

This repository contains the artifact package for the paper:

> **SMDSL: A Domain-Specific Language for Describing Software Platform Migration**

The artifacts demonstrate the design and use of SMDSL for describing software platform migration knowledge across four scenarios:

- **GMF → Sirius**
- **GMF → JointJS**
- **JUnit 4 → JUnit 5**
- **Spring Boot 2 → Spring Boot 3**

The repository includes the SMDSL metamodel, case-study models, feature-identification artifacts, Picto templates and generated views, and a prototype Java analyzer for computing migration-oriented metrics and reports.

---

## Repository Structure

```text
Artifacts/
├── Core Language/
│   └── SMDSL.ecore
│
├── Case Study Models/
│   ├── GMFtoSiriusCaseStudy_v2.model
│   ├── GMFtoSiriusCaseStudy_v2.model.picto
│   ├── GMFtoJointJSCaseStudy_v2.model
│   ├── GMFtoJointJSCaseStudy_v2.model.picto
│   ├── JUnit4ToJUnit5CaseStudy_v2.model
│   ├── JUnit4ToJUnit5CaseStudy_v2.model.picto
│   ├── SpringBoot2to3CaseStudy_v2.model
│   └── SpringBoot2to3CaseStudy_v2.model.picto
│
├── Feature Identification and Specification/
│   ├── GMF Features.png
│   ├── Sirius Features.png
│   ├── JointJS Features.png
│   ├── JUnit 4 Features.png
│   ├── JUnit 5 Features.png
│   ├── Spring Boot 2.png
│   └── Spring Boot 3.png
│
├── Generated Outputs and Reports/
│   ├── EntityProcessGMF.png
│   ├── EntityProcessSirius.png
│   ├── EntityProcessJointJS.png
│   └── Picto Views/
│       ├── GMF to Sirius/
│       ├── GMF to JointJS/
│       ├── JUnit 4 to JUnit 5/
│       └── Spring Boot 2 to Spring Boot 3/
│
├── Picto/
│   ├── default_picto_v2.egx
│   ├── default_correspondenceDetail_v2.egl
│   └── featureHierarchy_v2.egl
│
├── Java Analyzer Code/
│   ├── models/
│   │   ├── JUnit4ToJUnit5CaseStudy_v2.model
│   │   └── SMDSL.ecore
│   └── smdslanalyzer/
│       ├── SmdslAnalyzer.java
│       └── risk-config-default.properties
│
├── Java Analyzer Output/
│   ├── output_metrics_dashboard.html
│   ├── output_metrics.json
│   ├── output_metrics_metrics.json
│   ├── output_metrics_mappings.csv
│   ├── output_metrics_correspondences.csv
│   ├── output_metrics_coverage.dot
│   ├── output_metrics_coverage.png
│   ├── output_metrics_effort.dot
│   ├── output_metrics_effort.png
│   ├── output_metrics_mapping_risk.dot
│   ├── output_mapping_risk.png
│   ├── output_metrics_default_risk_config.properties
│   ├── output_metrics_risk_explanation.md
│   └── output_metrics_warnings.md
│
└── README.md
```

---

## 1. Core Language

The `Core Language/SMDSL.ecore` file defines the abstract syntax of SMDSL.

SMDSL represents migration knowledge independently of a specific transformation engine. The metamodel supports platforms, features, mappings, correspondences, migration operators, pre-migration requirements, conditions, strategies, evidence, and migration notes.

Use this file as the main language definition when opening or validating the `.model` case-study files.

---

## 2. Case Study Models

The `Case Study Models/` folder contains four SMDSL models.

### GMF → Sirius

This case study describes migration knowledge between two Eclipse-based graphical modeling technologies. It captures correspondences between GMF concepts such as nodes, compartments, labels, and references, and Sirius concepts such as mappings, containers, labels, and semantic candidates.

### GMF → JointJS

This case study describes migration from an Eclipse-based graphical modeling framework to a JavaScript/web-based diagramming framework. It highlights mappings between modeling-framework concepts and browser-oriented diagramming concepts such as graphs, papers, elements, links, views, events, and persistence.

### JUnit 4 → JUnit 5

This case study describes migration knowledge for a testing-framework evolution scenario. It includes mappings for annotations, lifecycle methods, assertions, assumptions, runners, rules, parameterized tests, suites, categories/tags, timeouts, and temporary compatibility mechanisms.

### Spring Boot 2 → Spring Boot 3

This case study describes migration knowledge for a framework-version upgrade. It includes Java 17 requirements, Jakarta namespace migration, Spring Framework 6 changes, Spring Security changes, dependency-management changes, configuration-property changes, Actuator changes, Hibernate-related migration issues, and temporary compatibility bridges.

---

## 3. Feature Identification and Specification

The `Feature Identification and Specification/` folder contains feature-space diagrams for the source and target platforms used in the case studies.

These diagrams document the feature identification stage used before defining SMDSL mappings and correspondences. They are useful for understanding what each platform provides and how the case-study models were scoped.

---

## 4. Generated Outputs and Visualizations

The `Generated Outputs and Reports/` folder contains pre-generated visual artifacts.

The root of this folder includes example entity-process diagrams for GMF, Sirius, and JointJS.

The `Picto Views/` subfolders contain generated HTML views and PNG images for individual correspondences. These views are intended for review without requiring the user to regenerate them. It provides some of the Correspondences discussed in the paper for each migration case.

---

## 5. Picto Templates

The `Picto/` folder contains the EGL and EGX templates used to generate visual views from SMDSL models.

These templates support feature-hierarchy and correspondence-level visualization. They are optional for reviewing the artifact package because generated HTML views are already included, but they can be used to regenerate or customize the views in an Eclipse environment with Epsilon/Picto installed.

---

## 6. Java Analyzer

The `Java Analyzer Code/` folder contains a prototype Java analyzer.

The analyzer loads the SMDSL Ecore file and an SMDSL model dynamically through EMF. It does not rely on generated SMDSL Java classes. It computes reports and exports such as:

- Markdown analysis report
- JSON metrics
- CSV mapping summary
- CSV correspondence summary
- model-quality warnings
- risk explanation report
- Graphviz DOT files for coverage, effort, and mapping risk
- a self-contained HTML dashboard
- optional query results

The `Java Analyzer Output/` folder contains an example output produced by the analyzer.

---

## 7. Requirements

To inspect the artifacts:

- Eclipse Modeling Tools or an Eclipse installation with EMF support
- Eclipse Modeling Framework (EMF)
- Ecore Tools, recommended for metamodel inspection
- Epsilon/Picto, optional for regenerating Picto views
- Java 17 or later, recommended for running the analyzer

To run the Java analyzer inside Eclipse, the project must have EMF libraries available on the build path. The analyzer uses the following EMF packages:

- `org.eclipse.emf.common`
- `org.eclipse.emf.ecore`
- `org.eclipse.emf.ecore.xmi`

In an Eclipse Modeling workspace, these are usually available as plug-in dependencies or can be added manually to the Java build path.

---

## 8. Running the Java Analyzer in Eclipse

The analyzer is intended to be run from an Eclipse workspace where EMF plug-ins are available.

### Step 1 — Import or create a Java project

Create a Java project in Eclipse, or import the `Java Analyzer Code/` folder into an existing Java project.

A convenient project layout is:

```text
Java Analyzer Code/
├── models/
│   ├── JUnit4ToJUnit5CaseStudy_v2.model
│   └── SMDSL.ecore
└── smdslanalyzer/
    ├── SmdslAnalyzer.java
    └── risk-config-default.properties
```

The Java source file declares the package:

```java
package smdslanalyzer;
```

Therefore, the `smdslanalyzer/` folder should be on the Java source path.

### Step 2 — Add EMF dependencies

Add EMF to the project build path.

If using a Plug-in Project, add the following plug-in dependencies:

```text
org.eclipse.emf.common
org.eclipse.emf.ecore
org.eclipse.emf.ecore.xmi
```

If using a regular Java Project in an Eclipse Modeling workspace, add the corresponding EMF libraries from the Eclipse installation or target platform to the build path.

The analyzer depends on EMF for loading the `.ecore` metamodel and `.model` instances dynamically.

### Step 3 — Run the analyzer from Eclipse

Open:

```text
Java Analyzer Code/smdslanalyzer/SmdslAnalyzer.java
```

Then select:

```text
Run As → Java Application
```

If no program arguments are provided, the analyzer runs in console-prompt mode and asks for:

1. the path to the SMDSL Ecore file;
2. the path to the SMDSL model file;
3. the output Markdown report path;
4. the optional risk-configuration file;
5. an optional query name;
6. whether to start the interactive query console.

For example, when prompted, you can enter paths such as:

```text
Java Analyzer Code/models/SMDSL.ecore
Java Analyzer Code/models/JUnit4ToJUnit5CaseStudy_v2.model
Java Analyzer Output/output_metrics.md
Java Analyzer Code/smdslanalyzer/risk-config-default.properties
```

You may also leave the output path empty. In that case, the analyzer writes a default report next to the selected model.

### Step 4 — Optional: run with program arguments

Instead of using the console prompts, you can configure Eclipse program arguments in:

```text
Run Configurations → Arguments → Program arguments
```

Example:

```text
--ecore "Java Analyzer Code/models/SMDSL.ecore"
--model "Java Analyzer Code/models/JUnit4ToJUnit5CaseStudy_v2.model"
--out "Java Analyzer Output/output_metrics.md"
--risk-config "Java Analyzer Code/smdslanalyzer/risk-config-default.properties"
```

The analyzer also supports query mode. Examples:

```text
--ecore "Java Analyzer Code/models/SMDSL.ecore"
--model "Java Analyzer Code/models/JUnit4ToJUnit5CaseStudy_v2.model"
--query list-queries
```

```text
--ecore "Java Analyzer Code/models/SMDSL.ecore"
--model "Java Analyzer Code/models/JUnit4ToJUnit5CaseStudy_v2.model"
--query no-direct-mappings
```

```text
--ecore "Java Analyzer Code/models/SMDSL.ecore"
--model "Java Analyzer Code/models/JUnit4ToJUnit5CaseStudy_v2.model"
--query explain-mapping
--name "Runner Suite Category and Ordering Migration"
```

### Step 5 — Review generated outputs

For an output path such as:

```text
Java Analyzer Output/output_metrics.md
```

the analyzer generates related files with the same prefix, including:

```text
output_metrics.md
output_metrics_metrics.json
output_metrics_mappings.csv
output_metrics_correspondences.csv
output_metrics_warnings.md
output_metrics_risk_explanation.md
output_metrics_mapping_risk.dot
output_metrics_effort.dot
output_metrics_coverage.dot
output_metrics_default_risk_config.properties
output_metrics_query_result.md
output_metrics_dashboard.html
```

The included `Java Analyzer Output/` folder already contains example generated outputs, including the HTML dashboard, CSV files, JSON files, DOT files, PNG visualizations, warning reports, and risk explanations.

---

## 9. Optional: Graphviz Rendering

The analyzer exports Graphviz DOT files for coverage, effort, and mapping risk.

If Graphviz is installed, DOT files can be rendered manually. For example:

```bash
dot -Tpng output_metrics_coverage.dot -o output_metrics_coverage.png
dot -Tpng output_metrics_effort.dot -o output_metrics_effort.png
dot -Tpng output_metrics_mapping_risk.dot -o output_mapping_risk.png
```

PNG versions of the main visualizations are already included in the artifact package.

---

## 10. How to Explore the Artifacts

A recommended review order is:

1. Open `Core Language/SMDSL.ecore` to inspect the metamodel.
2. Open the `.model` files in `Case Study Models/` using the EMF Reflective Editor or Sample Ecore Model Editor.
3. Review the feature diagrams in `Feature Identification and Specification/`.
4. Inspect the generated HTML correspondence views in `Generated Outputs and Reports/Picto Views/`.
5. Review the analyzer outputs in `Java Analyzer Output/`.
6. Optionally run `SmdslAnalyzer.java` from Eclipse using the instructions above.

---

## 11. Notes for Reviewers

- The artifacts are self-contained for inspection.
- The case studies cover both horizontal migration and vertical migration.
- The Java analyzer is a proof-of-concept that demonstrates how SMDSL models can support migration planning, coverage analysis, risk assessment, effort estimation, and model-quality checks.
- Generated outputs are included so that reviewers can inspect the results without running the analyzer.
- Running the analyzer requires an Eclipse/Java environment with EMF dependencies available on the build path.

---

## Contact

Author information has been omitted for anonymous review. For questions or clarifications, please refer to the submitted paper.
