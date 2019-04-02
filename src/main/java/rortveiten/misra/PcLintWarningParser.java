package rortveiten.misra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;

@Extension
public class PcLintWarningParser extends WarningParser {
	
	/** Maps PC-Lint errorcodes to Misra guidelines. One errorcode may be linked to multiple guidelines.  */
	private Map<Integer, List<String>> errorMap;

	
	@Override
	public void setMisraVersion(MisraVersion misraVersion)
	{
		String fileContent = getMisraLintFileContent(misraVersion);
		Pattern appendPattern = Pattern.compile("-append\\((\\d+),\\[MISRA (?:2004 |2012 |C\\+\\+ )?([^,\\]]+).*\\]\\)");
		errorMap = new HashMap<Integer, List<String>>(200);

		Matcher matcher = appendPattern.matcher(fileContent);
		
		while (matcher.find()) {
			Integer errNo = Integer.decode(matcher.group(1));
			List<String> associatedGuidelines = errorMap.get(errNo);
			if (associatedGuidelines == null) {
				associatedGuidelines = new ArrayList<String>();
				errorMap.put(errNo, associatedGuidelines);
			}
			associatedGuidelines.add(matcher.group(2));
		}
	}

	@Override
	protected List<Violation> parseWarningLine(String line) {
		Pattern fullPattern = Pattern.compile("(.*?)\\((\\d+)\\): (?:Note|Info|Error|Warning) (\\d+): [^\\[]*\\[MISRA (?:2004 |2012 |C\\+\\+ )?([^,\\]]*)(:?, advisory|, required|, mandatory)?\\]");
		Pattern misraPattern = Pattern.compile("\\[MISRA (?:2004 |2012 |C\\+\\+ )?([^,\\]]*)(:?, advisory|, required|, mandatory)?\\]");		
		Matcher lineMatcher = fullPattern.matcher(line);
		List<Violation> ret = new ArrayList<Violation>();
		if (lineMatcher.find())
		{
			String fileName = lineMatcher.group(1);
			int lineNumber = Integer.parseInt(lineMatcher.group(2));
			Matcher misraMatch = misraPattern.matcher(line);
			while (misraMatch.find())
			{
				Violation v =  new Violation();
				v.fileName = fileName;
				v.lineNumber = lineNumber;
				v.guidelineId = misraMatch.group(1);
				ret.add(v);
			}
		}
		return ret;
	}

	@Override
	protected List<String> findSuppressionComments(String fileContent) {		
		Pattern regex = Pattern.compile("//lint\\s*([^\n]*)$|/\\*lint\\s+(.*?)\\*/", Pattern.DOTALL | Pattern.MULTILINE);
		List<String> comments = new ArrayList<String>();
		Matcher matcher = regex.matcher(fileContent);
		while (matcher.find())
		{
			if (matcher.group(1) != null)//First group is single line comment "//"
				comments.add(matcher.group(1));
			else
				comments.add(matcher.group(2));//Second group is multiline comment
		}		
		return comments;
	}
	

	@Override
	protected Set<String> getGuidelineIdsFromComment(String comment) {
		Matcher matcher = Pattern.compile("\\G[-!]e(?:sym\\(|func\\(|macro\\(|file\\(|template\\(|string\\(|call\\(|type\\()?(\\d+)(:?,[^\\)]*\\)\\s*|\\s*)").matcher(comment);
		Set<String> ret = new HashSet<String>();
		while (matcher.find()) {
			Integer errNo = Integer.decode(matcher.group(1));
			List<String> reqIds = errorMap.get(errNo);
			if (reqIds == null)
				return null;
			ret.addAll(reqIds);
		}
		return ret;
	}

    @Override
	public String name() {
		return "PC-Lint";
	}
    
    @Override
    public Set<MisraVersion> supportedMisraVersions() {
        Set<MisraVersion> versions = new HashSet<MisraVersion>();
        versions.add(MisraVersion.C_1998);
        versions.add(MisraVersion.C_2004);
        versions.add(MisraVersion.C_2012);
        versions.add(MisraVersion.CPP_2008);
        return versions;
     }

}
