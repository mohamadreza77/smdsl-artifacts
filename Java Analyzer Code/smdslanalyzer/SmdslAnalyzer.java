package smdslanalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * SMDSL Analyzer V4.
 *
 * A command-line analysis/reporting tool for SMDSL models.
 *
 * The tool loads an SMDSL Ecore file and an SMDSL model dynamically, without
 * relying on generated SMDSL Java classes. It computes coverage, effort, risk,
 * traceability, query results, and model-quality warnings.
 *
 * V3 additions:
 *  - configurable risk scoring through a Java .properties file;
 *  - explicit risk-explanation report;
 *  - Graphviz DOT visual exports for mapping risk, effort, and coverage.
 *
 * V4 additions:
 *  - query mode for targeted inspection of SMDSL models;
 *  - optional interactive query console;
 *  - query-result Markdown export.
 *
 * V5 additions:
 *  - self-contained HTML dashboard export;
 *  - browser-based tables, progress bars, mapping risk details, and correspondence filtering;
 *  - no external CSS or JavaScript dependencies.
 */
public class SmdslAnalyzer {

    private static final Set<String> CORRESPONDENCE_TYPES = new LinkedHashSet<String>(Arrays.asList(
            "Transformation", "Preservation", "Bridge", "SemanticEffect"));

    private static final DecimalFormat PERCENT = new DecimalFormat("0.0");

    public static void main(String[] args) {
        try {
            Config config = Config.from(args);
            if (config == null) {
                config = Config.fromConsole();
            }

            System.out.println("Registering Ecore: " + config.ecorePath);
            ResourceSet resourceSet = new ResourceSetImpl();
            EmfLoader.registerDefaultFactories(resourceSet);
            EPackage ePackage = EmfLoader.registerEcore(resourceSet, config.ecorePath);
            System.out.println("Registered package: " + ePackage.getName() + " [" + ePackage.getNsURI() + "]");

            System.out.println("Loading model: " + config.modelPath);
            EObject migration = EmfLoader.loadRoot(resourceSet, config.modelPath);
            System.out.println("Loaded migration: " + nameOf(migration));

            RiskConfig riskConfig = RiskConfig.load(config.riskConfigPath);
            System.out.println("Risk configuration: " + riskConfig.description());

            AnalysisResult result = new Analyzer(riskConfig).analyze(migration);

            OutputPaths outputs = OutputPaths.from(config.outputPath);
            ensureParentDirectory(outputs.markdownPath);

            Files.write(outputs.markdownPath, new MarkdownReportGenerator().generate(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.metricsJsonPath, new JsonMetricsExporter().generate(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.mappingCsvPath, new CsvExporter().generateMappingCsv(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.correspondenceCsvPath, new CsvExporter().generateCorrespondenceCsv(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.warningsPath, new MarkdownReportGenerator().generateWarningsOnly(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.riskExplanationPath, new RiskExplanationReportGenerator().generate(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.mappingRiskDotPath, new DotExporter().generateMappingRiskDot(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.effortDotPath, new DotExporter().generateEffortDot(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.coverageDotPath, new DotExporter().generateCoverageDot(result).getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.defaultRiskConfigPath, RiskConfig.defaultPropertiesText().getBytes(StandardCharsets.UTF_8));
            Files.write(outputs.dashboardHtmlPath, new HtmlDashboardGenerator().generate(result).getBytes(StandardCharsets.UTF_8));

            if (config.hasQuery()) {
                String queryResult = new QueryEngine().run(result, config.queryName, config.queryArgument);
                Files.write(outputs.queryResultPath, queryResult.getBytes(StandardCharsets.UTF_8));
                System.out.println();
                System.out.println(queryResult);
            }

            System.out.println("Main report generated: " + outputs.markdownPath.toAbsolutePath());
            System.out.println("JSON metrics generated: " + outputs.metricsJsonPath.toAbsolutePath());
            System.out.println("Mapping CSV generated: " + outputs.mappingCsvPath.toAbsolutePath());
            System.out.println("Correspondence CSV generated: " + outputs.correspondenceCsvPath.toAbsolutePath());
            System.out.println("Warnings report generated: " + outputs.warningsPath.toAbsolutePath());
            System.out.println("Risk explanation generated: " + outputs.riskExplanationPath.toAbsolutePath());
            System.out.println("Mapping risk DOT generated: " + outputs.mappingRiskDotPath.toAbsolutePath());
            System.out.println("Effort DOT generated: " + outputs.effortDotPath.toAbsolutePath());
            System.out.println("Coverage DOT generated: " + outputs.coverageDotPath.toAbsolutePath());
            System.out.println("Default risk config template generated: " + outputs.defaultRiskConfigPath.toAbsolutePath());
            System.out.println("HTML dashboard generated: " + outputs.dashboardHtmlPath.toAbsolutePath());
            if (config.hasQuery()) {
                System.out.println("Query result generated: " + outputs.queryResultPath.toAbsolutePath());
            }
            if (config.interactive) {
                new QueryEngine().runInteractive(result);
            }
        } catch (Exception ex) {
            System.err.println("SMDSL Analyzer failed.");
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static class Config {
        String ecorePath;
        String modelPath;
        String outputPath;
        String riskConfigPath;
        String queryName;
        String queryArgument;
        boolean interactive;

        static Config from(String[] args) {
            if (args.length == 0) return null;

            Config config = new Config();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--ecore".equals(arg) && i + 1 < args.length) {
                    config.ecorePath = args[++i];
                } else if ("--model".equals(arg) && i + 1 < args.length) {
                    config.modelPath = args[++i];
                } else if ("--out".equals(arg) && i + 1 < args.length) {
                    config.outputPath = args[++i];
                } else if ("--risk-config".equals(arg) && i + 1 < args.length) {
                    config.riskConfigPath = args[++i];
                } else if ("--query".equals(arg) && i + 1 < args.length) {
                    config.queryName = args[++i];
                } else if ("--name".equals(arg) && i + 1 < args.length) {
                    config.queryArgument = args[++i];
                } else if ("--interactive".equals(arg)) {
                    config.interactive = true;
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage();
                    System.exit(0);
                }
            }

            if (isBlank(config.ecorePath) || isBlank(config.modelPath)) {
                printUsage();
                throw new IllegalArgumentException("Required arguments: --ecore <file.ecore> --model <file.model>");
            }
            if (isBlank(config.outputPath)) {
                config.outputPath = defaultOutputPath(config.modelPath);
            }
            return config;
        }

      static Config fromConsole() {
      Scanner scanner = new Scanner(System.in);
      Config config = new Config();

      System.out.println("Enter the path of the SMDSL Ecore file: ");
      config.ecorePath = scanner.nextLine().trim();

      System.out.println("Enter the path of the SMDSL model file: ");
      config.modelPath = scanner.nextLine().trim();

      System.out.println("Enter the output Markdown report path, or leave empty for default: ");
      config.outputPath = scanner.nextLine().trim();

      if (isBlank(config.outputPath)) {
          config.outputPath = defaultOutputPath(config.modelPath);
      }

      System.out.println("Enter the risk configuration path, or leave empty for default scoring: ");
      config.riskConfigPath = scanner.nextLine().trim();

      System.out.println("Enter a query name, or leave empty to skip query mode. Type list-queries to see available queries: ");
      config.queryName = scanner.nextLine().trim();

      if (!isBlank(config.queryName) && !("list-queries".equalsIgnoreCase(config.queryName) || "help".equalsIgnoreCase(config.queryName))) {
          System.out.println("Enter a query argument/name if needed, or leave empty: ");
          config.queryArgument = scanner.nextLine().trim();
      }

      System.out.println("Start interactive query console after report generation? y/n: ");
      String interactiveAnswer = scanner.nextLine().trim();
      config.interactive = "y".equalsIgnoreCase(interactiveAnswer) || "yes".equalsIgnoreCase(interactiveAnswer);

      return config;
  }

        boolean hasQuery() {
            return !isBlank(queryName);
        }

        static String defaultOutputPath(String modelPath) {
            File modelFile = new File(modelPath);
            String name = modelFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            File parent = modelFile.getAbsoluteFile().getParentFile();
            if (parent == null) parent = new File(".");
            return new File(parent, name + "_analysis_report.md").getPath();
        }

        static void printUsage() {
            System.out.println("Usage:");
            System.out.println("  java smdslanalyzer.SmdslAnalyzer --ecore default_updated.ecore --model JUnit4ToJUnit5.model --out report.md");
            System.out.println("  java smdslanalyzer.SmdslAnalyzer --ecore default_updated.ecore --model JUnit4ToJUnit5.model --out report.md --risk-config risk.properties");
            System.out.println("  java smdslanalyzer.SmdslAnalyzer --ecore default_updated.ecore --model JUnit4ToJUnit5.model --query no-direct-mappings");
            System.out.println("  java smdslanalyzer.SmdslAnalyzer --ecore default_updated.ecore --model JUnit4ToJUnit5.model --query explain-mapping --name \"Runner Suite Category and Ordering Migration\"");
            System.out.println("  java smdslanalyzer.SmdslAnalyzer --ecore default_updated.ecore --model JUnit4ToJUnit5.model --interactive");
            System.out.println();
            System.out.println("Outputs generated next to --out:");
            System.out.println("  report.md");
            System.out.println("  report_metrics.json");
            System.out.println("  report_mappings.csv");
            System.out.println("  report_correspondences.csv");
            System.out.println("  report_warnings.md");
            System.out.println("  report_risk_explanation.md");
            System.out.println("  report_mapping_risk.dot");
            System.out.println("  report_effort.dot");
            System.out.println("  report_coverage.dot");
            System.out.println("  report_default_risk_config.properties");
            System.out.println("  report_query_result.md, when --query is provided");
            System.out.println("  report_dashboard.html");
            System.out.println();
            System.out.println("Available query examples:");
            System.out.println("  list-queries");
            System.out.println("  summary");
            System.out.println("  no-direct-mappings");
            System.out.println("  manual-heavy-transformations");
            System.out.println("  cleanup-bridges");
            System.out.println("  risk-hotspots");
            System.out.println("  missing-strategies");
            System.out.println("  conditions");
            System.out.println("  explain-mapping --name <mapping name>");
            System.out.println("  explain-correspondence --name <correspondence name>");
            System.out.println("  feature-usage --name <feature name>");
        }
    }

    private static class OutputPaths {
        Path markdownPath;
        Path metricsJsonPath;
        Path mappingCsvPath;
        Path correspondenceCsvPath;
        Path warningsPath;
        Path riskExplanationPath;
        Path mappingRiskDotPath;
        Path effortDotPath;
        Path coverageDotPath;
        Path defaultRiskConfigPath;
        Path queryResultPath;
        Path dashboardHtmlPath;

        static OutputPaths from(String markdownOutputPath) {
            OutputPaths paths = new OutputPaths();
            paths.markdownPath = Paths.get(markdownOutputPath);
            String base = stripExtension(markdownOutputPath);
            paths.metricsJsonPath = Paths.get(base + "_metrics.json");
            paths.mappingCsvPath = Paths.get(base + "_mappings.csv");
            paths.correspondenceCsvPath = Paths.get(base + "_correspondences.csv");
            paths.warningsPath = Paths.get(base + "_warnings.md");
            paths.riskExplanationPath = Paths.get(base + "_risk_explanation.md");
            paths.mappingRiskDotPath = Paths.get(base + "_mapping_risk.dot");
            paths.effortDotPath = Paths.get(base + "_effort.dot");
            paths.coverageDotPath = Paths.get(base + "_coverage.dot");
            paths.defaultRiskConfigPath = Paths.get(base + "_default_risk_config.properties");
            paths.queryResultPath = Paths.get(base + "_query_result.md");
            paths.dashboardHtmlPath = Paths.get(base + "_dashboard.html");
            return paths;
        }

        private static String stripExtension(String path) {
            int sep1 = path.lastIndexOf('/');
            int sep2 = path.lastIndexOf('\\');
            int sep = Math.max(sep1, sep2);
            int dot = path.lastIndexOf('.');
            if (dot > sep) return path.substring(0, dot);
            return path;
        }
    }

    private static class EmfLoader {
        static void registerDefaultFactories(ResourceSet resourceSet) {
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("model", new XMIResourceFactoryImpl());
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        }

        static EPackage registerEcore(ResourceSet resourceSet, String ecorePath) throws IOException {
            URI uri = URI.createFileURI(new File(ecorePath).getAbsolutePath());
            Resource resource = resourceSet.getResource(uri, true);
            if (resource.getContents().isEmpty()) {
                throw new IllegalArgumentException("The Ecore file has no root element: " + ecorePath);
            }
            EObject root = resource.getContents().get(0);
            if (!(root instanceof EPackage)) {
                throw new IllegalArgumentException("The Ecore root is not an EPackage: " + root.eClass().getName());
            }
            EPackage ePackage = (EPackage) root;
            EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
            return ePackage;
        }

        static EObject loadRoot(ResourceSet resourceSet, String modelPath) throws IOException {
            URI uri = URI.createFileURI(new File(modelPath).getAbsolutePath());
            Resource resource = resourceSet.getResource(uri, true);
            if (resource.getContents().isEmpty()) {
                throw new IllegalArgumentException("The model has no root element: " + modelPath);
            }
            return resource.getContents().get(0);
        }
    }

    private static class Analyzer {
        private RiskConfig riskConfig;

        Analyzer(RiskConfig riskConfig) {
            this.riskConfig = riskConfig == null ? RiskConfig.defaults() : riskConfig;
        }

        AnalysisResult analyze(EObject migration) {
            AnalysisResult result = new AnalysisResult();
            result.riskConfig = this.riskConfig;
            result.migration = migration;
            result.migrationName = nameOf(migration);

            result.sourcePlatform = asEObject(get(migration, "source"));
            result.targetPlatform = asEObject(get(migration, "target"));
            result.sourcePlatformName = nameOf(result.sourcePlatform);
            result.targetPlatformName = nameOf(result.targetPlatform);

            result.sourceFeatures.addAll(collectPlatformFeatures(result.sourcePlatform));
            result.targetFeatures.addAll(collectPlatformFeatures(result.targetPlatform));
            result.mappings.addAll(list(migration, "mappings"));
            result.preMigrationRequirements.addAll(list(migration, "preMigrationRequirements"));

            for (EObject mapping : result.mappings) {
                MappingAnalysis ma = new MappingAnalysis(mapping);
                result.mappingAnalyses.add(ma);

                for (EObject topLevel : list(mapping, "correspondences")) {
                    collectCorrespondenceFamily(topLevel, result.allCorrespondences);
                    List<EObject> family = new ArrayList<EObject>();
                    collectCorrespondenceFamily(topLevel, family);
                    ma.correspondences.addAll(family);
                }
            }

            for (EObject c : result.allCorrespondences) {
                String type = typeOf(c);
                increment(result.correspondenceTypeCounts, type);

                if ("Transformation".equals(type)) analyzeTransformation(c, result);
                if ("Preservation".equals(type)) analyzePreservation(c, result);
                if ("Bridge".equals(type)) analyzeBridge(c, result);
                if ("SemanticEffect".equals(type)) analyzeSemanticEffect(c, result);

                analyzeConditions(c, result);
                checkCommonCorrespondenceQuality(c, result);
            }

            for (EObject req : result.preMigrationRequirements) {
                increment(result.preMigrationRequirementKindCounts, enumName(get(req, "kind")));
                result.featuresMentionedByPreMigrationRequirements.addAll(list(req, "affectedFeatures"));
                checkPreMigrationRequirementQuality(req, result);
            }

            buildCoverageSets(result);
            detectUncoveredFeatures(result);
            buildMappingAnalyses(result);

            return result;
        }

        private void analyzeTransformation(EObject c, AnalysisResult result) {
            result.transformations.add(c);

            EObject sourceFeature = asEObject(get(c, "sourceFeature"));
            if (sourceFeature != null) result.transformationSourceFeatures.add(sourceFeature);
            result.transformationTargetFeatures.addAll(list(c, "targetFeature"));

            EObject complexity = asEObject(get(c, "complexity"));
            if (complexity != null) {
                String complexityType = typeOf(complexity);
                increment(result.automationTypeCounts, complexityType);
                if ("FullAutomation".equals(complexityType)) {
                    increment(result.difficultyCounts, enumName(get(complexity, "difficulty")));
                    if ("<unspecified>".equals(enumName(get(complexity, "difficulty")))) {
                        result.warnings.add(warning(c, "FullAutomation has no difficulty."));
                    }
                }
                if ("OperatorHelp".equals(complexityType)) {
                    increment(result.helpKindCounts, enumName(get(complexity, "kind")));
                    result.operatorAssistedTransformations.add(c);
                    if (list(complexity, "operators").isEmpty()) {
                        result.warnings.add(warning(c, "OperatorHelp is used but no operator is assigned."));
                    }
                }
                if ("NoDirectMapping".equals(complexityType)) {
                    result.noDirectMappings.add(c);
                    increment(result.difficultyCounts, enumName(get(complexity, "difficulty")));
                    increment(result.helpKindCounts, enumName(get(complexity, "kind")));
                    if (isBlank(string(get(complexity, "rationale")))) {
                        result.warnings.add(warning(c, "NoDirectMapping has no rationale."));
                    }
                    if (list(complexity, "operators").isEmpty()) {
                        result.warnings.add(warning(c, "NoDirectMapping has no assigned operator."));
                    }
                    if (list(complexity, "possibleApproximation").isEmpty()) {
                        result.hints.add(hint(c, "NoDirectMapping has no possibleApproximation. This may be valid, but an approximation helps planning."));
                    }
                }
            } else {
                result.warnings.add(warning(c, "Transformation has no AutomationDegree."));
            }

            EObject strategy = asEObject(get(c, "strategy"));
            if (strategy == null) {
                result.transformationsWithoutStrategy.add(c);
                result.warnings.add(warning(c, "Transformation has no strategy."));
            } else {
                List<EObject> rootSteps = list(strategy, "steps");
                if (rootSteps.isEmpty()) {
                    result.warnings.add(warning(c, "Strategy exists but has no steps."));
                }
                List<EObject> steps = new ArrayList<EObject>();
                collectSteps(rootSteps, steps);
                result.stepsByTransformation.put(c, steps);
                result.strategyStepCount += steps.size();
                for (EObject step : steps) {
                    String stepType = enumName(get(step, "type"));
                    increment(result.stepTypeCounts, stepType);
                    if ("A".equals(stepType)) result.automatedStepCount++;
                    else if ("M".equals(stepType)) result.manualStepCount++;
                    else result.neutralStepCount++;

                    if (isBlank(string(get(step, "description")))) {
                        result.warnings.add(warning(c, "A strategy step has no description."));
                    }
                }
                checkStepOrder(rootSteps, c, result);
            }

            if (sourceFeature == null) {
                result.warnings.add(warning(c, "Transformation has no source feature."));
            }
            if (list(c, "targetFeature").isEmpty()) {
                result.warnings.add(warning(c, "Transformation has no target feature. This may be valid only for explicit no-direct-mapping cases."));
            }
            if (isBlank(string(get(c, "relation")))) {
                result.warnings.add(warning(c, "Transformation has no relation text."));
            }
        }

        private void analyzePreservation(EObject c, AnalysisResult result) {
            result.preservations.add(c);
            increment(result.preservationKindCounts, enumName(get(c, "kind")));
            result.preservedFeatures.addAll(list(c, "preservedFeatures"));
            if (isBlank(string(get(c, "reason")))) {
                result.warnings.add(warning(c, "Preservation has no reason."));
            }
            if (list(c, "preservedFeatures").isEmpty()) {
                result.warnings.add(warning(c, "Preservation has no preserved features."));
            }
            if ("SYNTACTICALLY_PRESERVED".equals(enumName(get(c, "kind")))) {
                result.hints.add(hint(c, "Syntactic preservation can hide semantic drift. Consider adding a SemanticEffect if behavior may change."));
            }
        }

        private void analyzeBridge(EObject c, AnalysisResult result) {
            result.bridges.add(c);
            increment(result.bridgeScopeCounts, enumName(get(c, "scope")));
            result.bridgeFeatures.addAll(list(c, "bridgeFeatures"));
            if (bool(get(c, "cleanupRequired"))) {
                result.cleanupRequiredBridges.add(c);
                result.warnings.add(warning(c, "Bridge requires cleanup after migration."));
            }
            if (isBlank(string(get(c, "rationale")))) {
                result.warnings.add(warning(c, "Bridge has no rationale."));
            }
            if (list(c, "bridgeFeatures").isEmpty()) {
                result.warnings.add(warning(c, "Bridge has no bridge features."));
            }
        }

        private void analyzeSemanticEffect(EObject c, AnalysisResult result) {
            result.semanticEffects.add(c);
            String kind = enumName(get(c, "kind"));
            increment(result.effectKindCounts, kind);
            result.semanticSourceFeatures.addAll(list(c, "affectedSourceFeatures"));
            result.semanticTargetFeatures.addAll(list(c, "affectedTargetFeatures"));

            if (isBlank(string(get(c, "rationale")))) {
                result.warnings.add(warning(c, "SemanticEffect has no rationale."));
            }
            if (list(c, "affectedSourceFeatures").isEmpty() && list(c, "affectedTargetFeatures").isEmpty()) {
                result.hints.add(hint(c, "SemanticEffect has no affected features. This may be global, but feature references help traceability."));
            }
            if ("RISK_INTRODUCED".equals(kind) || "VALIDATION_REQUIRED".equals(kind) || "BEHAVIOR_CHANGE".equals(kind)) {
                result.riskySemanticEffects.add(c);
            }
        }

        private void analyzeConditions(EObject c, AnalysisResult result) {
            List<EObject> conditions = list(c, "conditions");
            if (!conditions.isEmpty()) {
                result.correspondencesWithConditions.add(c);
            }
            for (EObject condition : conditions) {
                increment(result.conditionPolarityCounts, enumName(get(condition, "polarity")));
                result.conditionSubjectFeatures.addAll(list(condition, "subjectFeatures"));
                if (isBlank(string(get(condition, "expression")))) {
                    result.warnings.add(warning(c, "ApplicabilityCondition has no expression."));
                }
                if (list(condition, "subjectFeatures").isEmpty()) {
                    result.hints.add(hint(c, "ApplicabilityCondition has no subject features. This may be intentional for a global condition."));
                }
            }
        }

        private void checkCommonCorrespondenceQuality(EObject c, AnalysisResult result) {
            if (isBlank(nameOf(c))) {
                result.warnings.add("Unnamed " + typeOf(c) + " found.");
            }
        }

        private void checkPreMigrationRequirementQuality(EObject req, AnalysisResult result) {
            if (isBlank(string(get(req, "note")))) {
                result.warnings.add("PreMigrationRequirement of kind " + enumName(get(req, "kind")) + " has no note.");
            }
            if (bool(get(req, "mandatory")) && list(req, "affectedFeatures").isEmpty()) {
                result.hints.add("Mandatory PreMigrationRequirement of kind " + enumName(get(req, "kind")) + " has no affected features. This may be global, but consider linking features if possible.");
            }
        }

        private void buildCoverageSets(AnalysisResult result) {
            result.directlyCoveredSourceFeatures.addAll(result.transformationSourceFeatures);
            result.usedTargetFeatures.addAll(result.transformationTargetFeatures);
            result.usedTargetFeatures.addAll(result.semanticTargetFeatures);

            result.sourceMentionedFeatures.addAll(result.transformationSourceFeatures);
            result.sourceMentionedFeatures.addAll(result.preservedFeatures);
            result.sourceMentionedFeatures.addAll(result.bridgeFeatures);
            result.sourceMentionedFeatures.addAll(result.semanticSourceFeatures);
            result.sourceMentionedFeatures.addAll(result.conditionSubjectFeatures);
            result.sourceMentionedFeatures.addAll(result.featuresMentionedByPreMigrationRequirements);
            result.sourceMentionedFeatures.retainAll(result.sourceFeatures);

            result.targetMentionedFeatures.addAll(result.transformationTargetFeatures);
            result.targetMentionedFeatures.addAll(result.preservedFeatures);
            result.targetMentionedFeatures.addAll(result.bridgeFeatures);
            result.targetMentionedFeatures.addAll(result.semanticTargetFeatures);
            result.targetMentionedFeatures.addAll(result.conditionSubjectFeatures);
            result.targetMentionedFeatures.addAll(result.featuresMentionedByPreMigrationRequirements);
            result.targetMentionedFeatures.retainAll(result.targetFeatures);

            result.allMentionedFeatures.addAll(result.sourceMentionedFeatures);
            result.allMentionedFeatures.addAll(result.targetMentionedFeatures);
        }

        private void detectUncoveredFeatures(AnalysisResult result) {
            for (EObject feature : result.sourceFeatures) {
                if (!result.directlyCoveredSourceFeatures.contains(feature)) {
                    result.uncoveredSourceFeatures.add(feature);
                }
                if (!result.sourceMentionedFeatures.contains(feature)) {
                    result.unmentionedSourceFeatures.add(feature);
                }
            }
            for (EObject feature : result.targetFeatures) {
                if (!result.usedTargetFeatures.contains(feature)) {
                    result.unusedTargetFeatures.add(feature);
                }
                if (!result.targetMentionedFeatures.contains(feature)) {
                    result.unmentionedTargetFeatures.add(feature);
                }
            }
        }

        private void buildMappingAnalyses(AnalysisResult result) {
            for (MappingAnalysis ma : result.mappingAnalyses) {
                for (EObject c : ma.correspondences) {
                    String type = typeOf(c);
                    increment(ma.correspondenceTypeCounts, type);

                    if ("Transformation".equals(type)) {
                        ma.transformations.add(c);
                        EObject complexity = asEObject(get(c, "complexity"));
                        String complexityType = typeOf(complexity);
                        increment(ma.automationTypeCounts, complexityType);
                        if ("OperatorHelp".equals(complexityType)) ma.operatorAssistedCount++;
                        if ("NoDirectMapping".equals(complexityType)) ma.noDirectMappingCount++;
                        if (asEObject(get(c, "strategy")) == null) ma.missingStrategyCount++;

                        List<EObject> steps = result.stepsByTransformation.get(c);
                        if (steps != null) {
                            for (EObject step : steps) {
                                String st = enumName(get(step, "type"));
                                if ("A".equals(st)) ma.automatedSteps++;
                                else if ("M".equals(st)) ma.manualSteps++;
                                else ma.neutralSteps++;
                            }
                        }
                    } else if ("Preservation".equals(type)) {
                        ma.preservations.add(c);
                    } else if ("Bridge".equals(type)) {
                        ma.bridges.add(c);
                        if (bool(get(c, "cleanupRequired"))) ma.cleanupRequiredBridgeCount++;
                    } else if ("SemanticEffect".equals(type)) {
                        ma.semanticEffects.add(c);
                        String kind = enumName(get(c, "kind"));
                        if ("RISK_INTRODUCED".equals(kind) || "VALIDATION_REQUIRED".equals(kind) || "BEHAVIOR_CHANGE".equals(kind)) {
                            ma.riskySemanticEffectCount++;
                        }
                    }

                    RiskAssessment risk = RiskScorer.scoreCorrespondence(c, result, riskConfig);
                    ma.riskScore += risk.score;
                    for (String reason : risk.reasons) {
                        ma.riskReasons.add(typeOf(c) + " '" + nameOf(c) + "': " + reason);
                    }
                }

                ma.totalSteps = ma.automatedSteps + ma.manualSteps + ma.neutralSteps;
                ma.riskLevel = riskConfig.level(ma.riskScore);
                result.totalRiskScore += ma.riskScore;
            }
            result.overallRiskLevel = riskConfig.level(result.totalRiskScore);
        }

        private List<EObject> collectPlatformFeatures(EObject platform) {
            List<EObject> result = new ArrayList<EObject>();
            if (platform == null) return result;
            for (EObject feature : list(platform, "features")) {
                collectFeatureTree(feature, result);
            }
            return result;
        }

        private void collectFeatureTree(EObject feature, List<EObject> result) {
            if (feature == null || result.contains(feature)) return;
            result.add(feature);
            for (EObject child : list(feature, "nestedFeatures")) {
                collectFeatureTree(child, result);
            }
        }

        private void collectCorrespondenceFamily(EObject c, List<EObject> result) {
            if (c == null || result.contains(c)) return;
            if (CORRESPONDENCE_TYPES.contains(typeOf(c))) {
                result.add(c);
            }
            for (EObject child : list(c, "subcorrespondences")) {
                collectCorrespondenceFamily(child, result);
            }
        }

        private void collectSteps(List<EObject> roots, List<EObject> result) {
            for (EObject step : sortByOrder(roots)) {
                result.add(step);
                collectSteps(list(step, "nestedSteps"), result);
            }
        }

        private void checkStepOrder(List<EObject> steps, EObject owner, AnalysisResult result) {
            Set<Integer> seen = new HashSet<Integer>();
            for (EObject step : steps) {
                int order = integer(get(step, "order"));
                if (seen.contains(order)) {
                    result.warnings.add(warning(owner, "Duplicate strategy step order: " + order));
                }
                seen.add(order);
                checkStepOrder(list(step, "nestedSteps"), owner, result);
            }
        }
    }

    private static class RiskScorer {
        static RiskAssessment scoreCorrespondence(EObject c, AnalysisResult result, RiskConfig config) {
            RiskAssessment risk = new RiskAssessment();
            String type = typeOf(c);

            if ("Transformation".equals(type)) {
                EObject complexity = asEObject(get(c, "complexity"));
                String complexityType = typeOf(complexity);

                if ("NoDirectMapping".equals(complexityType)) {
                    risk.add(config.weight("NoDirectMapping"), "NoDirectMapping");
                } else if ("OperatorHelp".equals(complexityType)) {
                    String helpKind = enumName(get(complexity, "kind"));
                    if ("MANUAL".equals(helpKind)) risk.add(config.weight("OperatorHelpManual"), "OperatorHelp(MANUAL)");
                    else risk.add(config.weight("OperatorHelp"), "OperatorHelp");
                } else if ("FullAutomation".equals(complexityType) && "COMPLEX".equals(enumName(get(complexity, "difficulty")))) {
                    risk.add(config.weight("FullAutomationComplex"), "FullAutomation(COMPLEX)");
                }

                if (asEObject(get(c, "strategy")) == null) risk.add(config.weight("MissingStrategy"), "Missing strategy");

                List<EObject> steps = result.stepsByTransformation.get(c);
                if (steps != null) {
                    int manual = 0;
                    int automated = 0;
                    for (EObject step : steps) {
                        String st = enumName(get(step, "type"));
                        if ("M".equals(st)) manual++;
                        if ("A".equals(st)) automated++;
                    }
                    if (manual > automated && manual > 0) risk.add(config.weight("ManualHeavyStrategy"), "Manual-heavy strategy");
                }
            }

            if ("Bridge".equals(type) && bool(get(c, "cleanupRequired"))) {
                risk.add(config.weight("CleanupRequiredBridge"), "Cleanup-required bridge");
            }

            if ("Preservation".equals(type) && "SYNTACTICALLY_PRESERVED".equals(enumName(get(c, "kind")))) {
                risk.add(config.weight("SyntacticPreservation"), "Syntactic preservation may hide semantic drift");
            }

            if ("SemanticEffect".equals(type)) {
                String kind = enumName(get(c, "kind"));
                if ("RISK_INTRODUCED".equals(kind)) risk.add(config.weight("RiskIntroduced"), "Risk introduced");
                else if ("VALIDATION_REQUIRED".equals(kind)) risk.add(config.weight("ValidationRequired"), "Validation required");
                else if ("BEHAVIOR_CHANGE".equals(kind)) risk.add(config.weight("BehaviorChange"), "Behavior change");
                else if ("SUPPORT_REMOVED".equals(kind)) risk.add(config.weight("SupportRemoved"), "Support removed");
                else if ("SUPPORT_EXTERNALIZED".equals(kind)) risk.add(config.weight("SupportExternalized"), "Support externalized");
                else if ("SUPPORT_DEPRECATED".equals(kind)) risk.add(config.weight("SupportDeprecated"), "Support deprecated");
                else if ("CONFIGURATION_SEMANTICS_CHANGED".equals(kind)) risk.add(config.weight("ConfigurationSemanticsChanged"), "Configuration semantics changed");
                else if ("DEFAULT_VALUE_CHANGED".equals(kind)) risk.add(config.weight("DefaultValueChanged"), "Default value changed");
            }

            return risk;
        }
    }

    private static class RiskAssessment {
        int score = 0;
        List<String> reasons = new ArrayList<String>();

        void add(int points, String reason) {
            if (points == 0) return;
            score += points;
            reasons.add(reason + " (+" + points + ")");
        }
    }

    private static class RiskConfig {
        Map<String, Integer> weights = new LinkedHashMap<String, Integer>();
        int mediumThreshold = 5;
        int highThreshold = 12;
        int criticalThreshold = 25;

        static RiskConfig defaults() {
            RiskConfig config = new RiskConfig();
            config.weights.put("NoDirectMapping", 3);
            config.weights.put("OperatorHelpManual", 2);
            config.weights.put("OperatorHelp", 1);
            config.weights.put("FullAutomationComplex", 1);
            config.weights.put("MissingStrategy", 1);
            config.weights.put("ManualHeavyStrategy", 1);
            config.weights.put("CleanupRequiredBridge", 1);
            config.weights.put("SyntacticPreservation", 1);
            config.weights.put("RiskIntroduced", 3);
            config.weights.put("ValidationRequired", 2);
            config.weights.put("BehaviorChange", 2);
            config.weights.put("SupportRemoved", 2);
            config.weights.put("SupportExternalized", 1);
            config.weights.put("SupportDeprecated", 1);
            config.weights.put("ConfigurationSemanticsChanged", 1);
            config.weights.put("DefaultValueChanged", 1);
            return config;
        }

        static RiskConfig load(String path) throws IOException {
            RiskConfig config = defaults();
            if (isBlank(path)) return config;

            Properties properties = new Properties();
            properties.load(Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8));

            for (String key : config.weights.keySet()) {
                String value = properties.getProperty("weight." + key);
                if (!isBlank(value)) {
                    config.weights.put(key, parseInt(value, config.weights.get(key)));
                }
            }

            config.mediumThreshold = parseInt(properties.getProperty("threshold.medium"), config.mediumThreshold);
            config.highThreshold = parseInt(properties.getProperty("threshold.high"), config.highThreshold);
            config.criticalThreshold = parseInt(properties.getProperty("threshold.critical"), config.criticalThreshold);
            return config;
        }

        int weight(String key) {
            Integer value = weights.get(key);
            return value == null ? 0 : value.intValue();
        }

        String level(int score) {
            if (score >= criticalThreshold) return "CRITICAL";
            if (score >= highThreshold) return "HIGH";
            if (score >= mediumThreshold) return "MEDIUM";
            return "LOW";
        }

        String description() {
            return "medium>=" + mediumThreshold + ", high>=" + highThreshold + ", critical>=" + criticalThreshold;
        }

        static String defaultPropertiesText() {
            RiskConfig config = defaults();
            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Analyzer Risk Configuration\n");
            out.append("# Edit the weights and thresholds below, then run with:\n");
            out.append("# java smdslanalyzer.SmdslAnalyzer --ecore model.ecore --model migration.model --out report.md --risk-config risk.properties\n\n");
            for (Map.Entry<String, Integer> entry : config.weights.entrySet()) {
                out.append("weight.").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            out.append("\n");
            out.append("threshold.medium=").append(config.mediumThreshold).append("\n");
            out.append("threshold.high=").append(config.highThreshold).append("\n");
            out.append("threshold.critical=").append(config.criticalThreshold).append("\n");
            return out.toString();
        }

        private static int parseInt(String value, int fallback) {
            if (isBlank(value)) return fallback;
            try {
                return Integer.parseInt(value.trim());
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    private static class MappingAnalysis {
        EObject mapping;
        String name;
        List<EObject> correspondences = new ArrayList<EObject>();
        List<EObject> transformations = new ArrayList<EObject>();
        List<EObject> preservations = new ArrayList<EObject>();
        List<EObject> bridges = new ArrayList<EObject>();
        List<EObject> semanticEffects = new ArrayList<EObject>();

        Map<String, Integer> correspondenceTypeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> automationTypeCounts = new LinkedHashMap<String, Integer>();

        int automatedSteps;
        int manualSteps;
        int neutralSteps;
        int totalSteps;
        int operatorAssistedCount;
        int noDirectMappingCount;
        int cleanupRequiredBridgeCount;
        int riskySemanticEffectCount;
        int missingStrategyCount;
        int riskScore;
        String riskLevel = "LOW";
        List<String> riskReasons = new ArrayList<String>();

        MappingAnalysis(EObject mapping) {
            this.mapping = mapping;
            this.name = nameOf(mapping);
        }

        double manualRatio() {
            if (totalSteps == 0) return 0.0;
            return manualSteps * 100.0 / totalSteps;
        }
    }

    private static class AnalysisResult {
        RiskConfig riskConfig;
        EObject migration;
        String migrationName;
        EObject sourcePlatform;
        EObject targetPlatform;
        String sourcePlatformName;
        String targetPlatformName;

        List<EObject> sourceFeatures = new ArrayList<EObject>();
        List<EObject> targetFeatures = new ArrayList<EObject>();
        List<EObject> mappings = new ArrayList<EObject>();
        List<MappingAnalysis> mappingAnalyses = new ArrayList<MappingAnalysis>();
        List<EObject> allCorrespondences = new ArrayList<EObject>();
        List<EObject> transformations = new ArrayList<EObject>();
        List<EObject> preservations = new ArrayList<EObject>();
        List<EObject> bridges = new ArrayList<EObject>();
        List<EObject> semanticEffects = new ArrayList<EObject>();
        List<EObject> preMigrationRequirements = new ArrayList<EObject>();

        Set<EObject> transformationSourceFeatures = new LinkedHashSet<EObject>();
        Set<EObject> transformationTargetFeatures = new LinkedHashSet<EObject>();
        Set<EObject> directlyCoveredSourceFeatures = new LinkedHashSet<EObject>();
        Set<EObject> usedTargetFeatures = new LinkedHashSet<EObject>();
        Set<EObject> preservedFeatures = new LinkedHashSet<EObject>();
        Set<EObject> bridgeFeatures = new LinkedHashSet<EObject>();
        Set<EObject> semanticSourceFeatures = new LinkedHashSet<EObject>();
        Set<EObject> semanticTargetFeatures = new LinkedHashSet<EObject>();
        Set<EObject> conditionSubjectFeatures = new LinkedHashSet<EObject>();
        Set<EObject> featuresMentionedByPreMigrationRequirements = new LinkedHashSet<EObject>();
        Set<EObject> sourceMentionedFeatures = new LinkedHashSet<EObject>();
        Set<EObject> targetMentionedFeatures = new LinkedHashSet<EObject>();
        Set<EObject> allMentionedFeatures = new LinkedHashSet<EObject>();

        List<EObject> uncoveredSourceFeatures = new ArrayList<EObject>();
        List<EObject> unusedTargetFeatures = new ArrayList<EObject>();
        List<EObject> unmentionedSourceFeatures = new ArrayList<EObject>();
        List<EObject> unmentionedTargetFeatures = new ArrayList<EObject>();
        List<EObject> noDirectMappings = new ArrayList<EObject>();
        List<EObject> operatorAssistedTransformations = new ArrayList<EObject>();
        List<EObject> transformationsWithoutStrategy = new ArrayList<EObject>();
        List<EObject> cleanupRequiredBridges = new ArrayList<EObject>();
        List<EObject> riskySemanticEffects = new ArrayList<EObject>();
        List<EObject> correspondencesWithConditions = new ArrayList<EObject>();

        Map<EObject, List<EObject>> stepsByTransformation = new LinkedHashMap<EObject, List<EObject>>();

        Map<String, Integer> correspondenceTypeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> automationTypeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> stepTypeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> difficultyCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> helpKindCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> preservationKindCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> bridgeScopeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> effectKindCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> conditionPolarityCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> preMigrationRequirementKindCounts = new LinkedHashMap<String, Integer>();

        int strategyStepCount = 0;
        int automatedStepCount = 0;
        int manualStepCount = 0;
        int neutralStepCount = 0;
        int totalRiskScore = 0;
        String overallRiskLevel = "LOW";

        List<String> warnings = new ArrayList<String>();
        List<String> hints = new ArrayList<String>();
    }

    private static class MarkdownReportGenerator {
        String generate(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Analysis Report\n\n");
            out.append("**Migration:** ").append(md(r.migrationName)).append("  \n");
            out.append("**Source platform:** ").append(md(r.sourcePlatformName)).append("  \n");
            out.append("**Target platform:** ").append(md(r.targetPlatformName)).append("  \n");
            out.append("**Overall risk level:** ").append(md(r.overallRiskLevel)).append(" (score: ").append(r.totalRiskScore).append(")\n\n");

            appendExecutiveSummary(out, r);
            appendCoverage(out, r);
            appendEffort(out, r);
            appendMappingLevelAnalysis(out, r);
            appendRiskHotspots(out, r);
            appendRiskScoringModel(out, r);
            appendCorrespondenceTypes(out, r);
            appendPreMigrationRequirements(out, r);
            appendQueries(out, r);
            appendWarningsAndHints(out, r);
            appendDetailedCorrespondenceIndex(out, r);

            return out.toString();
        }

        String generateWarningsOnly(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Model Quality Warnings\n\n");
            out.append("**Migration:** ").append(md(r.migrationName)).append("\n\n");
            appendWarningsAndHints(out, r);
            return out.toString();
        }

        private void appendExecutiveSummary(StringBuilder out, AnalysisResult r) {
            out.append("## 1. Executive Summary\n\n");
            out.append("| Metric | Value |\n");
            out.append("|---|---:|\n");
            row(out, "Source features", r.sourceFeatures.size());
            row(out, "Target features", r.targetFeatures.size());
            row(out, "Mappings", r.mappings.size());
            row(out, "All correspondences", r.allCorrespondences.size());
            row(out, "Transformations", r.transformations.size());
            row(out, "Preservations", r.preservations.size());
            row(out, "Bridges", r.bridges.size());
            row(out, "Semantic effects", r.semanticEffects.size());
            row(out, "Pre-migration requirements", r.preMigrationRequirements.size());
            row(out, "Strategy steps", r.strategyStepCount);
            row(out, "Overall risk score", r.totalRiskScore + " (" + r.overallRiskLevel + ")");
            row(out, "Warnings", r.warnings.size());
            row(out, "Hints", r.hints.size());
            out.append("\n");
        }

        private void appendCoverage(StringBuilder out, AnalysisResult r) {
            out.append("## 2. Coverage Analysis\n\n");
            out.append("| Coverage item | Value |\n");
            out.append("|---|---:|\n");
            row(out, "Source features directly covered by Transformations", countWithTotal(r.directlyCoveredSourceFeatures.size(), r.sourceFeatures.size()));
            row(out, "Source features mentioned anywhere", countWithTotal(r.sourceMentionedFeatures.size(), r.sourceFeatures.size()));
            row(out, "Target features used by Transformations or SemanticEffects", countWithTotal(r.usedTargetFeatures.size(), r.targetFeatures.size()));
            row(out, "Target features mentioned anywhere", countWithTotal(r.targetMentionedFeatures.size(), r.targetFeatures.size()));
            row(out, "Features mentioned by Preservations", r.preservedFeatures.size());
            row(out, "Features mentioned by Bridges", r.bridgeFeatures.size());
            row(out, "Features mentioned by SemanticEffects", unionSize(r.semanticSourceFeatures, r.semanticTargetFeatures));
            row(out, "Features mentioned by ApplicabilityConditions", r.conditionSubjectFeatures.size());
            row(out, "Features mentioned by PreMigrationRequirements", r.featuresMentionedByPreMigrationRequirements.size());
            out.append("\n");

            appendShortFeatureList(out, "Source features not directly covered by Transformations", r.uncoveredSourceFeatures, 40);
            appendShortFeatureList(out, "Source features not mentioned anywhere", r.unmentionedSourceFeatures, 40);
            appendShortFeatureList(out, "Target features not used by Transformations or SemanticEffects", r.unusedTargetFeatures, 40);
            appendShortFeatureList(out, "Target features not mentioned anywhere", r.unmentionedTargetFeatures, 40);
        }

        private void appendEffort(StringBuilder out, AnalysisResult r) {
            out.append("## 3. Effort Analysis\n\n");
            out.append("### Automation degree counts\n\n");
            appendMapTable(out, "Automation degree", "Count", r.automationTypeCounts);

            out.append("### Strategy step counts\n\n");
            out.append("| Step type | Count |\n");
            out.append("|---|---:|\n");
            row(out, "Automated steps (A)", r.automatedStepCount);
            row(out, "Manual steps (M)", r.manualStepCount);
            row(out, "Neutral/unspecified steps (NONE)", r.neutralStepCount);
            row(out, "Manual step ratio", percentage(r.manualStepCount, r.strategyStepCount));
            out.append("\n");

            out.append("### Manual/risk indicators\n\n");
            out.append("| Indicator | Count |\n");
            out.append("|---|---:|\n");
            row(out, "Operator-assisted transformations", r.operatorAssistedTransformations.size());
            row(out, "No-direct mappings", r.noDirectMappings.size());
            row(out, "Cleanup-required bridges", r.cleanupRequiredBridges.size());
            row(out, "Risk/validation semantic effects", r.riskySemanticEffects.size());
            row(out, "Transformations without strategy", r.transformationsWithoutStrategy.size());
            out.append("\n");
        }

        private void appendMappingLevelAnalysis(StringBuilder out, AnalysisResult r) {
            out.append("## 4. Mapping-Level Analysis\n\n");
            if (r.mappingAnalyses.isEmpty()) {
                out.append("No mappings found.\n\n");
                return;
            }

            out.append("| Mapping | Corr. | Trans. | NoDirect | OpHelp | Manual steps | Auto steps | Manual % | Risk |\n");
            out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                row(out,
                    ma.name,
                    ma.correspondences.size(),
                    ma.transformations.size(),
                    ma.noDirectMappingCount,
                    ma.operatorAssistedCount,
                    ma.manualSteps,
                    ma.automatedSteps,
                    PERCENT.format(ma.manualRatio()) + "%",
                    ma.riskScore + " (" + ma.riskLevel + ")");
            }
            out.append("\n");

            out.append("### Highest-risk mappings\n\n");
            List<MappingAnalysis> sorted = new ArrayList<MappingAnalysis>(r.mappingAnalyses);
            Collections.sort(sorted, new Comparator<MappingAnalysis>() {
                @Override
                public int compare(MappingAnalysis a, MappingAnalysis b) {
                    return b.riskScore - a.riskScore;
                }
            });
            boolean any = false;
            for (MappingAnalysis ma : sorted) {
                if (ma.riskScore == 0) continue;
                any = true;
                out.append("- **").append(md(ma.name)).append("**: ")
                        .append(ma.riskScore).append(" (").append(ma.riskLevel).append(")");
                if (!ma.riskReasons.isEmpty()) {
                    out.append(" — ").append(md(shorten(join(ma.riskReasons, "; "), 220)));
                }
                out.append("\n");
            }
            if (!any) out.append("No mapping risk found.\n");
            out.append("\n");
        }

        private void appendRiskHotspots(StringBuilder out, AnalysisResult r) {
            out.append("## 5. Risk Hotspots\n\n");
            appendCorrespondenceList(out, "NoDirectMapping transformations", r.noDirectMappings);
            appendCorrespondenceList(out, "Risk/validation semantic effects", r.riskySemanticEffects);
            appendCorrespondenceList(out, "Cleanup-required bridges", r.cleanupRequiredBridges);
            appendCorrespondenceList(out, "Transformations without strategy", r.transformationsWithoutStrategy);
        }

        private void appendRiskScoringModel(StringBuilder out, AnalysisResult r) {
            out.append("## 6. Risk Scoring Model\n\n");
            out.append("The risk score is a lightweight, configurable heuristic. It is not intended to be a universal risk model; it helps locate migration knowledge that may require more attention.\n\n");
            out.append("| Risk factor | Weight |\n");
            out.append("|---|---:|\n");
            for (Map.Entry<String, Integer> entry : r.riskConfig.weights.entrySet()) {
                row(out, entry.getKey(), entry.getValue());
            }
            out.append("\n");
            out.append("| Risk level | Threshold |\n");
            out.append("|---|---:|\n");
            row(out, "MEDIUM", r.riskConfig.mediumThreshold + "+");
            row(out, "HIGH", r.riskConfig.highThreshold + "+");
            row(out, "CRITICAL", r.riskConfig.criticalThreshold + "+");
            out.append("\n");
        }

        private void appendCorrespondenceTypes(StringBuilder out, AnalysisResult r) {
            out.append("## 7. Correspondence Type Summary\n\n");
            appendMapTable(out, "Correspondence type", "Count", r.correspondenceTypeCounts);

            out.append("### Preservation kinds\n\n");
            appendMapTable(out, "Preservation kind", "Count", r.preservationKindCounts);

            out.append("### Bridge scopes\n\n");
            appendMapTable(out, "Bridge scope", "Count", r.bridgeScopeCounts);

            out.append("### Semantic effect kinds\n\n");
            appendMapTable(out, "Effect kind", "Count", r.effectKindCounts);

            out.append("### Applicability condition polarities\n\n");
            appendMapTable(out, "Condition polarity", "Count", r.conditionPolarityCounts);
        }

        private void appendPreMigrationRequirements(StringBuilder out, AnalysisResult r) {
            out.append("## 8. Pre-Migration Requirements\n\n");
            appendMapTable(out, "Requirement kind", "Count", r.preMigrationRequirementKindCounts);

            if (!r.preMigrationRequirements.isEmpty()) {
                out.append("| Kind | Mandatory | Note | Affected features |\n");
                out.append("|---|---:|---|---|\n");
                for (EObject req : r.preMigrationRequirements) {
                    row(out,
                            enumName(get(req, "kind")),
                            bool(get(req, "mandatory")) ? "yes" : "no",
                            md(shorten(string(get(req, "note")), 140)),
                            md(joinNames(list(req, "affectedFeatures"), ", ")));
                }
                out.append("\n");
            }
        }

        private void appendQueries(StringBuilder out, AnalysisResult r) {
            out.append("## 9. Predefined Query Results\n\n");
            appendCorrespondenceList(out, "Operator-assisted transformations", r.operatorAssistedTransformations);
            appendCorrespondenceList(out, "Correspondences with applicability conditions", r.correspondencesWithConditions);
        }

        private void appendWarningsAndHints(StringBuilder out, AnalysisResult r) {
            out.append("## 10. Model Quality Warnings and Hints\n\n");
            if (r.warnings.isEmpty()) {
                out.append("No warnings found.\n\n");
            } else {
                out.append("### Warnings\n\n");
                for (String warning : r.warnings) {
                    out.append("- ").append(md(warning)).append("\n");
                }
                out.append("\n");
            }

            if (!r.hints.isEmpty()) {
                out.append("### Hints\n\n");
                for (String hint : r.hints) {
                    out.append("- ").append(md(hint)).append("\n");
                }
                out.append("\n");
            }
        }

        private void appendDetailedCorrespondenceIndex(StringBuilder out, AnalysisResult r) {
            out.append("## 11. Correspondence Index\n\n");
            for (EObject mapping : r.mappings) {
                out.append("### Mapping: ").append(md(nameOf(mapping))).append("\n\n");
                for (EObject c : list(mapping, "correspondences")) {
                    appendCorrespondenceTree(out, c, 0);
                }
                out.append("\n");
            }
        }

        private void appendCorrespondenceTree(StringBuilder out, EObject c, int level) {
            String indent = repeat("  ", level);
            out.append(indent).append("- **").append(md(typeOf(c))).append(":** ").append(md(nameOf(c))).append("\n");

            if ("Transformation".equals(typeOf(c))) {
                EObject source = asEObject(get(c, "sourceFeature"));
                out.append(indent).append("  - Source: ").append(md(nameOf(source))).append("\n");
                out.append(indent).append("  - Target(s): ").append(md(joinNames(list(c, "targetFeature"), ", "))).append("\n");
                EObject complexity = asEObject(get(c, "complexity"));
                out.append(indent).append("  - Automation: ").append(md(typeOf(complexity))).append("\n");
            }
            if ("Preservation".equals(typeOf(c))) {
                out.append(indent).append("  - Kind: ").append(md(enumName(get(c, "kind")))).append("\n");
                out.append(indent).append("  - Preserved feature(s): ").append(md(joinNames(list(c, "preservedFeatures"), ", "))).append("\n");
            }
            if ("Bridge".equals(typeOf(c))) {
                out.append(indent).append("  - Scope: ").append(md(enumName(get(c, "scope")))).append("\n");
                out.append(indent).append("  - Cleanup required: ").append(bool(get(c, "cleanupRequired"))).append("\n");
            }
            if ("SemanticEffect".equals(typeOf(c))) {
                out.append(indent).append("  - Kind: ").append(md(enumName(get(c, "kind")))).append("\n");
            }
            for (EObject child : list(c, "subcorrespondences")) {
                appendCorrespondenceTree(out, child, level + 1);
            }
        }

        private void appendShortFeatureList(StringBuilder out, String title, List<EObject> features, int limit) {
            out.append("### ").append(title).append("\n\n");
            if (features.isEmpty()) {
                out.append("None.\n\n");
                return;
            }
            int count = 0;
            for (EObject feature : features) {
                if (count >= limit) break;
                out.append("- ").append(md(nameOf(feature))).append("\n");
                count++;
            }
            if (features.size() > limit) {
                out.append("- ... and ").append(features.size() - limit).append(" more.\n");
            }
            out.append("\n");
        }

        private void appendCorrespondenceList(StringBuilder out, String title, List<EObject> correspondences) {
            out.append("### ").append(title).append("\n\n");
            if (correspondences.isEmpty()) {
                out.append("None.\n\n");
                return;
            }
            for (EObject c : correspondences) {
                out.append("- ").append(md(nameOf(c))).append(" (`").append(typeOf(c)).append("`)\n");
            }
            out.append("\n");
        }

        private void appendMapTable(StringBuilder out, String keyHeader, String valueHeader, Map<String, Integer> map) {
            if (map.isEmpty()) {
                out.append("No data.\n\n");
                return;
            }
            out.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" |\n");
            out.append("|---|---:|\n");
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                row(out, entry.getKey(), entry.getValue());
            }
            out.append("\n");
        }

        private void row(StringBuilder out, String a, Object b) {
            out.append("| ").append(md(a)).append(" | ").append(md(String.valueOf(b))).append(" |\n");
        }

        private void row(StringBuilder out, Object a, Object b, Object c, Object d) {
            out.append("| ").append(md(String.valueOf(a))).append(" | ")
                    .append(md(String.valueOf(b))).append(" | ")
                    .append(md(String.valueOf(c))).append(" | ")
                    .append(md(String.valueOf(d))).append(" |\n");
        }

        private void row(StringBuilder out, Object a, Object b, Object c, Object d, Object e, Object f, Object g, Object h, Object i) {
            out.append("| ")
                    .append(md(String.valueOf(a))).append(" | ")
                    .append(md(String.valueOf(b))).append(" | ")
                    .append(md(String.valueOf(c))).append(" | ")
                    .append(md(String.valueOf(d))).append(" | ")
                    .append(md(String.valueOf(e))).append(" | ")
                    .append(md(String.valueOf(f))).append(" | ")
                    .append(md(String.valueOf(g))).append(" | ")
                    .append(md(String.valueOf(h))).append(" | ")
                    .append(md(String.valueOf(i))).append(" |\n");
        }
    }

    private static class RiskExplanationReportGenerator {
        String generate(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Risk Explanation\n\n");
            out.append("**Migration:** ").append(md(r.migrationName)).append("  \n");
            out.append("**Overall risk:** ").append(md(r.overallRiskLevel)).append(" (score: ").append(r.totalRiskScore).append(")\n\n");

            out.append("## Risk model\n\n");
            out.append("This report explains why the analyzer assigned risk scores. Scores are heuristic and configurable.\n\n");
            out.append("| Risk factor | Weight |\n");
            out.append("|---|---:|\n");
            for (Map.Entry<String, Integer> entry : r.riskConfig.weights.entrySet()) {
                out.append("| ").append(md(entry.getKey())).append(" | ").append(entry.getValue()).append(" |\n");
            }
            out.append("\n");
            out.append("| Level | Threshold |\n");
            out.append("|---|---:|\n");
            out.append("| MEDIUM | ").append(r.riskConfig.mediumThreshold).append("+ |\n");
            out.append("| HIGH | ").append(r.riskConfig.highThreshold).append("+ |\n");
            out.append("| CRITICAL | ").append(r.riskConfig.criticalThreshold).append("+ |\n\n");

            out.append("## Mapping-level risk explanation\n\n");
            List<MappingAnalysis> sorted = new ArrayList<MappingAnalysis>(r.mappingAnalyses);
            Collections.sort(sorted, new Comparator<MappingAnalysis>() {
                @Override
                public int compare(MappingAnalysis a, MappingAnalysis b) {
                    return b.riskScore - a.riskScore;
                }
            });

            for (MappingAnalysis ma : sorted) {
                out.append("### ").append(md(ma.name)).append("\n\n");
                out.append("| Metric | Value |\n");
                out.append("|---|---:|\n");
                out.append("| Risk score | ").append(ma.riskScore).append(" |\n");
                out.append("| Risk level | ").append(md(ma.riskLevel)).append(" |\n");
                out.append("| Manual step ratio | ").append(PERCENT.format(ma.manualRatio())).append("% |\n");
                out.append("| NoDirectMappings | ").append(ma.noDirectMappingCount).append(" |\n");
                out.append("| OperatorHelp transformations | ").append(ma.operatorAssistedCount).append(" |\n");
                out.append("| Cleanup-required bridges | ").append(ma.cleanupRequiredBridgeCount).append(" |\n");
                out.append("| Risky semantic effects | ").append(ma.riskySemanticEffectCount).append(" |\n");
                out.append("| Missing strategies | ").append(ma.missingStrategyCount).append(" |\n\n");

                if (ma.riskReasons.isEmpty()) {
                    out.append("No risk reasons found.\n\n");
                } else {
                    out.append("Risk reasons:\n\n");
                    Map<String, Integer> grouped = groupStrings(ma.riskReasons);
                    for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
                        out.append("- ").append(md(entry.getKey()));
                        if (entry.getValue() > 1) out.append(" x ").append(entry.getValue());
                        out.append("\n");
                    }
                    out.append("\n");
                }
            }
            return out.toString();
        }
    }

    private static class JsonMetricsExporter {
        String generate(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("{\n");
            jsonField(out, "migration", r.migrationName, 1, true);
            jsonField(out, "sourcePlatform", r.sourcePlatformName, 1, true);
            jsonField(out, "targetPlatform", r.targetPlatformName, 1, true);
            jsonField(out, "sourceFeatureCount", r.sourceFeatures.size(), 1, true);
            jsonField(out, "targetFeatureCount", r.targetFeatures.size(), 1, true);
            jsonField(out, "mappingCount", r.mappings.size(), 1, true);
            jsonField(out, "correspondenceCount", r.allCorrespondences.size(), 1, true);
            jsonField(out, "transformationCount", r.transformations.size(), 1, true);
            jsonField(out, "preservationCount", r.preservations.size(), 1, true);
            jsonField(out, "bridgeCount", r.bridges.size(), 1, true);
            jsonField(out, "semanticEffectCount", r.semanticEffects.size(), 1, true);
            jsonField(out, "preMigrationRequirementCount", r.preMigrationRequirements.size(), 1, true);
            jsonField(out, "manualStepRatio", percentage(r.manualStepCount, r.strategyStepCount), 1, true);
            jsonField(out, "overallRiskScore", r.totalRiskScore, 1, true);
            jsonField(out, "overallRiskLevel", r.overallRiskLevel, 1, true);
            jsonField(out, "riskConfig", r.riskConfig.description(), 1, true);

            out.append("  \"coverage\": {\n");
            jsonField(out, "sourceTransformationCoverage", percentage(r.directlyCoveredSourceFeatures.size(), r.sourceFeatures.size()), 2, true);
            jsonField(out, "sourceMentionedCoverage", percentage(r.sourceMentionedFeatures.size(), r.sourceFeatures.size()), 2, true);
            jsonField(out, "targetUsedCoverage", percentage(r.usedTargetFeatures.size(), r.targetFeatures.size()), 2, true);
            jsonField(out, "targetMentionedCoverage", percentage(r.targetMentionedFeatures.size(), r.targetFeatures.size()), 2, false);
            out.append("  },\n");

            out.append("  \"mappingRisks\": [\n");
            for (int i = 0; i < r.mappingAnalyses.size(); i++) {
                MappingAnalysis ma = r.mappingAnalyses.get(i);
                out.append("    {");
                out.append("\"name\":\"").append(json(ma.name)).append("\",");
                out.append("\"riskScore\":").append(ma.riskScore).append(",");
                out.append("\"riskLevel\":\"").append(json(ma.riskLevel)).append("\",");
                out.append("\"manualStepRatio\":\"").append(PERCENT.format(ma.manualRatio())).append("%\",");
                out.append("\"riskReasons\":\"").append(json(shorten(join(ma.riskReasons, "; "), 600))).append("\"");
                out.append("}");
                if (i + 1 < r.mappingAnalyses.size()) out.append(",");
                out.append("\n");
            }
            out.append("  ],\n");

            jsonArray(out, "warnings", r.warnings, 1, true);
            jsonArray(out, "hints", r.hints, 1, false);
            out.append("}\n");
            return out.toString();
        }

        private void jsonField(StringBuilder out, String key, String value, int level, boolean comma) {
            out.append(indent(level)).append("\"").append(json(key)).append("\": \"").append(json(value)).append("\"");
            if (comma) out.append(",");
            out.append("\n");
        }

        private void jsonField(StringBuilder out, String key, int value, int level, boolean comma) {
            out.append(indent(level)).append("\"").append(json(key)).append("\": ").append(value);
            if (comma) out.append(",");
            out.append("\n");
        }

        private void jsonArray(StringBuilder out, String key, List<String> values, int level, boolean comma) {
            out.append(indent(level)).append("\"").append(json(key)).append("\": [");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) out.append(", ");
                out.append("\"").append(json(values.get(i))).append("\"");
            }
            out.append("]");
            if (comma) out.append(",");
            out.append("\n");
        }

        private String indent(int level) {
            return repeat("  ", level);
        }
    }

    private static class CsvExporter {
        String generateMappingCsv(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("mapping,correspondences,transformations,preservations,bridges,semanticEffects,noDirectMappings,operatorAssisted,manualSteps,automatedSteps,manualRatio,riskScore,riskLevel,riskReasons\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                csvRow(out,
                    ma.name,
                    ma.correspondences.size(),
                    ma.transformations.size(),
                    ma.preservations.size(),
                    ma.bridges.size(),
                    ma.semanticEffects.size(),
                    ma.noDirectMappingCount,
                    ma.operatorAssistedCount,
                    ma.manualSteps,
                    ma.automatedSteps,
                    PERCENT.format(ma.manualRatio()) + "%",
                    ma.riskScore,
                    ma.riskLevel,
                    join(ma.riskReasons, "; "));
            }
            return out.toString();
        }

        String generateCorrespondenceCsv(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("mapping,type,name,sourceFeature,targetFeatures,automation,manualSteps,automatedSteps,riskScore,riskReasons\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                for (EObject c : ma.correspondences) {
                    String source = "";
                    String targets = "";
                    String automation = "";
                    int manual = 0;
                    int automated = 0;

                    if ("Transformation".equals(typeOf(c))) {
                        source = nameOf(asEObject(get(c, "sourceFeature")));
                        targets = joinNames(list(c, "targetFeature"), "; ");
                        EObject complexity = asEObject(get(c, "complexity"));
                        automation = typeOf(complexity);

                        List<EObject> steps = r.stepsByTransformation.get(c);
                        if (steps != null) {
                            for (EObject step : steps) {
                                String st = enumName(get(step, "type"));
                                if ("M".equals(st)) manual++;
                                if ("A".equals(st)) automated++;
                            }
                        }
                    } else if ("Preservation".equals(typeOf(c))) {
                        source = joinNames(list(c, "preservedFeatures"), "; ");
                    } else if ("Bridge".equals(typeOf(c))) {
                        source = joinNames(list(c, "bridgeFeatures"), "; ");
                    } else if ("SemanticEffect".equals(typeOf(c))) {
                        source = joinNames(list(c, "affectedSourceFeatures"), "; ");
                        targets = joinNames(list(c, "affectedTargetFeatures"), "; ");
                    }

                    RiskAssessment risk = RiskScorer.scoreCorrespondence(c, r, r.riskConfig);
                    csvRow(out,
                        ma.name,
                        typeOf(c),
                        nameOf(c),
                        source,
                        targets,
                        automation,
                        manual,
                        automated,
                        risk.score,
                        join(risk.reasons, "; "));
                }
            }
            return out.toString();
        }

        private void csvRow(StringBuilder out, Object... values) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(",");
                out.append(csv(String.valueOf(values[i])));
            }
            out.append("\n");
        }

        private String csv(String value) {
            if (value == null) return "";
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }

    private static class DotExporter {
        String generateMappingRiskDot(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("digraph MappingRiskOverview {\n");
            out.append("  graph [rankdir=LR, labelloc=t, label=\"SMDSL Mapping Risk Overview: ").append(dot(r.migrationName)).append("\", fontsize=18];\n");
            out.append("  node [shape=box, style=\"rounded,filled\", fontname=\"Arial\", fontsize=10];\n");
            out.append("  edge [color=\"#666666\"];\n");
            out.append("  migration [label=\"").append(dot(r.migrationName)).append("\\nRisk: ").append(dot(r.overallRiskLevel)).append(" (").append(r.totalRiskScore).append(")\", fillcolor=\"#E6E6E6\"];\n");
            for (int i = 0; i < r.mappingAnalyses.size(); i++) {
                MappingAnalysis ma = r.mappingAnalyses.get(i);
                String id = "m" + i;
                out.append("  ").append(id)
                        .append(" [label=\"").append(dot(ma.name)).append("\\n")
                        .append(ma.riskLevel).append(" (").append(ma.riskScore).append(")\\nManual: ")
                        .append(PERCENT.format(ma.manualRatio())).append("%\", fillcolor=\"")
                        .append(riskColor(ma.riskLevel)).append("\"];\n");
                out.append("  migration -> ").append(id).append(";\n");
            }
            out.append("}\n");
            return out.toString();
        }

        String generateEffortDot(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("digraph EffortOverview {\n");
            out.append("  graph [rankdir=TB, labelloc=t, label=\"SMDSL Effort Overview: ").append(dot(r.migrationName)).append("\", fontsize=18];\n");
            out.append("  node [shape=box, style=\"rounded,filled\", fontname=\"Arial\", fontsize=10, fillcolor=\"#F3F3F3\"];\n");
            out.append("  summary [label=\"Automated steps: ").append(r.automatedStepCount)
                    .append("\\nManual steps: ").append(r.manualStepCount)
                    .append("\\nManual ratio: ").append(percentage(r.manualStepCount, r.strategyStepCount)).append("\", fillcolor=\"#D9EAD3\"];\n");
            for (int i = 0; i < r.mappingAnalyses.size(); i++) {
                MappingAnalysis ma = r.mappingAnalyses.get(i);
                String id = "e" + i;
                out.append("  ").append(id).append(" [label=\"").append(dot(ma.name)).append("\\nManual: ")
                        .append(ma.manualSteps).append("\\nAutomated: ").append(ma.automatedSteps)
                        .append("\\nManual ratio: ").append(PERCENT.format(ma.manualRatio())).append("%\", fillcolor=\"")
                        .append(effortColor(ma.manualRatio())).append("\"];\n");
                out.append("  summary -> ").append(id).append(";\n");
            }
            out.append("}\n");
            return out.toString();
        }

        String generateCoverageDot(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("digraph CoverageOverview {\n");
            out.append("  graph [rankdir=LR, labelloc=t, label=\"SMDSL Coverage Overview: ").append(dot(r.migrationName)).append("\", fontsize=18];\n");
            out.append("  node [shape=box, style=\"rounded,filled\", fontname=\"Arial\", fontsize=10];\n");
            out.append("  source [label=\"Source features\\n").append(r.sourceFeatures.size()).append("\", fillcolor=\"#D9EAD3\"];\n");
            out.append("  target [label=\"Target features\\n").append(r.targetFeatures.size()).append("\", fillcolor=\"#D9EAD3\"];\n");
            out.append("  stc [label=\"Source transformation coverage\\n").append(countWithTotal(r.directlyCoveredSourceFeatures.size(), r.sourceFeatures.size())).append("\", fillcolor=\"#FFF2CC\"];\n");
            out.append("  smc [label=\"Source mentioned coverage\\n").append(countWithTotal(r.sourceMentionedFeatures.size(), r.sourceFeatures.size())).append("\", fillcolor=\"#FFF2CC\"];\n");
            out.append("  tuc [label=\"Target used coverage\\n").append(countWithTotal(r.usedTargetFeatures.size(), r.targetFeatures.size())).append("\", fillcolor=\"#FCE5CD\"];\n");
            out.append("  tmc [label=\"Target mentioned coverage\\n").append(countWithTotal(r.targetMentionedFeatures.size(), r.targetFeatures.size())).append("\", fillcolor=\"#FCE5CD\"];\n");
            out.append("  source -> stc;\n");
            out.append("  source -> smc;\n");
            out.append("  target -> tuc;\n");
            out.append("  target -> tmc;\n");
            out.append("}\n");
            return out.toString();
        }

        private String riskColor(String level) {
            if ("CRITICAL".equals(level)) return "#F4CCCC";
            if ("HIGH".equals(level)) return "#FCE5CD";
            if ("MEDIUM".equals(level)) return "#FFF2CC";
            return "#D9EAD3";
        }

        private String effortColor(double manualRatio) {
            if (manualRatio >= 70.0) return "#F4CCCC";
            if (manualRatio >= 50.0) return "#FCE5CD";
            if (manualRatio >= 30.0) return "#FFF2CC";
            return "#D9EAD3";
        }
    }



    private static class HtmlDashboardGenerator {
        String generate(AnalysisResult r) {
            StringBuilder out = new StringBuilder();
            out.append("<!doctype html>\n");
            out.append("<html lang=\"en\">\n");
            out.append("<head>\n");
            out.append("<meta charset=\"utf-8\">\n");
            out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
            out.append("<title>SMDSL Dashboard - ").append(h(r.migrationName)).append("</title>\n");
            appendStyle(out);
            appendScript(out);
            out.append("</head>\n");
            out.append("<body>\n");
            appendHeader(out, r);
            appendSummaryCards(out, r);
            appendCoverageSection(out, r);
            appendEffortSection(out, r);
            appendMappingRiskSection(out, r);
            appendRiskExplanationSection(out, r);
            appendWarningsSection(out, r);
            appendCorrespondenceExplorer(out, r);
            appendFooter(out);
            out.append("</body>\n");
            out.append("</html>\n");
            return out.toString();
        }

        private void appendHeader(StringBuilder out, AnalysisResult r) {
            out.append("<header class=\"hero\">\n");
            out.append("  <div>\n");
            out.append("    <p class=\"eyebrow\">SMDSL Analyzer Dashboard</p>\n");
            out.append("    <h1>").append(h(r.migrationName)).append("</h1>\n");
            out.append("    <p class=\"subtitle\"><strong>").append(h(r.sourcePlatformName)).append("</strong> &rarr; <strong>").append(h(r.targetPlatformName)).append("</strong></p>\n");
            out.append("  </div>\n");
            out.append("  <div class=\"hero-risk ").append(riskClass(r.overallRiskLevel)).append("\">\n");
            out.append("    <span>Overall risk</span>\n");
            out.append("    <strong>").append(h(r.overallRiskLevel)).append("</strong>\n");
            out.append("    <em>score ").append(r.totalRiskScore).append("</em>\n");
            out.append("  </div>\n");
            out.append("</header>\n");
            out.append("<nav class=\"nav\">\n");
            out.append("  <a href=\"#summary\">Summary</a>\n");
            out.append("  <a href=\"#coverage\">Coverage</a>\n");
            out.append("  <a href=\"#effort\">Effort</a>\n");
            out.append("  <a href=\"#mapping-risk\">Mapping risk</a>\n");
            out.append("  <a href=\"#warnings\">Warnings</a>\n");
            out.append("  <a href=\"#correspondences\">Correspondences</a>\n");
            out.append("</nav>\n");
        }

        private void appendSummaryCards(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"summary\" class=\"section\">\n");
            out.append("<h2>Executive summary</h2>\n");
            out.append("<div class=\"cards\">\n");
            card(out, "Source features", r.sourceFeatures.size(), "Platform concepts captured for the source side.");
            card(out, "Target features", r.targetFeatures.size(), "Platform concepts captured for the target side.");
            card(out, "Mappings", r.mappings.size(), "Top-level migration knowledge groups.");
            card(out, "Correspondences", r.allCorrespondences.size(), "Transformations, preservations, bridges, and semantic effects.");
            card(out, "Transformations", r.transformations.size(), "Source-to-target migration relations.");
            card(out, "Strategy steps", r.strategyStepCount, "Automated and manual migration steps.");
            card(out, "Manual step ratio", percentage(r.manualStepCount, r.strategyStepCount), "Manual effort among all strategy steps.");
            card(out, "Warnings", r.warnings.size(), "Model-quality warnings detected by the analyzer.");
            out.append("</div>\n");
            out.append("</section>\n");
        }

        private void appendCoverageSection(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"coverage\" class=\"section\">\n");
            out.append("<h2>Coverage analysis</h2>\n");
            out.append("<p class=\"section-note\">Transformation coverage only counts source features that directly participate as transformation sources. Mentioned coverage also considers preservation, bridges, semantic effects, applicability conditions, and pre-migration requirements.</p>\n");
            out.append("<div class=\"grid two\">\n");
            metricBar(out, "Source transformation coverage", r.directlyCoveredSourceFeatures.size(), r.sourceFeatures.size());
            metricBar(out, "Source mentioned coverage", r.sourceMentionedFeatures.size(), r.sourceFeatures.size());
            metricBar(out, "Target used coverage", r.usedTargetFeatures.size(), r.targetFeatures.size());
            metricBar(out, "Target mentioned coverage", r.targetMentionedFeatures.size(), r.targetFeatures.size());
            out.append("</div>\n");
            out.append("<details class=\"panel\"><summary>Unmentioned source features (").append(r.unmentionedSourceFeatures.size()).append(")</summary>");
            featureList(out, r.unmentionedSourceFeatures);
            out.append("</details>\n");
            out.append("<details class=\"panel\"><summary>Unmentioned target features (").append(r.unmentionedTargetFeatures.size()).append(")</summary>");
            featureList(out, r.unmentionedTargetFeatures);
            out.append("</details>\n");
            out.append("</section>\n");
        }

        private void appendEffortSection(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"effort\" class=\"section\">\n");
            out.append("<h2>Effort analysis</h2>\n");
            out.append("<div class=\"cards\">\n");
            card(out, "Automated steps", r.automatedStepCount, "Strategy steps marked as A.");
            card(out, "Manual steps", r.manualStepCount, "Strategy steps marked as M.");
            card(out, "NoDirectMappings", r.noDirectMappings.size(), "Transformations without direct target equivalence.");
            card(out, "OperatorHelp", r.operatorAssistedTransformations.size(), "Transformations requiring operator involvement.");
            out.append("</div>\n");
            out.append("<div class=\"panel\">\n");
            out.append("<h3>Manual effort by mapping</h3>\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                out.append("<div class=\"mini-row\">\n");
                out.append("<span>").append(h(ma.name)).append("</span>\n");
                out.append("<div class=\"bar\"><i style=\"width:").append(PERCENT.format(ma.manualRatio())).append("%\"></i></div>\n");
                out.append("<strong>").append(PERCENT.format(ma.manualRatio())).append("%</strong>\n");
                out.append("</div>\n");
            }
            out.append("</div>\n");
            out.append("</section>\n");
        }

        private void appendMappingRiskSection(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"mapping-risk\" class=\"section\">\n");
            out.append("<h2>Mapping-level risk</h2>\n");
            out.append("<div class=\"table-wrap\">\n");
            out.append("<table>\n");
            out.append("<thead><tr><th>Mapping</th><th>Corr.</th><th>Trans.</th><th>NoDirect</th><th>OpHelp</th><th>Manual steps</th><th>Automated steps</th><th>Manual %</th><th>Risk</th></tr></thead>\n");
            out.append("<tbody>\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                out.append("<tr>\n");
                out.append("<td>").append(h(ma.name)).append("</td>");
                out.append("<td>").append(ma.correspondences.size()).append("</td>");
                out.append("<td>").append(ma.transformations.size()).append("</td>");
                out.append("<td>").append(ma.noDirectMappingCount).append("</td>");
                out.append("<td>").append(ma.operatorAssistedCount).append("</td>");
                out.append("<td>").append(ma.manualSteps).append("</td>");
                out.append("<td>").append(ma.automatedSteps).append("</td>");
                out.append("<td>").append(PERCENT.format(ma.manualRatio())).append("%</td>");
                out.append("<td><span class=\"badge ").append(riskClass(ma.riskLevel)).append("\">").append(h(ma.riskLevel)).append(" ").append(ma.riskScore).append("</span></td>");
                out.append("</tr>\n");
            }
            out.append("</tbody></table></div>\n");
            out.append("</section>\n");
        }

        private void appendRiskExplanationSection(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"risk-explanation\" class=\"section\">\n");
            out.append("<h2>Risk explanation</h2>\n");
            out.append("<p class=\"section-note\">Risk scores are lightweight configurable heuristics. They are intended to identify migration knowledge that may require additional attention, not to provide a universal risk model.</p>\n");
            List<MappingAnalysis> sorted = new ArrayList<MappingAnalysis>(r.mappingAnalyses);
            Collections.sort(sorted, new Comparator<MappingAnalysis>() {
                @Override
                public int compare(MappingAnalysis a, MappingAnalysis b) {
                    return b.riskScore - a.riskScore;
                }
            });
            for (MappingAnalysis ma : sorted) {
                out.append("<details class=\"panel risk-detail\">");
                out.append("<summary><span>").append(h(ma.name)).append("</span><span class=\"badge ").append(riskClass(ma.riskLevel)).append("\">").append(h(ma.riskLevel)).append(" ").append(ma.riskScore).append("</span></summary>\n");
                out.append("<div class=\"detail-grid\">\n");
                smallMetric(out, "Manual ratio", PERCENT.format(ma.manualRatio()) + "%");
                smallMetric(out, "NoDirectMappings", ma.noDirectMappingCount);
                smallMetric(out, "OperatorHelp", ma.operatorAssistedCount);
                smallMetric(out, "Cleanup bridges", ma.cleanupRequiredBridgeCount);
                smallMetric(out, "Risky effects", ma.riskySemanticEffectCount);
                smallMetric(out, "Missing strategies", ma.missingStrategyCount);
                out.append("</div>\n");
                if (ma.riskReasons.isEmpty()) {
                    out.append("<p>No risk reasons found.</p>");
                } else {
                    out.append("<ul class=\"compact-list\">\n");
                    for (Map.Entry<String, Integer> entry : groupStrings(ma.riskReasons).entrySet()) {
                        out.append("<li>").append(h(entry.getKey()));
                        if (entry.getValue() > 1) out.append(" <strong>x ").append(entry.getValue()).append("</strong>");
                        out.append("</li>\n");
                    }
                    out.append("</ul>\n");
                }
                out.append("</details>\n");
            }
            out.append("</section>\n");
        }

        private void appendWarningsSection(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"warnings\" class=\"section\">\n");
            out.append("<h2>Warnings and model-improvement suggestions</h2>\n");
            if (r.warnings.isEmpty() && r.hints.isEmpty()) {
                out.append("<div class=\"panel success\">No warnings or hints found.</div>\n");
            } else {
                if (!r.warnings.isEmpty()) {
                    out.append("<div class=\"panel\"><h3>Warnings</h3><ul class=\"compact-list\">\n");
                    for (String warning : r.warnings) out.append("<li>").append(h(warning)).append("</li>\n");
                    out.append("</ul></div>\n");
                }
                if (!r.hints.isEmpty()) {
                    out.append("<div class=\"panel\"><h3>Hints</h3><ul class=\"compact-list\">\n");
                    for (String hint : r.hints) out.append("<li>").append(h(hint)).append("</li>\n");
                    out.append("</ul></div>\n");
                }
            }
            out.append("</section>\n");
        }

        private void appendCorrespondenceExplorer(StringBuilder out, AnalysisResult r) {
            out.append("<section id=\"correspondences\" class=\"section\">\n");
            out.append("<h2>Correspondence explorer</h2>\n");
            out.append("<p class=\"section-note\">Use the search box to filter by mapping, correspondence name, type, source/target feature, automation degree, or risk reason.</p>\n");
            out.append("<input id=\"correspondenceSearch\" class=\"search\" type=\"search\" placeholder=\"Search correspondences...\" oninput=\"filterTable('correspondenceSearch','correspondenceTable')\">\n");
            out.append("<div class=\"table-wrap\">\n");
            out.append("<table id=\"correspondenceTable\">\n");
            out.append("<thead><tr><th>Mapping</th><th>Type</th><th>Name</th><th>Source / affected feature</th><th>Target feature(s)</th><th>Automation</th><th>Risk</th></tr></thead>\n");
            out.append("<tbody>\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                for (EObject c : ma.correspondences) {
                    String source = "";
                    String targets = "";
                    String automation = "";
                    if ("Transformation".equals(typeOf(c))) {
                        source = nameOf(asEObject(get(c, "sourceFeature")));
                        targets = joinNames(list(c, "targetFeature"), ", ");
                        automation = typeOf(asEObject(get(c, "complexity")));
                    } else if ("Preservation".equals(typeOf(c))) {
                        source = joinNames(list(c, "preservedFeatures"), ", ");
                        automation = enumName(get(c, "kind"));
                    } else if ("Bridge".equals(typeOf(c))) {
                        source = joinNames(list(c, "bridgeFeatures"), ", ");
                        automation = enumName(get(c, "scope"));
                    } else if ("SemanticEffect".equals(typeOf(c))) {
                        source = joinNames(list(c, "affectedSourceFeatures"), ", ");
                        targets = joinNames(list(c, "affectedTargetFeatures"), ", ");
                        automation = enumName(get(c, "kind"));
                    }
                    RiskAssessment risk = RiskScorer.scoreCorrespondence(c, r, r.riskConfig);
                    String level = r.riskConfig.level(risk.score);
                    out.append("<tr>\n");
                    out.append("<td>").append(h(ma.name)).append("</td>");
                    out.append("<td><span class=\"type-pill\">").append(h(typeOf(c))).append("</span></td>");
                    out.append("<td>").append(h(nameOf(c))).append("</td>");
                    out.append("<td>").append(h(source)).append("</td>");
                    out.append("<td>").append(h(targets)).append("</td>");
                    out.append("<td>").append(h(automation)).append("</td>");
                    out.append("<td><span title=\"").append(h(join(risk.reasons, "; "))).append("\" class=\"badge ").append(riskClass(level)).append("\">").append(risk.score).append("</span></td>");
                    out.append("</tr>\n");
                }
            }
            out.append("</tbody></table></div>\n");
            out.append("</section>\n");
        }

        private void appendFooter(StringBuilder out) {
            out.append("<footer class=\"footer\">Generated by SMDSL Analyzer V5. This dashboard is self-contained and does not require external CSS or JavaScript.</footer>\n");
        }

        private void appendStyle(StringBuilder out) {
            out.append("<style>\n");
            out.append(":root{--bg:#f6f7fb;--card:#fff;--ink:#1f2937;--muted:#64748b;--line:#e5e7eb;--shadow:0 12px 30px rgba(15,23,42,.08);--green:#d9ead3;--yellow:#fff2cc;--orange:#fce5cd;--red:#f4cccc;}\n");
            out.append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:Inter,Segoe UI,Roboto,Arial,sans-serif;line-height:1.45}a{color:#1d4ed8;text-decoration:none}.hero{display:flex;justify-content:space-between;gap:24px;align-items:center;padding:40px 56px;background:linear-gradient(135deg,#111827,#334155);color:white}.eyebrow{text-transform:uppercase;letter-spacing:.14em;font-size:12px;color:#cbd5e1;margin:0 0 8px}.hero h1{font-size:36px;margin:0}.subtitle{color:#dbeafe}.hero-risk{min-width:180px;border-radius:18px;padding:18px;text-align:center;color:#111827;box-shadow:var(--shadow)}.hero-risk span,.hero-risk em{display:block}.hero-risk strong{display:block;font-size:28px}.nav{position:sticky;top:0;z-index:10;background:white;border-bottom:1px solid var(--line);padding:10px 56px;display:flex;gap:18px;flex-wrap:wrap}.section{margin:28px 56px;padding:28px;background:var(--card);border:1px solid var(--line);border-radius:22px;box-shadow:var(--shadow)}.section h2{margin-top:0;font-size:26px}.section-note{color:var(--muted);max-width:1050px}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:16px}.card{border:1px solid var(--line);border-radius:18px;padding:18px;background:#fff}.card span{color:var(--muted);font-size:13px}.card strong{display:block;font-size:28px;margin:8px 0}.grid.two{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}.metric{border:1px solid var(--line);border-radius:18px;padding:18px;background:#fff}.metric-head{display:flex;justify-content:space-between;gap:12px;margin-bottom:12px}.bar{height:12px;background:#e5e7eb;border-radius:999px;overflow:hidden}.bar i{display:block;height:100%;background:#2563eb;border-radius:999px}.panel{border:1px solid var(--line);border-radius:18px;background:white;padding:18px;margin-top:16px}.panel summary{cursor:pointer;font-weight:700;display:flex;justify-content:space-between;gap:16px}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:18px;background:#fff}table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:11px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}thead th{position:sticky;top:0;background:#f8fafc;z-index:1}.badge,.type-pill{display:inline-block;border-radius:999px;padding:4px 10px;font-weight:700;font-size:12px}.risk-low{background:var(--green)}.risk-medium{background:var(--yellow)}.risk-high{background:var(--orange)}.risk-critical{background:var(--red)}.type-pill{background:#e0f2fe;color:#075985}.detail-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:14px 0}.small-metric{background:#f8fafc;border:1px solid var(--line);border-radius:14px;padding:12px}.small-metric span{display:block;color:var(--muted);font-size:12px}.small-metric strong{font-size:20px}.compact-list{margin:10px 0 0;padding-left:20px}.compact-list li{margin:6px 0}.mini-row{display:grid;grid-template-columns:minmax(220px,1fr) 2fr 70px;gap:12px;align-items:center;margin:12px 0}.search{width:100%;padding:14px 16px;border:1px solid var(--line);border-radius:14px;font-size:15px;margin:0 0 14px}.success{background:var(--green)}.footer{text-align:center;color:var(--muted);padding:30px}@media(max-width:760px){.hero{display:block;padding:28px}.nav{padding:10px 24px}.section{margin:18px 18px;padding:20px}.mini-row{grid-template-columns:1fr}.hero-risk{margin-top:18px}}\n");
            out.append("</style>\n");
        }

        private void appendScript(StringBuilder out) {
            out.append("<script>\n");
            out.append("function filterTable(inputId,tableId){var q=document.getElementById(inputId).value.toLowerCase();var rows=document.querySelectorAll('#'+tableId+' tbody tr');rows.forEach(function(r){r.style.display=r.innerText.toLowerCase().indexOf(q)>=0?'':'none';});}\n");
            out.append("</script>\n");
        }

        private void card(StringBuilder out, String title, Object value, String note) {
            out.append("<div class=\"card\"><span>").append(h(title)).append("</span><strong>").append(h(String.valueOf(value))).append("</strong><small>").append(h(note)).append("</small></div>\n");
        }

        private void metricBar(StringBuilder out, String label, int numerator, int denominator) {
            String pct = percentage(numerator, denominator);
            String width = denominator == 0 ? "0.0" : PERCENT.format((numerator * 100.0) / denominator);
            out.append("<div class=\"metric\"><div class=\"metric-head\"><strong>").append(h(label)).append("</strong><span>").append(numerator).append(" / ").append(denominator).append(" (").append(pct).append(")</span></div><div class=\"bar\"><i style=\"width:").append(width).append("%\"></i></div></div>\n");
        }

        private void smallMetric(StringBuilder out, String label, Object value) {
            out.append("<div class=\"small-metric\"><span>").append(h(label)).append("</span><strong>").append(h(String.valueOf(value))).append("</strong></div>\n");
        }

        private void featureList(StringBuilder out, List<EObject> features) {
            if (features.isEmpty()) {
                out.append("<p>None.</p>");
                return;
            }
            out.append("<ul class=\"compact-list\">\n");
            for (EObject feature : features) out.append("<li>").append(h(nameOf(feature))).append("</li>\n");
            out.append("</ul>\n");
        }

        private String riskClass(String level) {
            if ("CRITICAL".equals(level)) return "risk-critical";
            if ("HIGH".equals(level)) return "risk-high";
            if ("MEDIUM".equals(level)) return "risk-medium";
            return "risk-low";
        }

        private String h(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }

    private static class QueryEngine {

        String run(AnalysisResult r, String queryName, String queryArgument) {
            String q = normalize(queryName);
            String arg = queryArgument == null ? "" : queryArgument.trim();

            if (isBlank(q) || "help".equals(q) || "list-queries".equals(q)) {
                return listQueries();
            }

            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Query Result\n\n");
            out.append("**Migration:** ").append(md(r.migrationName)).append("  \n");
            out.append("**Query:** `").append(md(q)).append("`");
            if (!isBlank(arg)) out.append("  \n**Argument:** ").append(md(arg));
            out.append("\n\n");

            if ("summary".equals(q)) appendSummary(out, r);
            else if ("no-direct-mappings".equals(q)) appendCorrespondenceListDetailed(out, "NoDirectMapping transformations", r.noDirectMappings, r);
            else if ("operator-help".equals(q) || "operator-assisted".equals(q)) appendCorrespondenceListDetailed(out, "Operator-assisted transformations", r.operatorAssistedTransformations, r);
            else if ("manual-heavy-transformations".equals(q)) appendCorrespondenceListDetailed(out, "Manual-heavy transformations", manualHeavyTransformations(r), r);
            else if ("cleanup-bridges".equals(q)) appendCorrespondenceListDetailed(out, "Cleanup-required bridges", r.cleanupRequiredBridges, r);
            else if ("semantic-effects".equals(q)) appendCorrespondenceListDetailed(out, "Semantic effects", r.semanticEffects, r);
            else if ("risk-hotspots".equals(q)) appendRiskHotspots(out, r);
            else if ("conditions".equals(q)) appendConditions(out, r.correspondencesWithConditions);
            else if ("conditions-for".equals(q)) appendConditionsFor(out, r, arg);
            else if ("missing-strategies".equals(q)) appendCorrespondenceListDetailed(out, "Transformations without strategy", r.transformationsWithoutStrategy, r);
            else if ("unmentioned-source-features".equals(q)) appendFeatureList(out, "Unmentioned source features", r.unmentionedSourceFeatures);
            else if ("unmentioned-target-features".equals(q)) appendFeatureList(out, "Unmentioned target features", r.unmentionedTargetFeatures);
            else if ("explain-mapping".equals(q)) appendMappingExplanation(out, r, arg);
            else if ("explain-correspondence".equals(q)) appendCorrespondenceExplanation(out, r, arg);
            else if ("feature-usage".equals(q)) appendFeatureUsage(out, r, arg);
            else {
                out.append("Unknown query: `").append(md(q)).append("`.\n\n");
                out.append(listQueries());
            }

            return out.toString();
        }

        void runInteractive(AnalysisResult r) {
            Scanner scanner = new Scanner(System.in);
            System.out.println();
            System.out.println("SMDSL interactive query console.");
            System.out.println("Type list-queries for available queries. Type exit to quit.");
            while (true) {
                System.out.print("smdsl> ");
                String line = scanner.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) break;

                String query = line;
                String argument = "";
                int firstSpace = line.indexOf(' ');
                if (firstSpace > 0) {
                    query = line.substring(0, firstSpace).trim();
                    argument = line.substring(firstSpace + 1).trim();
                }

                String result = run(r, query, argument);
                System.out.println(result);
            }
        }

        private String listQueries() {
            StringBuilder out = new StringBuilder();
            out.append("# SMDSL Analyzer Query Help\n\n");
            out.append("Available queries:\n\n");
            out.append("| Query | Optional argument | Meaning |\n");
            out.append("|---|---|---|\n");
            out.append("| `summary` | - | Show a compact migration summary. |\n");
            out.append("| `no-direct-mappings` | - | List transformations with `NoDirectMapping`. |\n");
            out.append("| `operator-help` | - | List operator-assisted transformations. |\n");
            out.append("| `manual-heavy-transformations` | - | List transformations where manual steps exceed automated steps. |\n");
            out.append("| `cleanup-bridges` | - | List bridges requiring cleanup. |\n");
            out.append("| `semantic-effects` | - | List semantic effects. |\n");
            out.append("| `risk-hotspots` | - | Show mapping-level risk hotspots. |\n");
            out.append("| `conditions` | - | List correspondences that have applicability conditions. |\n");
            out.append("| `conditions-for` | correspondence name | Show applicability conditions for one correspondence. |\n");
            out.append("| `missing-strategies` | - | List transformations with no strategy. |\n");
            out.append("| `unmentioned-source-features` | - | List source features not mentioned anywhere. |\n");
            out.append("| `unmentioned-target-features` | - | List target features not mentioned anywhere. |\n");
            out.append("| `explain-mapping` | mapping name | Explain one mapping, including risk reasons and contained correspondences. |\n");
            out.append("| `explain-correspondence` | correspondence name | Explain one correspondence in detail. |\n");
            out.append("| `feature-usage` | feature name | Show where a feature is referenced. |\n\n");
            out.append("Examples:\n\n");
            out.append("```bash\n");
            out.append("java smdslanalyzer.SmdslAnalyzer --ecore model.ecore --model migration.model --query no-direct-mappings\n");
            out.append("java smdslanalyzer.SmdslAnalyzer --ecore model.ecore --model migration.model --query explain-mapping --name \"Runner Suite Category and Ordering Migration\"\n");
            out.append("java smdslanalyzer.SmdslAnalyzer --ecore model.ecore --model migration.model --query feature-usage --name \"@RunWith\"\n");
            out.append("java smdslanalyzer.SmdslAnalyzer --ecore model.ecore --model migration.model --interactive\n");
            out.append("```\n");
            return out.toString();
        }

        private void appendSummary(StringBuilder out, AnalysisResult r) {
            out.append("| Metric | Value |\n");
            out.append("|---|---:|\n");
            out.append("| Source features | ").append(r.sourceFeatures.size()).append(" |\n");
            out.append("| Target features | ").append(r.targetFeatures.size()).append(" |\n");
            out.append("| Mappings | ").append(r.mappings.size()).append(" |\n");
            out.append("| Correspondences | ").append(r.allCorrespondences.size()).append(" |\n");
            out.append("| Transformations | ").append(r.transformations.size()).append(" |\n");
            out.append("| Preservations | ").append(r.preservations.size()).append(" |\n");
            out.append("| Bridges | ").append(r.bridges.size()).append(" |\n");
            out.append("| Semantic effects | ").append(r.semanticEffects.size()).append(" |\n");
            out.append("| Manual step ratio | ").append(percentage(r.manualStepCount, r.strategyStepCount)).append(" |\n");
            out.append("| Overall risk | ").append(md(r.overallRiskLevel)).append(" (").append(r.totalRiskScore).append(") |\n\n");
        }

        private void appendRiskHotspots(StringBuilder out, AnalysisResult r) {
            List<MappingAnalysis> sorted = new ArrayList<MappingAnalysis>(r.mappingAnalyses);
            Collections.sort(sorted, new Comparator<MappingAnalysis>() {
                @Override
                public int compare(MappingAnalysis a, MappingAnalysis b) {
                    return b.riskScore - a.riskScore;
                }
            });

            out.append("## Mapping risk hotspots\n\n");
            out.append("| Mapping | Risk | Manual % | NoDirect | OperatorHelp | Missing strategies |\n");
            out.append("|---|---:|---:|---:|---:|---:|\n");
            for (MappingAnalysis ma : sorted) {
                out.append("| ").append(md(ma.name)).append(" | ")
                        .append(ma.riskScore).append(" (").append(md(ma.riskLevel)).append(") | ")
                        .append(PERCENT.format(ma.manualRatio())).append("% | ")
                        .append(ma.noDirectMappingCount).append(" | ")
                        .append(ma.operatorAssistedCount).append(" | ")
                        .append(ma.missingStrategyCount).append(" |\n");
            }
            out.append("\n");

            if (!sorted.isEmpty()) {
                MappingAnalysis top = sorted.get(0);
                out.append("## Top hotspot explanation\n\n");
                appendSingleMapping(out, top);
            }
        }

        private void appendMappingExplanation(StringBuilder out, AnalysisResult r, String name) {
            if (isBlank(name)) {
                out.append("Please provide a mapping name using `--name` or as the text after the command in interactive mode.\n\n");
                appendMappingNames(out, r);
                return;
            }

            MappingAnalysis ma = findMapping(r, name);
            if (ma == null) {
                out.append("No mapping matched: ").append(md(name)).append("\n\n");
                appendMappingNames(out, r);
                return;
            }

            appendSingleMapping(out, ma);
        }

        private void appendSingleMapping(StringBuilder out, MappingAnalysis ma) {
            out.append("## Mapping: ").append(md(ma.name)).append("\n\n");
            out.append("| Metric | Value |\n");
            out.append("|---|---:|\n");
            out.append("| Correspondences | ").append(ma.correspondences.size()).append(" |\n");
            out.append("| Transformations | ").append(ma.transformations.size()).append(" |\n");
            out.append("| Preservations | ").append(ma.preservations.size()).append(" |\n");
            out.append("| Bridges | ").append(ma.bridges.size()).append(" |\n");
            out.append("| Semantic effects | ").append(ma.semanticEffects.size()).append(" |\n");
            out.append("| NoDirectMappings | ").append(ma.noDirectMappingCount).append(" |\n");
            out.append("| OperatorHelp transformations | ").append(ma.operatorAssistedCount).append(" |\n");
            out.append("| Manual steps | ").append(ma.manualSteps).append(" |\n");
            out.append("| Automated steps | ").append(ma.automatedSteps).append(" |\n");
            out.append("| Manual step ratio | ").append(PERCENT.format(ma.manualRatio())).append("% |\n");
            out.append("| Risk | ").append(ma.riskScore).append(" (").append(md(ma.riskLevel)).append(") |\n\n");

            out.append("### Risk reasons\n\n");
            if (ma.riskReasons.isEmpty()) {
                out.append("No risk reasons found.\n\n");
            } else {
                for (Map.Entry<String, Integer> entry : groupStrings(ma.riskReasons).entrySet()) {
                    out.append("- ").append(md(entry.getKey()));
                    if (entry.getValue() > 1) out.append(" x ").append(entry.getValue());
                    out.append("\n");
                }
                out.append("\n");
            }

            out.append("### Correspondences\n\n");
            for (EObject c : ma.correspondences) {
                out.append("- **").append(md(typeOf(c))).append(":** ").append(md(nameOf(c))).append("\n");
            }
            out.append("\n");
        }

        private void appendCorrespondenceExplanation(StringBuilder out, AnalysisResult r, String name) {
            if (isBlank(name)) {
                out.append("Please provide a correspondence name using `--name` or as the text after the command in interactive mode.\n\n");
                return;
            }

            List<EObject> matches = findCorrespondences(r, name);
            if (matches.isEmpty()) {
                out.append("No correspondence matched: ").append(md(name)).append("\n\n");
                return;
            }

            for (EObject c : matches) {
                appendCorrespondenceDetails(out, c, r);
            }
        }

        private void appendCorrespondenceListDetailed(StringBuilder out, String title, List<EObject> correspondences, AnalysisResult r) {
            out.append("## ").append(md(title)).append("\n\n");
            if (correspondences.isEmpty()) {
                out.append("None.\n\n");
                return;
            }
            for (EObject c : correspondences) {
                appendCorrespondenceDetails(out, c, r);
            }
        }

        private void appendCorrespondenceDetails(StringBuilder out, EObject c, AnalysisResult r) {
            MappingAnalysis ma = findMappingFor(r, c);
            RiskAssessment risk = RiskScorer.scoreCorrespondence(c, r, r.riskConfig);

            out.append("### ").append(md(nameOf(c))).append("\n\n");
            out.append("| Field | Value |\n");
            out.append("|---|---|\n");
            out.append("| Type | ").append(md(typeOf(c))).append(" |\n");
            out.append("| Mapping | ").append(ma == null ? "" : md(ma.name)).append(" |\n");
            out.append("| Risk | ").append(risk.score).append(" |");
            out.append("\n");

            if ("Transformation".equals(typeOf(c))) {
                EObject source = asEObject(get(c, "sourceFeature"));
                EObject complexity = asEObject(get(c, "complexity"));
                out.append("| Source feature | ").append(md(nameOf(source))).append(" |\n");
                out.append("| Target feature(s) | ").append(md(joinNames(list(c, "targetFeature"), ", "))).append(" |\n");
                out.append("| Automation degree | ").append(md(typeOf(complexity))).append(" |\n");
                out.append("| Relation | ").append(md(string(get(c, "relation")))).append(" |\n");
                List<EObject> steps = r.stepsByTransformation.get(c);
                int manual = 0;
                int automated = 0;
                if (steps != null) {
                    for (EObject step : steps) {
                        String st = enumName(get(step, "type"));
                        if ("M".equals(st)) manual++;
                        if ("A".equals(st)) automated++;
                    }
                }
                out.append("| Manual steps | ").append(manual).append(" |\n");
                out.append("| Automated steps | ").append(automated).append(" |\n");
            } else if ("Preservation".equals(typeOf(c))) {
                out.append("| Kind | ").append(md(enumName(get(c, "kind")))).append(" |\n");
                out.append("| Preserved feature(s) | ").append(md(joinNames(list(c, "preservedFeatures"), ", "))).append(" |\n");
                out.append("| Reason | ").append(md(string(get(c, "reason")))).append(" |\n");
            } else if ("Bridge".equals(typeOf(c))) {
                out.append("| Scope | ").append(md(enumName(get(c, "scope")))).append(" |\n");
                out.append("| Bridge feature(s) | ").append(md(joinNames(list(c, "bridgeFeatures"), ", "))).append(" |\n");
                out.append("| Cleanup required | ").append(bool(get(c, "cleanupRequired"))).append(" |\n");
                out.append("| Rationale | ").append(md(string(get(c, "rationale")))).append(" |\n");
            } else if ("SemanticEffect".equals(typeOf(c))) {
                out.append("| Kind | ").append(md(enumName(get(c, "kind")))).append(" |\n");
                out.append("| Affected source feature(s) | ").append(md(joinNames(list(c, "affectedSourceFeatures"), ", "))).append(" |\n");
                out.append("| Affected target feature(s) | ").append(md(joinNames(list(c, "affectedTargetFeatures"), ", "))).append(" |\n");
                out.append("| Rationale | ").append(md(string(get(c, "rationale")))).append(" |\n");
            }

            if (!risk.reasons.isEmpty()) {
                out.append("\nRisk reasons:\n\n");
                for (String reason : risk.reasons) out.append("- ").append(md(reason)).append("\n");
            }

            appendConditionsForCorrespondence(out, c);

            if ("Transformation".equals(typeOf(c))) {
                appendStrategySteps(out, r.stepsByTransformation.get(c));
            }

            if (!list(c, "subcorrespondences").isEmpty()) {
                out.append("\nSubcorrespondences:\n\n");
                for (EObject child : list(c, "subcorrespondences")) {
                    out.append("- **").append(md(typeOf(child))).append(":** ").append(md(nameOf(child))).append("\n");
                }
            }
            out.append("\n");
        }

        private void appendStrategySteps(StringBuilder out, List<EObject> steps) {
            if (steps == null || steps.isEmpty()) return;
            out.append("\nStrategy steps:\n\n");
            for (EObject step : steps) {
                out.append("- [").append(md(enumName(get(step, "type")))).append("] ")
                        .append(integer(get(step, "order"))).append(". ")
                        .append(md(string(get(step, "description")))).append("\n");
            }
        }

        private void appendConditions(StringBuilder out, List<EObject> correspondences) {
            out.append("## Correspondences with applicability conditions\n\n");
            if (correspondences.isEmpty()) {
                out.append("None.\n\n");
                return;
            }
            for (EObject c : correspondences) {
                out.append("### ").append(md(nameOf(c))).append(" (`").append(md(typeOf(c))).append("`)\n\n");
                appendConditionsForCorrespondence(out, c);
            }
        }

        private void appendConditionsFor(StringBuilder out, AnalysisResult r, String name) {
            if (isBlank(name)) {
                out.append("Please provide a correspondence name.\n\n");
                return;
            }

            List<EObject> matches = findCorrespondences(r, name);
            if (matches.isEmpty()) {
                out.append("No correspondence matched: ").append(md(name)).append("\n\n");
                return;
            }

            for (EObject c : matches) {
                out.append("## Conditions for ").append(md(nameOf(c))).append("\n\n");
                appendConditionsForCorrespondence(out, c);
            }
        }

        private void appendConditionsForCorrespondence(StringBuilder out, EObject c) {
            List<EObject> conditions = list(c, "conditions");
            if (conditions.isEmpty()) return;

            out.append("\nApplicability conditions:\n\n");
            for (EObject condition : conditions) {
                out.append("- **").append(md(enumName(get(condition, "polarity")))).append("**");
                String subjects = joinNames(list(condition, "subjectFeatures"), ", ");
                if (!isBlank(subjects)) out.append(" on ").append(md(subjects));
                String expression = string(get(condition, "expression"));
                if (!isBlank(expression)) out.append(": ").append(md(expression));
                out.append("\n");
            }
        }

        private void appendFeatureUsage(StringBuilder out, AnalysisResult r, String featureName) {
            if (isBlank(featureName)) {
                out.append("Please provide a feature name using `--name` or as the text after the command in interactive mode.\n\n");
                return;
            }

            List<EObject> matches = findFeatures(r, featureName);
            if (matches.isEmpty()) {
                out.append("No feature matched: ").append(md(featureName)).append("\n\n");
                return;
            }

            for (EObject feature : matches) {
                out.append("## Feature usage: ").append(md(nameOf(feature))).append("\n\n");
                boolean isSource = r.sourceFeatures.contains(feature);
                boolean isTarget = r.targetFeatures.contains(feature);
                out.append("Platform side: ");
                if (isSource && isTarget) out.append("source and target");
                else if (isSource) out.append("source");
                else if (isTarget) out.append("target");
                else out.append("unknown");
                out.append("\n\n");

                List<String> usages = new ArrayList<String>();

                for (EObject c : r.transformations) {
                    if (feature.equals(asEObject(get(c, "sourceFeature")))) usages.add("Transformation source: " + nameOf(c));
                    if (list(c, "targetFeature").contains(feature)) usages.add("Transformation target: " + nameOf(c));
                }
                for (EObject c : r.preservations) {
                    if (list(c, "preservedFeatures").contains(feature)) usages.add("Preservation: " + nameOf(c));
                }
                for (EObject c : r.bridges) {
                    if (list(c, "bridgeFeatures").contains(feature)) usages.add("Bridge: " + nameOf(c));
                }
                for (EObject c : r.semanticEffects) {
                    if (list(c, "affectedSourceFeatures").contains(feature)) usages.add("SemanticEffect source: " + nameOf(c));
                    if (list(c, "affectedTargetFeatures").contains(feature)) usages.add("SemanticEffect target: " + nameOf(c));
                }
                for (EObject c : r.allCorrespondences) {
                    for (EObject condition : list(c, "conditions")) {
                        if (list(condition, "subjectFeatures").contains(feature)) usages.add("ApplicabilityCondition in " + nameOf(c));
                    }
                }
                for (EObject req : r.preMigrationRequirements) {
                    if (list(req, "affectedFeatures").contains(feature)) usages.add("PreMigrationRequirement: " + enumName(get(req, "kind")));
                }

                if (usages.isEmpty()) {
                    out.append("No usage found.\n\n");
                } else {
                    for (String usage : usages) {
                        out.append("- ").append(md(usage)).append("\n");
                    }
                    out.append("\n");
                }
            }
        }

        private void appendFeatureList(StringBuilder out, String title, List<EObject> features) {
            out.append("## ").append(md(title)).append("\n\n");
            if (features.isEmpty()) {
                out.append("None.\n\n");
                return;
            }
            for (EObject feature : features) {
                out.append("- ").append(md(nameOf(feature))).append("\n");
            }
            out.append("\n");
        }

        private List<EObject> manualHeavyTransformations(AnalysisResult r) {
            List<EObject> result = new ArrayList<EObject>();
            for (EObject t : r.transformations) {
                List<EObject> steps = r.stepsByTransformation.get(t);
                if (steps == null) continue;
                int manual = 0;
                int automated = 0;
                for (EObject step : steps) {
                    String st = enumName(get(step, "type"));
                    if ("M".equals(st)) manual++;
                    if ("A".equals(st)) automated++;
                }
                if (manual > automated && manual > 0) result.add(t);
            }
            return result;
        }

        private MappingAnalysis findMapping(AnalysisResult r, String name) {
            for (MappingAnalysis ma : r.mappingAnalyses) {
                if (matchesName(ma.name, name)) return ma;
            }
            return null;
        }

        private MappingAnalysis findMappingFor(AnalysisResult r, EObject c) {
            for (MappingAnalysis ma : r.mappingAnalyses) {
                if (ma.correspondences.contains(c)) return ma;
            }
            return null;
        }

        private List<EObject> findCorrespondences(AnalysisResult r, String name) {
            List<EObject> result = new ArrayList<EObject>();
            for (EObject c : r.allCorrespondences) {
                if (matchesName(nameOf(c), name)) result.add(c);
            }
            return result;
        }

        private List<EObject> findFeatures(AnalysisResult r, String name) {
            List<EObject> result = new ArrayList<EObject>();
            for (EObject f : r.sourceFeatures) {
                if (matchesName(nameOf(f), name)) result.add(f);
            }
            for (EObject f : r.targetFeatures) {
                if (matchesName(nameOf(f), name) && !result.contains(f)) result.add(f);
            }
            return result;
        }

        private void appendMappingNames(StringBuilder out, AnalysisResult r) {
            out.append("Available mappings:\n\n");
            for (MappingAnalysis ma : r.mappingAnalyses) {
                out.append("- ").append(md(ma.name)).append("\n");
            }
            out.append("\n");
        }

        private boolean matchesName(String candidate, String query) {
            if (candidate == null || query == null) return false;
            String c = candidate.toLowerCase();
            String q = query.toLowerCase();
            return c.equals(q) || c.contains(q);
        }

        private String normalize(String value) {
            if (value == null) return "";
            return value.trim().toLowerCase();
        }
    }

    private static void increment(Map<String, Integer> map, String key) {
        if (isBlank(key)) key = "<unspecified>";
        Integer current = map.get(key);
        map.put(key, current == null ? 1 : current + 1);
    }

    private static Object get(EObject object, String featureName) {
        if (object == null) return null;
        EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
        if (feature == null) return null;
        return object.eGet(feature);
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> list(EObject object, String featureName) {
        Object value = get(object, featureName);
        if (value == null) return new ArrayList<EObject>();
        if (value instanceof EList<?>) {
            List<EObject> result = new ArrayList<EObject>();
            for (Object item : (EList<Object>) value) {
                if (item instanceof EObject) result.add((EObject) item);
            }
            return result;
        }
        if (value instanceof EObject) {
            List<EObject> result = new ArrayList<EObject>();
            result.add((EObject) value);
            return result;
        }
        return new ArrayList<EObject>();
    }

    private static EObject asEObject(Object value) {
        if (value instanceof EObject) return (EObject) value;
        return null;
    }

    private static String nameOf(EObject object) {
        if (object == null) return "";
        String name = string(get(object, "name"));
        if (!isBlank(name)) return name;
        return typeOf(object);
    }

    private static String typeOf(EObject object) {
        if (object == null) return "";
        EClass eClass = object.eClass();
        return eClass == null ? "" : eClass.getName();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String enumName(Object value) {
        if (value == null) return "<unspecified>";
        return String.valueOf(value);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static List<EObject> sortByOrder(List<EObject> steps) {
        List<EObject> copy = new ArrayList<EObject>(steps);
        Collections.sort(copy, new Comparator<EObject>() {
            @Override
            public int compare(EObject a, EObject b) {
                return integer(get(a, "order")) - integer(get(b, "order"));
            }
        });
        return copy;
    }

    private static String joinNames(List<EObject> objects, String separator) {
        List<String> names = new ArrayList<String>();
        for (EObject object : objects) {
            names.add(nameOf(object));
        }
        return join(names, separator);
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static String percentage(int numerator, int denominator) {
        if (denominator == 0) return "0.0%";
        return PERCENT.format((numerator * 100.0) / denominator) + "%";
    }

    private static String countWithTotal(int numerator, int denominator) {
        return numerator + " / " + denominator + " (" + percentage(numerator, denominator) + ")";
    }

    private static int unionSize(Set<EObject> a, Set<EObject> b) {
        Set<EObject> union = new LinkedHashSet<EObject>();
        union.addAll(a);
        union.addAll(b);
        return union.size();
    }

    private static String warning(EObject c, String message) {
        return typeOf(c) + " '" + nameOf(c) + "': " + message;
    }

    private static String hint(EObject c, String message) {
        return typeOf(c) + " '" + nameOf(c) + "': " + message;
    }

    private static String md(String text) {
        if (text == null) return "";
        return text.replace("\n", " ").replace("|", "\\|");
    }

    private static String json(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String dot(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static Map<String, Integer> groupStrings(List<String> values) {
        Map<String, Integer> grouped = new LinkedHashMap<String, Integer>();
        for (String value : values) {
            increment(grouped, value);
        }
        return grouped;
    }

    private static void ensureParentDirectory(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
