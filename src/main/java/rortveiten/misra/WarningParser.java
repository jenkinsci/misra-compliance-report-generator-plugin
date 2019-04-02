package rortveiten.misra;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import jenkins.model.Jenkins;
import rortveiten.misra.Guideline.Category;
import rortveiten.misra.Guideline.ComplianceStatus;

/**
 * Abstract class with functions to parse warnings from a specific MISRA checker
 * tool. Extend this class and implement the abstract methods in order to
 * implement a parser for a new tool. See PCLintWarningParser for an example.
 * 
 * 
 * @author Oyvind Rortveit
 *
 */
public abstract class WarningParser implements ExtensionPoint {

    protected static class Violation {
        String guidelineId;
        String fileName = "";
        @SuppressFBWarnings("URF_UNREAD_FIELD") // For future use
        int lineNumber = 0;
    }

    protected static class Suppression {
        String guidelineId;
        boolean isFalsePositive;
        boolean isDeviation;
        String deviationReference;
        String deviationLink;
    }

    protected static class CommentProperties {
        int lineNumber;
        String fileName;
        boolean isNonMisra;
        Map<String, Suppression> suppressions;
    }

    public enum MisraVersion {
        C_2012, CPP_2008, C_1998, C_2004;

        @Override
        public String toString() {
            return "MISRA " + super.toString().replaceFirst("_", " ").replaceFirst("PP", "++");
        }

        public static MisraVersion fromString(String versionString) {
            if (versionString.contains("1998"))
                return C_1998;
            if (versionString.contains("CPP") || versionString.contains("C++"))
                return CPP_2008;
            if (versionString.contains("2004"))
                return C_2004;
            return C_2012;
        }
    }

    /**
     * Parse a line from the output of the MISRA checker tool (e.g. PC-lint).
     * 
     * @param line A single line of text from the parser tools output. The parser
     *             should be configured so that a violation does not cover more than
     *             one line.
     * @return A list of MISRA-violations described by this line. If the line does
     *         not represent any MISRA-violations, return null or an empty list.
     */
    protected abstract List<Violation> parseWarningLine(String line);

    /**
     * Parse the contents of a source code file, and return a list of all
     * suppression comments in the file. A suppression comment is a comment that is
     * used to suppress a warning from the checker tool. For example, for PC-lint
     * this is every comment that starts out with "lint" immediately following the
     * comment opening symbol. The returned string should contain the full text of
     * the comment, including any suppression tokens.
     * 
     * @param fileContent The full contents of the source file as a string
     * @return A list of all suppression comments in the file
     */
    protected abstract List<String> findSuppressionComments(String fileContent);

    /**
     * For a single suppression comment, find each MISRA violation that is
     * suppressed by this comment.
     * <p>
     * Note: This is likely the most difficult method to implement, since it may
     * have to map tool error codes to MISRA guidelines. If this mapping is too
     * complex, you can implement this method to always return null. This will force
     * the user to always add a GUIDELINE(...) tag to suppression comments, or end
     * up with an invalid report.
     * </p>
     * 
     * @param comment A suppression comment from the source file
     * @return A set of guidelines suppressed by this comment, e.g "Rule 1.2".
     *         Return null or an empty set if no MISRA guidelines are suppressed, or
     *         we could not determine which guideline is suppressed by this comment.
     */
    protected abstract Set<String> getGuidelineIdsFromComment(String comment);

    /**
     * @return The name of this warning parser
     */
    public abstract String name();

    /**
     * Returns the set of all MISRA-guideline versions supported by this warning
     * parser. Override if your warning parser does not support all guideline
     * versions.
     * 
     * @return A set of all versions of the MISRA guidelines that are supported by
     *         this warning parser.
     */
    public Set<MisraVersion> supportedMisraVersions() {
        Set<MisraVersion> versions = new HashSet<MisraVersion>();
        for (MisraVersion version : MisraVersion.values())
            versions.add(version);
        return versions;
    }

    public static final int ERR_GUIDELINE_NOT_FOUND = 1;
    public static final int ERR_COULD_NOT_DETERMINE_SUPPRESSED_GUIDELINE = 2;
    public static final int ERR_GRP_ERROR = 4;
    public static final int ERR_ILLEGAL_DEVIATION = 8;
    public static final int ERR_READ_FILE = 16;
    public static final int ERR_WRITE_FILE = 32;

    private Pattern falsePositivePattern = Pattern.compile("\\bFALSE.?POSITIVE(?:\\(([^\\)]*)\\))?");
    private Pattern deviationPattern = Pattern.compile(
            "\\bDEVIATION\\(\\s*([^,\\(\\)]+?)\\s*(?:\\)|(?:,\\s*([^\\(\\)]*?)\\s*(?:\\)|(?:,\\s*([^\\(\\)]*?)\\s*\\)))))");
    private Pattern nonMisraPattern = Pattern.compile("\\bNON.?MISRA");
    private Pattern guidelinePattern = Pattern.compile("\\bGUIDELINE\\(([^\\)]*)\\)");
    private Map<String, Guideline> guidelines;
    private List<Guideline> guidelineList;
    private MisraVersion misraVersion;
    private String currentFile;
    private String logFilePath;
    private FilePath workspace;
    protected int errorCode = 0;
    protected PrintStream out = System.out;

    public WarningParser() {
        workspace = new FilePath(new File(""));
        initialize(MisraVersion.C_2012);
    }

    /**
     * Resets this parser and sets a specific MISRA version. Override this method to
     * set the MISRA version and reset your WarningParser implementation. To set the
     * misra version from outside this class' descendants, use initialize() instead.
     * 
     * @param misraVersion Version of MISRA rules to use.
     */
    protected void setMisraVersion(MisraVersion misraVersion) {
        // Nothing to do here - can be overridden to something meaningful by sub classes
    }

    /**
     * Initializes/resets this warning parser and sets the MISRA version. This
     * method will call setMisraVersion, which can be overridden by subclasses.
     * 
     * @param misraVersion Version of the MISRA rules to use
     */
    public final void initialize(MisraVersion misraVersion) {
        this.misraVersion = misraVersion;
        readGuidelines();
        errorCode = 0;
        setMisraVersion(misraVersion);
    }

    public final MisraVersion getMisraVersion() {
        return misraVersion;
    }

    private void readGuidelines() {
        String regex = "/\\*+ ((?:Rule|Dir) (?:\\d+-\\d+-\\d+|\\d+\\.\\d+|\\d+)) +\\(([Rr]eq|[Aa]dv|[Mm]and|doc)\\) +\\*";
        String misraLintFileText = getMisraLintFileContent(misraVersion);
        Matcher matcher = Pattern.compile(regex).matcher(misraLintFileText);
        guidelines = new HashMap<String, Guideline>(305);// > 228/0.75, since 228 is the maximum number of guidelines
        guidelineList = new ArrayList<Guideline>();
        while (matcher.find()) {
            String guidelineId = matcher.group(1);
            guidelineId = guidelineId.replaceFirst("Dir ", "Directive ");
            String category = matcher.group(2);
            Guideline guideline = new Guideline(guidelineId, category);
            guidelines.put(guidelineId, guideline);
            guidelineList.add(guideline);
        }
    }

    public final List<Guideline> getGuidelines() {
        return guidelineList;
    }

    /**
     * Method only meant for injection from tests to reduce test-refactoring effort
     * 
     * @param guidelines List of guideliness
     */
    protected void setGuidelines(List<Guideline> guidelines) {
        guidelineList = guidelines;
        this.guidelines = new HashMap<String, Guideline>();
        for (Guideline guideline : guidelines) {
            this.guidelines.put(guideline.getId(), guideline);
        }
    }

    /**
     * These files are provided by Gimpel software as settings for PC-lint. They can
     * be very useful, since they contain lists of all guidelines.
     * 
     * @param misraVersion Version of MISRA guidelines
     * @return The whole file content
     */
    protected final static String getMisraLintFileContent(MisraVersion misraVersion) {
        String filename;
        switch (misraVersion) {
        case C_1998:
            filename = "au-misra1.lnt";
            break;
        case C_2004:
            filename = "au-misra2.lnt";
            break;
        case CPP_2008:
            filename = "au-misra-cpp.lnt";
            break;
        case C_2012:
        default:
            filename = "au-misra3.lnt";
            break;
        }

        InputStream stream = WarningParser.class.getResourceAsStream(filename);
        Scanner s = new Scanner(stream, "ISO-8859-1");
        String ret = s.useDelimiter("\\Z").next();
        s.close();
        return ret;
    }

    /**
     * Gets the errors produced by this warningparser
     * 
     * @return A bitfield ORed together of all the errors that have occurred (see
     *         the errorcodes starting with ERR_ )
     */
    public final int getErrorCode() {
        return errorCode;
    }

    public final String getFalsePositivePattern() {
        return falsePositivePattern.pattern();
    }

    public final void setFalsePositivePattern(String regex) {
        falsePositivePattern = Pattern.compile(regex);
    }

    public final String getDeviationPattern() {
        return deviationPattern.pattern();
    }

    public final void setDeviationPattern(String regex) {
        deviationPattern = Pattern.compile(regex);
    }

    public final String getNonMisraPattern() {
        return nonMisraPattern.pattern();
    }

    public final void setNonMisraPattern(String regex) {
        nonMisraPattern = Pattern.compile(regex);
    }

    public final String getGuidelinePattern() {
        return guidelinePattern.pattern();
    }

    public final void setGuidelinePattern(String regex) {
        guidelinePattern = Pattern.compile(regex);
    }

    public final void setLogger(PrintStream logger) {
        out = logger;
    }

    protected List<CommentProperties> parseSourceFile(String fileName) {
        List<CommentProperties> commentProperties = new ArrayList<CommentProperties>();
        String fileContent;
        try {
            fileContent = workspace.child(fileName).readToString();
        } catch (IOException | InterruptedException e) {
            return null;
        }
        List<String> comments = findSuppressionComments(fileContent);
        LineNumberFinder lineNumberFinder = new LineNumberFinder(fileContent);
        for (String comment : comments) {
            CommentProperties suppressionComment = parseComment(comment);
            suppressionComment.fileName = fileName;
            suppressionComment.lineNumber = lineNumberFinder.findNext(comment);
            if ((suppressionComment.suppressions == null || suppressionComment.suppressions.size() == 0)
                    && !suppressionComment.isNonMisra) {
                handleCouldNotDetermineWhichGuidelineIsSuppressed(comment);
            } else {
                commentProperties.add(suppressionComment);
            }
        }
        return commentProperties;
    }

    private CommentProperties parseComment(String commentString) {

        Set<String> guidelineIds = getIdsFromGuidelineTags(commentString);
        if (guidelineIds.isEmpty())
            guidelineIds = getGuidelineIdsFromComment(commentString);
        if (guidelineIds == null)
            guidelineIds = new HashSet<String>(0);
        CommentProperties ret = new CommentProperties();
        ret.suppressions = new HashMap<String, Suppression>(guidelineIds.size());
        ret.isNonMisra = nonMisraPattern.matcher(commentString).find();
        for (String guidelineId : guidelineIds) {
            Suppression s = new Suppression();
            s.guidelineId = guidelineId;
            ret.suppressions.put(guidelineId, s);
        }
        markFalsePositives(ret, commentString);
        markDeviations(ret, commentString);
        return ret;
    }

    private void markFalsePositives(CommentProperties commentProperties, String commentString) {
        Matcher falsePositiveMatcher = falsePositivePattern.matcher(commentString);
        while (falsePositiveMatcher.find()) {
            if (falsePositiveMatcher.group(1) == null) { // No match for the paranthesized part, global false positive
                                                         // for this comment
                for (Suppression s : commentProperties.suppressions.values())
                    s.isFalsePositive = true;
                break;
            } else {
                String guidelineId = falsePositiveMatcher.group(1);
                Suppression s = commentProperties.suppressions.get(guidelineId);
                if (s != null)
                    s.isFalsePositive = true;
                else
                    handleFalsePositiveNotFound(guidelineId, commentString);
            }
        }
    }

    private void markDeviations(CommentProperties commentProperties, String commentString) {
        Matcher deviationMatcher = deviationPattern.matcher(commentString);
        while (deviationMatcher.find()) {
            if (deviationMatcher.group(3) == null) { // No match for a third parameter. Global deviation for this
                                                     // comment
                for (Suppression s : commentProperties.suppressions.values()) {
                    s.isDeviation = true;
                    s.deviationReference = deviationMatcher.group(1); // May be null, that's ok
                    s.deviationLink = addProperProtocolMarker(deviationMatcher.group(2)); // May be null, that's ok
                }
                break;
            } else {
                String guidelineId = deviationMatcher.group(3);
                Suppression s = commentProperties.suppressions.get(guidelineId);
                if (s != null) {
                    s.isDeviation = true;
                    s.deviationReference = deviationMatcher.group(1);
                    s.deviationLink = addProperProtocolMarker(deviationMatcher.group(2));
                } else
                    handleDeviationNotFound(guidelineId, commentString);
            }
        }
    }

    /**
     * Since, according to MISRA, C-comments must not contain the text "//",
     * deviation tags with links need to omit the "://" part of the url and replace
     * it by either ":/" or ":". This function reinserts the double slash so links
     * work properly.
     */
    private String addProperProtocolMarker(String url) {
        if (url == null)
            return null;
        return url.replaceFirst(":(?!/)|:/(?!/)", "://");
    }

    private Set<String> getIdsFromGuidelineTags(String commentString) {
        Matcher m = guidelinePattern.matcher(commentString);
        Set<String> guidelineIds = new HashSet<String>();
        while (m.find()) {
            guidelineIds.add(m.group(1));
        }
        return guidelineIds;
    }

    public final void parseWarnings(List<String> lines) {
        for (String line : lines) {
            List<Violation> vs = parseWarningLine(line);
            for (Violation v : vs) {
                if (v != null && v.guidelineId != null && !v.guidelineId.isEmpty()) {
                    Guideline r = guidelines.get(v.guidelineId);
                    if (r == null)
                        handleGuidelineFromWarningNotFound(v);
                    else if (!isDisapplied(r))
                        r.setStatus(ComplianceStatus.VIOLATIONS);
                }
            }
        }
    }

    public final void parseSourceFiles(List<String> filesToParse) {
        List<CommentProperties> allComments = new ArrayList<CommentProperties>();
        for (String filename : filesToParse) {
            currentFile = filename;
            List<CommentProperties> comments = parseSourceFile(filename);
            if (comments != null) {
                modifyGuidelinesBasedOnSuppressionComments(comments);
                allComments.addAll(comments);
            } else {
                handleUnableToOpenSourceFile(filename);
            }
        }
        generateSuppressionReport(allComments);
    }

    private void generateSuppressionReport(List<CommentProperties> comments) {
        if (logFilePath != null && !logFilePath.isEmpty()) {
            try (Writer writer = new OutputStreamWriter(workspace.child(logFilePath).write(),
                    StandardCharsets.ISO_8859_1)) {
                for (CommentProperties props : comments) {
                    if (props.isNonMisra) {
                        logNonMisraSuppression(props, writer);
                    } else {
                        for (Suppression suppression : props.suppressions.values()) {
                            Category cat = guidelines.get(suppression.guidelineId).activeCategory();
                            String guidelineString = suppression.guidelineId + " (" + cat + ")";
                            if (suppression.isFalsePositive) {
                                writer.write(props.fileName + ":" + props.lineNumber + ": info: Suppression of "
                                        + guidelineString + " tagged as false positive\n");
                            } else if (suppression.isDeviation) {
                                writer.write(props.fileName + ":" + props.lineNumber + ": info: Deviation of "
                                        + guidelineString + "\n");
                            } else if (cat == null || cat != Category.DISAPPLIED) {
                                String infoString = cat == Category.ADVISORY ? "info" : "error";
                                writer.write(props.fileName + ":" + props.lineNumber + ": " + infoString
                                        + ": Violation of " + guidelineString + "\n");
                            }
                        }
                    }
                }
            } catch (IOException | InterruptedException ex) {
                handleCouldNotWriteLogFile();
            }
            log("Wrote logfile \"" + logFilePath + "\"");
        }
    }

    private void logNonMisraSuppression(CommentProperties props, Writer writer) throws IOException {
        String warningLevel = props.suppressions.size() > 0 ? "warning" : "info";
        writer.write(props.fileName + ":" + props.lineNumber + ": " + warningLevel
                + ": Tool suppression comment tagged as not MISRA relevant");
        if (props.suppressions.size() > 0) {
            writer.write(", but " + name() + " indicates that this comment suppresses ");
            int remaining = props.suppressions.size();
            for (Suppression suppression : props.suppressions.values()) {
                writer.write(suppression.guidelineId);
                remaining--;
                if (remaining > 1)
                    writer.write(", ");
                else if (remaining == 1)
                    writer.write(" and ");
            }
        }
        writer.write("\n");
    }

    private void modifyGuidelinesBasedOnSuppressionComments(List<CommentProperties> comments) {
        for (CommentProperties commentProperties : comments) {
            if (!commentProperties.isNonMisra)// Ignore non-misra suppression comments
                for (Suppression suppression : commentProperties.suppressions.values())
                    modifyGuidelineBasedOnComment(suppression);
        }
    }

    private void modifyGuidelineBasedOnComment(Suppression comment) {
        Guideline guideline = guidelines.get(comment.guidelineId);
        if (guideline != null)
            setComplianceStatusFromComment(comment, guideline);
        else
            handleSuppressedGuidelineNotFound(comment.guidelineId);
    }

    private void setComplianceStatusFromComment(Suppression comment, Guideline guideline) {
        if (!comment.isFalsePositive) {
            if (!isDisapplied(guideline)) {
                if (comment.isDeviation) {
                    if (guideline.getStatus() != ComplianceStatus.VIOLATIONS) {
                        guideline.setStatus(ComplianceStatus.DEVIATIONS);
                        guideline.addDeviationReference(comment.deviationReference, comment.deviationLink);
                    }
                    if (guideline.activeCategory() == Category.MANDATORY) {
                        handleAttemptToDeviateFromMandatoryGuideline(guideline);
                    }
                } else
                    guideline.setStatus(ComplianceStatus.VIOLATIONS);
            } else
                guideline.setStatus(ComplianceStatus.DISAPPLIED);
        }
    }

    private boolean isDisapplied(Guideline r) {
        Category requiredCompliance = r.getReCategorization();
        if (requiredCompliance != null)
            if (requiredCompliance == Category.DISAPPLIED)
                return true;
        return false;
    }

    /**
     * Reads in a guideline categorization plan (GRP) from a list of strings, each
     * containing a guideline to be recategorized and its new category separated by
     * a comma, e.g "Rule 1.1, mandatory" or "Directive 2.1, disapplied"
     *
     * @param grpLines List of lines from the GRP file
     */
    public final void readGrp(List<String> grpLines) {
        for (String line : grpLines) {
            String[] symbols = line.split(",");
            if (symbols.length == 0 || symbols[0].trim().length() == 0)
                continue;
            String guidelineId = symbols[0].trim();
            Guideline guideline = guidelines.get(guidelineId);
            if (guideline == null) {
                handleGuidelineInGrpNotFound(guidelineId);
                continue;
            }
            if (symbols.length < 2 || symbols[1].trim().length() == 0) {
                handleMisformedGrpLine(line);
                continue;
            }
            String newCategoryString = symbols[1].trim();
            Category newCategory = Category.fromString(newCategoryString);
            if (newCategory == Category.UNKNOWN) {
                handleUnknownComplianceCategory(newCategoryString);
                continue;
            }
            if (!isRecategorizationLegal(guideline.getCategory(), newCategory)) {
                handleIllegalRecategorization(guideline, newCategory);
                continue;
            }
            guideline.setReCategorization(newCategory);
        }
    }

    private static boolean isRecategorizationLegal(Category oldCategory, Category newCategory) {
        if (oldCategory == Category.MANDATORY)
            if (newCategory != Category.MANDATORY)
                return false;
        if (oldCategory == Category.REQUIRED)
            if (newCategory == Category.ADVISORY || newCategory == Category.DISAPPLIED)
                return false;
        return true;
    }

    private void handleIllegalRecategorization(Guideline guideline, Category category) {
        errorCode |= ERR_GRP_ERROR;
        log("Illegal recategorization of " + guideline.getId() + ": Cannot recategorize " + guideline.getCategory()
                + " guideline to " + category);
    }

    private void handleUnknownComplianceCategory(String newCategoryString) {
        errorCode |= ERR_GRP_ERROR;
        log("\"" + newCategoryString
                + "\" in the guideline recategorization plan (GRP) was not recognized as a valid MISRA compliance category");
    }

    private void handleMisformedGrpLine(String line) {
        errorCode |= ERR_GRP_ERROR;
        log("The line \"" + line
                + "\" in the GRP file is not a valid recategorization. Each line should contain a guideline ID followed by the new category, separated by a comma, e.g \"Rule 1.1, required\"");
    }

    private void handleCouldNotDetermineWhichGuidelineIsSuppressed(String comment) {

        errorCode |= ERR_COULD_NOT_DETERMINE_SUPPRESSED_GUIDELINE;
        log(currentFile + ": Could not determine which guideline is suppressed by the comment \"" + comment
                + "\". Please add a tag in the style GUIDELINE(<guideline id>) or NONMISRA to the comment in order to avoid this error.");
    }

    private void handleGuidelineInGrpNotFound(String guidelineId) {
        errorCode |= ERR_GRP_ERROR;
        log("The guideline \"" + guidelineId
                + "\" was found in the guideline recategorization plan (GRP), but no corresponding guideline "
                + "was found in the current MISRA rule set.");
    }

    private void handleGuidelineFromWarningNotFound(Violation v) {
        log(name() + " warns about the guideline \"" + v.guidelineId + "\" in file \"" + v.fileName
                + "\", but no such guideline is found in the selected MISRA version.");
        errorCode |= ERR_GUIDELINE_NOT_FOUND;
    }

    private void handleSuppressedGuidelineNotFound(String guidelineId) {
        log(currentFile + ": Suppression comment for the guideline " + guidelineId
                + ", but the guideline was not found.");
        errorCode |= ERR_GUIDELINE_NOT_FOUND;
    }

    private void handleFalsePositiveNotFound(String guidelineId, String comment) {
        log(currentFile + ": False positive tag found for the guideline " + guidelineId
                + ", but this guideline is not suppressed by the comment \"" + comment + "\".");
        errorCode |= ERR_GUIDELINE_NOT_FOUND;
    }

    private void handleDeviationNotFound(String guidelineId, String comment) {
        log(currentFile + ": Deviation tag found for the guideline " + guidelineId
                + ", but this guideline is not suppressed by the comment \"" + comment + "\".");
        errorCode |= ERR_GUIDELINE_NOT_FOUND;
    }

    private void handleAttemptToDeviateFromMandatoryGuideline(Guideline guideline) {
        log(currentFile + ": " + guideline.getId() + " is mandatory, and deviations are illegal");
        errorCode |= ERR_ILLEGAL_DEVIATION;
    }

    private void handleUnableToOpenSourceFile(String fileName) {
        log("Unable to open file \"" + fileName + "\"");
        errorCode |= ERR_READ_FILE;
    }

    private void handleCouldNotWriteLogFile() {
        log("Unable to write to logfile \"" + logFilePath + "\"");
        errorCode |= ERR_WRITE_FILE;
    }

    protected final void log(String s) {
        if (out != null)
            out.println("Misra GCS plugin: " + s);
    }

    private boolean isCompliant(Guideline g) {
        if (g.getStatus() == ComplianceStatus.VIOLATIONS)
            if (g.activeCategory() == Category.REQUIRED || g.activeCategory() == Category.MANDATORY)
                return false;
        return true;
    }

    public final boolean isCompliant() {
        if (errorCode != 0)
            return false;
        for (Guideline g : getGuidelines()) {
            if (!isCompliant(g))
                return false;
        }
        return true;
    }

    public final String summary() {
        return violationsSummary() + " " + deviationsSummary();
    }

    private String deviationsSummary() {
        int[] deviations = getCategoryCount(ComplianceStatus.DEVIATIONS);
        return getSummaryForType(deviations, "deviations");
    }

    private String violationsSummary() {
        int[] violations = getCategoryCount(ComplianceStatus.VIOLATIONS);
        return getSummaryForType(violations, "violations");
    }

    private String getSummaryForType(int[] count, String type) {
        int remaining = nnz(count);
        if (remaining == 0)
            return "There were no " + type + ".";
        StringBuffer r = new StringBuffer("There were " + type + " of ");
        String divider[] = new String[] { ".", " and ", ", " };
        String categories[] = new String[] { "mandatory", "required", "advisory" };
        for (int i = 0; i < 3; i++) {
            if (count[i] > 0) {
                remaining--;
                String guidelineText = count[i] == 1 ? " guideline" : " guidelines";
                r.append(count[i] + " " + categories[i] + guidelineText + divider[remaining]);
            }
        }
        return r.toString();
    }

    private int[] getCategoryCount(ComplianceStatus status) {
        int count[] = new int[3];
        for (Guideline guideline : getGuidelines()) {
            if (guideline.getStatus() == status) {
                switch (guideline.activeCategory()) {
                case ADVISORY:
                    count[2]++;
                    break;
                case REQUIRED:
                    count[1]++;
                    break;
                case MANDATORY:
                    count[0]++;
                    break;
                default:
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Number of non-zero entries in the array
     */
    private int nnz(int[] numbers) {
        int ret = 0;
        for (int i : numbers)
            if (i != 0)
                ret++;
        return ret;
    }

    /**
     * This is how we get all subclasses, so you don't need to do anything but add a
     * new subclass to the project in order to create support for a new tool.
     * 
     * @return All registered {@link WarningParser}s.
     */
    public final static ExtensionList<WarningParser> all() {
        return Jenkins.getInstance().getExtensionList(WarningParser.class);
    }

    public final String getLogFilePath() {
        return logFilePath;
    }

    public final void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public final FilePath getWorkspace() {
        return workspace;
    }

    public final void setWorkspace(FilePath workspace) {
        this.workspace = workspace;
    }
}
