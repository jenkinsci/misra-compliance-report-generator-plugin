package rortveiten.misra;

import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;

@Extension
public class CppcheckWarningParser extends WarningParser {

    private final Pattern suppressionPattern = 
            Pattern.compile("(?:(?<=//)\\s*cppcheck-suppress\\s[^\\n]*$)|(?:(?<=/\\*)\\s*cppcheck-suppress\\s.*?(?=\\*/))",
            Pattern.DOTALL | Pattern.MULTILINE);
    private final Pattern guidelinePattern = Pattern.compile("misra-c2012-(\\d+\\.\\d+)");
    private final Pattern warningLinePattern = Pattern.compile("\\[([^:]*):(\\d+)\\][^\\[]*\\[misra-c2012-(\\d+.\\d+)\\]");

    @Override
    protected List<Violation> parseWarningLine(String line) {
        List<Violation> violations = new ArrayList<Violation>();
        Matcher matcher = warningLinePattern.matcher(line);
        if (matcher.find()) {
            Violation violation = new Violation();
            violation.fileName = matcher.group(1);
            violation.lineNumber = Integer.parseInt(matcher.group(2));
            violation.guidelineId = "Rule " + matcher.group(3);
            violations.add(violation);
        }
        return violations;
    }

    @Override
    protected List<String> findSuppressionComments(String fileContent) {
        List<String> comments = new ArrayList<String>();
        Matcher matcher = suppressionPattern.matcher(fileContent);
        while (matcher.find())
            comments.add(matcher.group());
        return comments;
    }

    @Override
    protected Set<String> getGuidelineIdsFromComment(String comment) {
        HashSet<String> ids = new HashSet<String>();
        Matcher matcher = guidelinePattern.matcher(comment);
        if (matcher.find())
            ids.add("Rule " + matcher.group(1));
        return ids;
    }

    @Override
    public String name() {
        return "Cppcheck";
    }

    @Override
    public Set<MisraVersion> supportedMisraVersions() {
        Set<MisraVersion> versions = new HashSet<MisraVersion>();
        versions.add(MisraVersion.C_2012);// Only C 2012 is supported by cppcheck at the moment.
        return versions;
    }
}
