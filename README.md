# SMDSL Artifacts

This repository contains the complete set of artifacts supporting the paper:

> **SMDSL: A Domain-Specific Language for Describing Software Platform Migrations**

The artifacts demonstrate the design, application, and evaluation of SMDSL across two case studies:

* **GMF → Sirius**
* **GMF → JointJS**

They include the metamodel, instantiated models, feature analyses, correspondence definitions, and generated reports.

---

📦 Repository Overview

The repository is organized into five main artifact groups, reflecting the workflow presented in the paper:
```
.
├── 1. Core Language
│   └── SMDSL.ecore
│
├── 2. Case Study Models
│   ├── GMFSiriusCaseStudy.model
│   ├── GMFSiriusCaseStudy.model.picto
│   ├── GMFJointJsCaseStudy.model
│   └── GMFJointJsCaseStudy.model.picto
│
├── 3. Feature Identification & Specification
│   ├── GMF Features Identified.png
│   ├── Sirius Features Identified.png
│   ├── JointJs Features Identified.png
│   └── Subset of Features and Correspondences.pdf
│
├── 4. Generated Outputs & Reports
│   ├── GMFSiriusReports/
│   ├── GMFJointJsReports/
│   ├── EntityProcessGMF.png
│   ├── EntityProcessSirius.png
│   └── EntityProcessJointJS.png
│
├── 5. Picto (Visualization)
│   ├── default_picto.egx
│   ├── default_correspondenceDetail.egl
│   └── featureHierarchy.egl
│
└── README.md
```
---

## 🧠 1. SMDSL Metamodel

**File:** `SMDSL.ecore`

This is the core contribution of the work.

It defines the abstract syntax of SMDSL, including:

* **Platforms** (source and target)
* **Features** (structural and behavioral)
* **Correspondences**
* **Mappings**

This metamodel enables:

* explicit modeling of migration knowledge
* representation of correspondences between heterogeneous platforms
* reasoning about automation, mismatch, and traceability

---

## 🧪 2. Case Study Models

### 2.1 GMF → Sirius

* `GMFSiriusCaseStudy.model`
* `GMFSiriusCaseStudy.model.picto`

This model instantiates SMDSL for the migration from **Eclipse GMF** to **Eclipse Sirius**.

---

### 2.2 GMF → JointJS

* `GMFJointJsCaseStudy.model`
* `GMFJointJsCaseStudy.model.picto`

This model represents migration from **GMF** to **JointJS** (cross-ecosystem case).

---

## 📊 3. Feature Identification Artifacts

These images document the extracted feature spaces used in the evaluation:

* `GMF Features Identified.png`
* `Sirius Features Identified.png`
* `JointJs Features Identified.png`

They provide:

* a **feature-oriented view** of each platform
* the basis for defining correspondences
* evidence for the *utility* of SMDSL

---

## 📑 4. Feature & Correspondence Specification

**File:** `Subset of Features and Correspondences.pdf`

This document provides a structured and formalized subset of:

- Identified features across **GMF, Sirius, and JointJS**
- Defined correspondences for:
  - GMF → Sirius
  - GMF → JointJS

### 🔹 Contents

The document is organized into two main parts:

#### Table 1 — Feature Definitions
- Defines key platform features
- Includes:
  - name
  - definition
  - role in the editor

This table operationalizes the feature spaces illustrated in:
- `GMF Features Identified.png`
- `Sirius Features Identified.png`
- `JointJs Features Identified.png`

#### Table 2 — Correspondences
- Defines mappings between source and target features
- Includes:
  - source feature
  - target feature(s)
  - rationale
  - correspondence type

---

## 🔄 5. Example Transformation Outputs

These files illustrate concrete diagram-level outputs across platforms:

* `EntityProcessGMF.png`
* `EntityProcessSirius.png`
* `EntityProcessJointJS.png`

---

## 📄 6. Generated Correspondence Reports

### 6.1 GMF → Sirius Reports

Folder: `GMFSiriusReports/`

Includes detailed reports of correspondence analyses such as:

* Canvas Mapping → Diagram Description
* Compartment Mapping → Container Mapping
* Node Reference → Node/Container Mapping

---

### 6.2 GMF → JointJS Reports

Folder: `GMFJointJsReports/`

Includes the same reports such as:

* Node Mapping → Dia Element
* Compartment Mapping → Visual Attributes
* Polyline → Path Data

---

### 📌 Purpose of Reports

These reports:

* document **how correspondences are realized**
* justify mapping decisions
* expose **mismatches and adaptations**
* support evaluation of:

  * automation degree
  * traceability
  * transformation complexity

---

## 🔍 7. How to Use the Artifacts

### Step 1 — Inspect the Metamodel

Open `SMDSL.ecore` in Eclipse (EMF/Ecore Tools).

### Step 2 — Explore Case Study Models

Open `.model` files with:

* EMF reflective editor, or
* your modeling environment

### Step 3 — Visualize with Picto

Use `.model.picto` files to:

* render structured visualizations
* inspect correspondences graphically

### Step 4 — Review Reports

Browse `GMFSiriusReports/` and `GMFJointJsReports/`:

* each PDF explains a specific mapping
* includes rationale and implementation notes

---

## 🎯 8. What These Artifacts Demonstrate

The artifact suite provides evidence for:

### ✔ Utility

SMDSL can represent migrations across:

* same ecosystem (GMF → Sirius)
* different ecosystems (GMF → JointJS)

### ✔ Traceability

Explicit links between:

* source features
* target features
* transformation logic

### ✔ Mismatch Handling

Captures:

* structural decomposition
* semantic gaps
* required adaptations

### ✔ Automation Potential

Reports indicate:

* which mappings are automatable
* where manual intervention is required

---
## 🎨 Picto Visualizations

The `Picto/` folder contains EGL/EGX templates used to generate:

- feature hierarchy views
- correspondence visualizations
- structured diagram representations

These templates are used in conjunction with `.model.picto` files.
---

## ⚙️ Requirements

To explore the artifacts:

* Eclipse Modeling Framework (EMF)
* Ecore Tools
* (Optional) Picto for visualization

---

## 📌 Notes for Reviewers

* All artifacts are **self-contained**
* No external dependencies are required
* Reports are **human-readable explanations of mappings**

---

## 📬 Contact

For questions or clarifications, please refer to the paper or contact the authors (anonymized for review).

---

