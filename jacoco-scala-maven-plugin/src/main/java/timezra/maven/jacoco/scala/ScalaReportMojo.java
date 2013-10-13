package timezra.maven.jacoco.scala;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.internal.analysis.ClassAnalyzer;
import org.jacoco.core.internal.analysis.MethodAnalyzer;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.internal.flow.ClassProbesAdapter;
import org.jacoco.core.internal.flow.ClassProbesVisitor;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.instr.InstrSupport;
import org.jacoco.maven.FileFilter;
import org.jacoco.maven.ReportMojo;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class ScalaReportMojo extends ReportMojo {

    /**
     * A list of filters to exclude from the report. Currently only SCALAC.MIXIN is supported. When not specified nothing
     * will be excluded.
     * 
     * @parameter
     */
    private List<String> filters;

    @Override
    protected void executeReport(final Locale locale) throws MavenReportException {
        if (!filters.contains("SCALAC.MIXIN")) {
            super.executeReport(locale);
            ;
        } else {
            createScalaReport(locale);
        }
    }

    private void createScalaReport(final Locale locale) throws MavenReportException {
        execSuperclassMethod("loadExecutionData");
        try {
            final IReportVisitor visitor = execSuperclassMethod("createVisitor", locale);
            final SessionInfoStore sessionInfoStore = getFromSuperClass("sessionInfoStore");
            final ExecutionDataStore executionDataStore = getFromSuperClass("executionDataStore");
            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());
            createReport(visitor);
            visitor.visitEnd();
        } catch (final IOException e) {
            throw new MavenReportException("Error while creating report: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromSuperClass(final String fieldName) throws MavenReportException {
        try {
            final Field theField = ReportMojo.class.getDeclaredField(fieldName);
            theField.setAccessible(true);
            return (T) theField.get(this);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new MavenReportException("Unable to get the value " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T execSuperclassMethod(final String methodName, final Object... args) throws MavenReportException {
        try {
            final Method theMethod = ReportMojo.class.getDeclaredMethod(methodName, parameterTypes(args));
            theMethod.setAccessible(true);
            return (T) theMethod.invoke(this, args);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new MavenReportException("Unable to invoke method " + methodName, e);
        }
    }

    private Class<?>[] parameterTypes(final Object... args) {
        final Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    private void createReport(final IReportGroupVisitor visitor) throws IOException, MavenReportException {
        final FileFilter fileFilter = new FileFilter(getIncludes(), getExcludes());
        final BundleCreator creator = new SanitizingBundleCreator(getProject(), fileFilter);
        final ExecutionDataStore executionDataStore = getFromSuperClass("executionDataStore");
        final IBundleCoverage bundle = creator.createBundle(executionDataStore);

        final List<File> sourceRoots = execSuperclassMethod("getCompileSourceRoots");
        final String sourceEncoding = getFromSuperClass("sourceEncoding");

        final SourceFileCollection locator = new SourceFileCollection(sourceRoots, sourceEncoding);
        execSuperclassMethod("checkForMissingDebugInformation", bundle);
        visitor.visitBundle(bundle, locator);
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

    private static final class SanitizingBundleCreator extends BundleCreator {
        public SanitizingBundleCreator(final MavenProject project, final FileFilter fileFilter) {
            super(project, fileFilter);
        }

        @Override
        protected Analyzer createAnalyzer(final ExecutionDataStore executionDataStore, final ICoverageVisitor coverageVisitor) {
            return new SanitizingAnalyzer(executionDataStore, coverageVisitor);
        }
    }

    private static final class SanitizingAnalyzer extends Analyzer {

        private final ExecutionDataStore executionData;
        private final ICoverageVisitor coverageVisitor;

        public SanitizingAnalyzer(final ExecutionDataStore executionData, final ICoverageVisitor coverageVisitor) {
            super(executionData, coverageVisitor);
            this.executionData = executionData;
            this.coverageVisitor = coverageVisitor;
        }

        @Override
        public void analyzeClass(final ClassReader reader) {
            final ClassVisitor visitor = createSanitizingVisitor(CRC64.checksum(reader.b));
            reader.accept(visitor, 0);
        }

        private ClassVisitor createSanitizingVisitor(final long classid) {
            final ExecutionData data = executionData.get(classid);
            final boolean[] probes = data == null ? null : data.getProbes();
            final StringPool stringPool = new StringPool();
            final ClassProbesVisitor analyzer = new SanitizingClassAnalyzer(classid, probes, stringPool, coverageVisitor);
            return new ClassProbesAdapter(analyzer);
        }
    }

    private static final class SanitizingClassAnalyzer extends ClassAnalyzer {
        private final StringPool stringPool;
        private final boolean[] probes;
        private final Collection<IMethodCoverage> methodCoveragesToAdd = new ArrayList<IMethodCoverage>();
        private final ICoverageVisitor coverageVisitor;

        private SanitizingClassAnalyzer(final long classid, final boolean[] probes, final StringPool stringPool,
                final ICoverageVisitor coverageVisitor) {
            super(classid, probes, stringPool);
            this.stringPool = stringPool;
            this.probes = probes;
            this.coverageVisitor = coverageVisitor;
        }

        @Override
        public MethodProbesVisitor visitMethod(final int access, final String name, final String desc,
                final String signature, final String[] exceptions) {

            InstrSupport.assertNotInstrumented(name, getCoverage().getName());

            // TODO: Use filter hook
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                return null;
            }

            return new MethodAnalyzer(stringPool.get(name), stringPool.get(desc), stringPool.get(signature), probes) {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    final IMethodCoverage methodCoverage = getCoverage();
                    if (methodCoverage.getInstructionCounter().getTotalCount() > 0) {
                        // Only consider methods that actually contain
                        // code
                        methodCoveragesToAdd.add(methodCoverage);
                    }
                }
            };
        }

        @Override
        public void visitEnd() {
            try {
                visitSanitizedMethods();
            } finally {
                super.visitEnd();
                coverageVisitor.visitCoverage(getCoverage());
            }
        }

        private boolean isConstructor(final IMethodCoverage methodCoverage) {
            return "<init>".equals(methodCoverage.getName());
        }

        private void visitSanitizedMethods() {

            final Collection<Integer> constructorLines = new HashSet<Integer>();
            for (final IMethodCoverage methodCoverage : methodCoveragesToAdd) {
                if (isConstructor(methodCoverage)) {
                    constructorLines.add(Integer.valueOf(methodCoverage.getFirstLine()));
                }
            }
            for (final IMethodCoverage methodCoverage : methodCoveragesToAdd) {
                if (!constructorLines.contains(Integer.valueOf(methodCoverage.getFirstLine()))
                        || isConstructor(methodCoverage)) {
                    getCoverage().addMethod(methodCoverage);
                }
            }
        }
    }
}
