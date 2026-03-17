# SMDSL Artifacts

This repository contains the complete set of artifacts supporting the paper:

> **SMDSL: A Domain-Specific Language for Describing Software Platform Migrations**

The artifacts demonstrate the design, application, and evaluation of SMDSL across two case studies:

* **GMF → Sirius**
* **GMF → JointJS**

They include the metamodel, instantiated models, feature analyses, correspondence definitions, and generated reports.

---

## 📦 Repository Overview



The repository is organized as follows:

```
.
├── SMDSL.ecore
├── GMFSiriusCaseStudy.model
├── GMFSiriusCaseStudy.model.picto
├── GMFJointJsCaseStudy.model
├── GMFJointJsCaseStudy.model.picto
│
├── GMFSiriusReports/
├── GMFJointJsReports/
│
├── GMF Features Identified.png
├── Sirius Features Identified.png
├── JointJs Features Identified.png
│
├── EntityProcessGMF.png
├── EntityProcessSirius.png
├── EntityProcessJointJS.png
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
* **Transformation-related constructs**

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

It captures:

* identified features in both platforms
* correspondence relationships
* mapping rationale and structure

---

### 2.2 GMF → JointJS

* `GMFJointJsCaseStudy.model`
* `GMFJointJsCaseStudy.model.picto`

This model represents migration from **GMF** to **JointJS** (cross-ecosystem case).

It highlights:

* structural decomposition (e.g., Canvas → Graph + Paper)
* semantic mismatches
* web-based rendering differences

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

## 🔄 4. Example Transformation Outputs

These files illustrate concrete diagram-level outputs across platforms:

* `EntityProcessGMF.png`
* `EntityProcessSirius.png`
* `EntityProcessJointJS.png`

They show:

* how the same conceptual model is rendered differently
* visual differences across platforms
* motivation for explicit correspondence modeling

---

## 📄 5. Generated Correspondence Reports

### 5.1 GMF → Sirius Reports

Folder: `GMFSiriusReports/`

Includes detailed correspondence analyses such as:

* Canvas Mapping → Diagram Description
* Compartment Mapping → Container Mapping
* Node Reference → Node/Container Mapping
* Label Mapping
* Shape resemblance and layout handling

---

### 5.2 GMF → JointJS Reports

Folder: `GMFJointJsReports/`

Includes reports such as:

* Canvas Mapping → Graph + Paper
* Node Mapping → Dia Element
* Compartment Mapping → Visual Attributes
* Polyline → Path Data
* Geometry and layout computations

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

## 🔍 6. How to Use the Artifacts

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

## 🎯 7. What These Artifacts Demonstrate

The artifact suite provides evidence for:

### ✔ Utility

SMDSL can represent migrations across:

* same ecosystem (GMF → Sirius)
* different ecosystems (GMF → JointJS)

### ✔ Expressiveness

Supports:

* structural mappings
* behavioral mappings
* composite correspondences

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
* Case studies were selected to demonstrate:

  * intra-ecosystem migration
  * cross-technology migration

---

## 📬 Contact

For questions or clarifications, please refer to the paper or contact the authors (anonymized for review).

---

