/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *    Kyle Lieber - implementation of CheckMojo
 *
 *******************************************************************************/
package timezra.maven.jacoco.scala;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.internal.analysis.ClassAnalyzer;
import org.jacoco.core.internal.analysis.ClassCoverageImpl;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.internal.flow.ClassProbesAdapter;
import org.jacoco.core.internal.flow.ClassProbesVisitor;
import org.jacoco.core.internal.instr.InstrSupport;
import org.jacoco.maven.FileFilter;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Creates a code coverage report for a single project in multiple formats (HTML, XML, and CSV).
 * 
 * @phase verify
 * @goal report
 * @requiresProject true
 * @threadSafe
 */
public class ReportMojo extends AbstractMavenReport {

    private static Log log;

    private static final String FILTER_SCALAC_MIXIN = "SCALAC.MIXIN";
    private static final String FILTER_SCALAC_CASE = "SCALAC.CASE";

    /**
     * Output directory for the reports. Note that this parameter is only relevant if the goal is run from the command line
     * or from the default build lifecycle. If the goal is run indirectly as part of a site generation, the output directory
     * configured in the Maven Site Plugin is used instead.
     * 
     * @parameter default-value="${project.reporting.outputDirectory}/jacoco"
     */
    private File outputDirectory;

    /**
     * Encoding of the generated reports.
     * 
     * @parameter expression="${project.reporting.outputEncoding}" default-value="UTF-8"
     */
    private String outputEncoding;

    /**
     * Encoding of the source files.
     * 
     * @parameter expression="${project.build.sourceEncoding}" default-value="UTF-8"
     */
    private String sourceEncoding;

    /**
     * File with execution data.
     * 
     * @parameter default-value="${project.build.directory}/jacoco.exec"
     */
    private File dataFile;

    /**
     * A list of class files to include in the report. May use wildcard characters (* and ?). When not specified everything
     * will be included.
     * 
     * @parameter
     */
    private List<String> includes;

    /**
     * A list of class files to exclude from the report. May use wildcard characters (* and ?). When not specified nothing
     * will be excluded.
     * 
     * @parameter
     */
    private List<String> excludes;

    /**
     * Flag used to suppress execution.
     * 
     * @parameter expression="${jacoco.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * A list of filters to exclude from the report. Currently only SCALAC.MIXIN is supported. When not specified nothing
     * will be excluded.
     * 
     * @parameter
     */
    private List<String> filters;

    /**
     * Maven project.
     * 
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * Doxia Site Renderer.
     * 
     * @component
     */
    private Renderer siteRenderer;

    private SessionInfoStore sessionInfoStore;

    private ExecutionDataStore executionDataStore;

    @Override
    public String getOutputName() {
        return "jacoco/index";
    }

    @Override
    public String getName(final Locale locale) {
        return "JaCoCo";
    }

    @Override
    public String getDescription(final Locale locale) {
        return "JaCoCo Test Coverage Report.";
    }

    @Override
    public boolean isExternalReport() {
        return true;
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    /**
     * Returns the list of class files to include in the report.
     * 
     * @return class files to include, may contain wildcard characters
     */
    protected List<String> getIncludes() {
        return includes;
    }

    /**
     * Returns the list of class files to exclude from the report.
     * 
     * @return class files to exclude, may contain wildcard characters
     */
    protected List<String> getExcludes() {
        return excludes;
    }

    public ReportMojo() { log = getLog(); }

    @Override
    public void setReportOutputDirectory(final File reportOutputDirectory) {
        if (reportOutputDirectory != null && !reportOutputDirectory.getAbsolutePath().endsWith("jacoco")) {
            outputDirectory = new File(reportOutputDirectory, "jacoco");
        } else {
            outputDirectory = reportOutputDirectory;
        }
    }

    @Override
    public boolean canGenerateReport() {
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Skipping JaCoCo for project with packaging type 'pom'");
            return false;
        }
        if (skip) {
            getLog().info("Skipping JaCoCo execution");
            return false;
        }
        if (!dataFile.exists()) {
            getLog().info("Skipping JaCoCo execution due to missing execution data file");
            return false;
        }
        return true;
    }

    /**
     * This method is called when the report generation is invoked directly as a standalone Mojo.
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (!canGenerateReport()) {
            return;
        }
        try {
            executeReport(Locale.getDefault());
        } catch (final MavenReportException e) {
            throw new MojoExecutionException("An error has occurred in " + getName(Locale.ENGLISH) + " report generation.",
                    e);
        }
    }

    @Override
    protected void executeReport(final Locale locale) throws MavenReportException {
        loadExecutionData();
        try {
            final IReportVisitor visitor = createVisitor(locale);
            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());
            createReport(visitor);
            visitor.visitEnd();
        } catch (final IOException e) {
            throw new MavenReportException("Error while creating report: " + e.getMessage(), e);
        }
    }

    private void loadExecutionData() throws MavenReportException {
        final ExecFileLoader loader = new ExecFileLoader();
        try {
            loader.load(dataFile);
        } catch (final IOException e) {
            throw new MavenReportException("Unable to read execution data file " + dataFile + ": " + e.getMessage(), e);
        }
        sessionInfoStore = loader.getSessionInfoStore();
        executionDataStore = loader.getExecutionDataStore();
    }

    private BundleCreator createBundleCreator() {
        log.debug("createBundleCreator: entered; filters=" + 
            (filters == null ? "null" : String.join(",", filters)));
        final FileFilter fileFilter = new FileFilter(getIncludes(), getExcludes());
        final Collection<MethodCoverageFilter> methodCoverageFilters = new ArrayList<>();
        if (filters != null) {
            if (filters.contains(FILTER_SCALAC_MIXIN)) {
                methodCoverageFilters.add(new MixinFilter());
            }
            if (filters.contains(FILTER_SCALAC_CASE)) {
                methodCoverageFilters.add(new CaseFilter());
            }
        }
        return new SanitizingBundleCreator(getProject(), fileFilter, new Filters(methodCoverageFilters));
    }

    private void createReport(final IReportGroupVisitor visitor) throws IOException {
        final BundleCreator creator = createBundleCreator();
        final IBundleCoverage bundle = creator.createBundle(executionDataStore);

        final SourceFileCollection locator = new SourceFileCollection(getCompileSourceRoots(), sourceEncoding);
        checkForMissingDebugInformation(bundle);
        visitor.visitBundle(bundle, locator);
    }

    private void checkForMissingDebugInformation(final ICoverageNode node) {
        if (node.getClassCounter().getTotalCount() > 0 && node.getLineCounter().getTotalCount() == 0) {
            getLog().warn("To enable source code annotation class files have to be compiled with debug information.");
        }
    }

    private IReportVisitor createVisitor(final Locale locale) throws IOException {
        final List<IReportVisitor> visitors = new ArrayList<IReportVisitor>();

        outputDirectory.mkdirs();

        final XMLFormatter xmlFormatter = new XMLFormatter();
        xmlFormatter.setOutputEncoding(outputEncoding);
        visitors.add(xmlFormatter.createVisitor(new FileOutputStream(new File(outputDirectory, "jacoco.xml"))));

        final CSVFormatter csvFormatter = new CSVFormatter();
        csvFormatter.setOutputEncoding(outputEncoding);
        visitors.add(csvFormatter.createVisitor(new FileOutputStream(new File(outputDirectory, "jacoco.csv"))));

        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        htmlFormatter.setOutputEncoding(outputEncoding);
        htmlFormatter.setLocale(locale);
        visitors.add(htmlFormatter.createVisitor(new FileMultiReportOutput(outputDirectory)));

        return new MultiReportVisitor(visitors);
    }

    private static class SourceFileCollection implements ISourceFileLocator {

        private final List<File> sourceRoots;
        private final String encoding;

        public SourceFileCollection(final List<File> sourceRoots, final String encoding) {
            this.sourceRoots = sourceRoots;
            this.encoding = encoding;
        }

        @Override
        public Reader getSourceFile(final String packageName, final String fileName) throws IOException {
            final String r;
            if (packageName.length() > 0) {
                r = packageName + '/' + fileName;
            } else {
                r = fileName;
            }
            for (final File sourceRoot : sourceRoots) {
                final File file = new File(sourceRoot, r);
                if (file.exists() && file.isFile()) {
                    return new InputStreamReader(new FileInputStream(file), encoding);
                }
            }
            return null;
        }

        @Override
        public int getTabWidth() {
            return 4;
        }
    }

    private File resolvePath(final String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(getProject().getBasedir(), path);
        }
        return file;
    }

    private List<File> getCompileSourceRoots() {
        final List<File> result = new ArrayList<File>();
        for (final Object path : getProject().getCompileSourceRoots()) {
            result.add(resolvePath((String) path));
        }
        return result;
    }

    private static final class SanitizingBundleCreator extends BundleCreator {
        private final MethodCoverageFilter filter;

        public SanitizingBundleCreator(final MavenProject project, final FileFilter fileFilter,
                final MethodCoverageFilter filter) {
            super(project, fileFilter);
            this.filter = filter;
        }

        @Override
        protected Analyzer createAnalyzer(final ExecutionDataStore executionDataStore, final ICoverageVisitor coverageVisitor) {
            return new SanitizingAnalyzer(executionDataStore, coverageVisitor, filter);
        }
    }

    private static final class SanitizingAnalyzer extends Analyzer {

        private final ExecutionDataStore executionData;
        private final ICoverageVisitor coverageVisitor;
        private final MethodCoverageFilter filter;

        public SanitizingAnalyzer(final ExecutionDataStore executionData, final ICoverageVisitor coverageVisitor,
                final MethodCoverageFilter filter) {
            super(executionData, coverageVisitor);
            this.executionData = executionData;
            this.coverageVisitor = coverageVisitor;
            this.filter = filter;
        }

        @Override
        public void analyzeClass(byte[] source, String location) {
            final long classId = CRC64.classId(source);
            final ClassReader reader = InstrSupport.classReaderFor(source);
            if ((reader.getAccess() & Opcodes.ACC_MODULE) != 0) {
                return;
            }
            if ((reader.getAccess() & Opcodes.ACC_SYNTHETIC) != 0) {
                return;
            }
            final ClassVisitor visitor = createSanitizingVisitor(classId, reader.getClassName());
            reader.accept(visitor, 0);
        }

        private ClassVisitor createSanitizingVisitor(final long classid, final String className) {
            final ExecutionData data = executionData.get(classid);
            final boolean[] probes;
            final boolean noMatch;
            if (data == null) {
                probes = null;
                noMatch = executionData.contains(className);
            } else {
                probes = data.getProbes();
                noMatch = false;
            }
            final ScalaFilteredClassCoverageImpl coverage = new ScalaFilteredClassCoverageImpl(className, classid, noMatch);
            final StringPool stringPool = new StringPool();
            final ClassProbesVisitor analyzer = new SanitizingClassAnalyzer(coverage, probes, stringPool, coverageVisitor);
            // final ClassProbesVisitor analyzer = new ClassAnalyzer(coverage, probes, stringPool);
            return new ClassProbesAdapter(analyzer, false);
        }
    }

    static public String imethcovstr(IMethodCoverage cov) {
        return String.format("%s:%d-%d", cov.getName(), cov.getFirstLine(), cov.getLastLine());
    }

    static public String classcovstr(ClassCoverageImpl cov) {
        return String.format("%s:%s:%d-%d", cov.getSourceFileName(), cov.getName(),
                cov.getFirstLine(), cov.getLastLine());
    }

    private static interface MethodCoverageFilter {
        Collection<IMethodCoverage> filter(final Collection<IMethodCoverage> methodCoverages);
    }

    private static final class Filters implements MethodCoverageFilter {

        private final Iterable<MethodCoverageFilter> filters;

        Filters(final Iterable<MethodCoverageFilter> filters) {
            this.filters = filters;
        }

        @Override
        public Collection<IMethodCoverage> filter(final Collection<IMethodCoverage> methodCoverages) {
            Collection<IMethodCoverage> filtered = methodCoverages;
            for (final MethodCoverageFilter filter : filters) {
                filtered = filter.filter(filtered);
            }
            return filtered;
        }
    }

    private static final class MixinFilter implements MethodCoverageFilter {
        @Override
        public Collection<IMethodCoverage> filter(final Collection<IMethodCoverage> methodCoverages) {
            final Collection<IMethodCoverage> filtered = new ArrayList<>();
            final Collection<Integer> constructorLines = new HashSet<Integer>();
            for (final IMethodCoverage methodCoverage : methodCoverages) {
                if (isConstructor(methodCoverage)) {
                    constructorLines.add(Integer.valueOf(methodCoverage.getFirstLine()));
                }
            }
            for (final IMethodCoverage methodCoverage : methodCoverages) {
                log.debug("MixinFilter.filter: processing " + imethcovstr(methodCoverage));
                if (isConstructor(methodCoverage)
                        || !constructorLines.contains(Integer.valueOf(methodCoverage.getFirstLine()))) {
                    log.debug("MixinFilter.filter: returning " + imethcovstr(methodCoverage));
                    filtered.add(methodCoverage);
                }
            }
            return filtered;
        }

        private boolean isConstructor(final IMethodCoverage methodCoverage) {
            return "<init>".equals(methodCoverage.getName());
        }
    }

    private static final class CaseFilter implements MethodCoverageFilter {
        @Override
        public Collection<IMethodCoverage> filter(final Collection<IMethodCoverage> methodCoverages) {
            final Collection<IMethodCoverage> filtered = new ArrayList<>();
            for (final IMethodCoverage methodCoverage : methodCoverages) {
                log.debug("CaseFilter.filter: processing " + imethcovstr(methodCoverage));
                final String methodSignature = methodCoverage.getName() + methodCoverage.getDesc();
                if (!(methodSignature.startsWith("curried()") || methodSignature.startsWith("tupled()") || methodSignature
                        .matches("(?:\\w|\\$)+\\$default\\$\\d+\\(\\).*"))) {
                    log.debug("CaseFilter.filter: returning " + imethcovstr(methodCoverage));
                    filtered.add(methodCoverage);
                }
            }
            return filtered;
        }
    }

    static final Set<String> scalaSyntheticMethods = new HashSet<>(Arrays.asList(
        "apply",
        "canEqual",
        "copy",
        "equals",
        "hashCode",
        "productArity",
        "productPrefix",
        "productElement",
        "productIterator",
        "productElementName",
        "productElementNames",
        "toString",
        "unapply",
        "writeReplace"));

    private static class ScalaFilteredClassCoverageImpl extends ClassCoverageImpl {
        private int tempFirstLine = -1;
        private final List<IMethodCoverage> methodsSeen = new ArrayList<IMethodCoverage>();

        public ScalaFilteredClassCoverageImpl(String arg1, long arg2, boolean arg3) { super(arg1, arg2, arg3); }

        @Override
        public void addMethod(final IMethodCoverage method) {
            log.debug(String.format("SFCCI.addMethod: %s %s", classcovstr(this), imethcovstr(method)));
            if ((method.getFirstLine() > 0) && (method.getInstructionCounter().getTotalCount() > 0) &&
                ((tempFirstLine < 0) || (method.getFirstLine() < tempFirstLine))) {
                tempFirstLine = method.getFirstLine();
            }
            methodsSeen.add(method);
        }

        public void addFilteredMethodsForReal() {
            for (IMethodCoverage method : methodsSeen) {
                if ((method.getInstructionCounter().getTotalCount() <= 0) ||
                    (isScalaSource() && scalaSyntheticMethods.contains(method.getName()) &&
                     method.getFirstLine() <= tempFirstLine &&
                     method.getLastLine() <= tempFirstLine)) {
                    continue;
                }
                log.debug(String.format("SFCCI.addFilteredMethodsForReal: %s %s", classcovstr(this), imethcovstr(method)));
                super.addMethod(method);
            }
        }

        private boolean isScalaSource() {
            return getSourceFileName().endsWith(".scala");
        }
    }

    private static final class SanitizingClassAnalyzer extends ClassAnalyzer {
        private final ScalaFilteredClassCoverageImpl coverageToVisit;
        private final ICoverageVisitor coverageVisitor;

        private SanitizingClassAnalyzer(final ScalaFilteredClassCoverageImpl coverage, final boolean[] probes,
            final StringPool stringPool, final ICoverageVisitor coverageVisitor) {
            super(coverage, probes, stringPool);
            this.coverageToVisit = coverage;
            this.coverageVisitor = coverageVisitor;
        }

        @Override
        public void visitEnd() {
            coverageToVisit.addFilteredMethodsForReal();
            super.visitEnd();
            coverageVisitor.visitCoverage(coverageToVisit);
        }
    }
}
