package rortveiten.misra;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import rortveiten.misra.CppcheckWarningParser;
import rortveiten.misra.WarningParser;
import rortveiten.misra.WarningParser.MisraVersion;
import rortveiten.misra.WarningParser.Violation;

public class CppcheckWarningParserTest {

    CppcheckWarningParser parser = new CppcheckWarningParser();
    
    @Test
    public void testParseWarningLine() {
        //Test no violations found
        List<Violation> violations = parser.parseWarningLine("This line doesn't provide any actual violation info");
        assertEquals(0, violations.size());
        
        //Test violation found
        String warningLine = "[Drivers/drvMCU.c:36]: (style) misra violation (use --rule-texts=<file> to get proper output) [misra-c2012-15.6]";
        violations = parser.parseWarningLine(warningLine);
        assertEquals(1, violations.size());
        Violation violation = violations.get(0);
        assertEquals("Drivers/drvMCU.c", violation.fileName);
        assertEquals(36, violation.lineNumber);
        assertEquals("Rule 15.6", violation.guidelineId);
    }

    @Test
    public void testFindSuppressionComments() {
        String fileContent = "heelo \n Yess \n //a single line comment \n"
                + "a = 2; // cppcheck-suppress misra-c2012-5.2 ; some more text\n"
                + "and now comes a multiline comment /* this is a multiline comment"
                + "but no suppression */"
                + "and /*cppcheck-suppress this is one that is * a suppression\n"
                + "right? **/ Ok? Syntax error: */"
                + "// yes";
        
        List<String> comments = parser.findSuppressionComments(fileContent);
        
        assertEquals(2, comments.size());
        assertEquals(" cppcheck-suppress misra-c2012-5.2 ; some more text", comments.get(0));
        assertEquals("cppcheck-suppress this is one that is * a suppression\nright? *", comments.get(1));
    }

    @Test
    public void testGetGuidelineIdsFromComment() {
       
        //Check no misra rule suppressed
        String comment = " cppcheck-suppress no misra rule is suppressed here ";
        
        Set<String> ids = parser.getGuidelineIdsFromComment(comment);
        
        assertEquals(0, ids.size());
        
        //Check one misra rule suppressed. Cppcheck cannot suppress more than one rule per comment.
        comment = "cppcheck misra-c2012-13.11; Something irrelevant here";
        
        ids = parser.getGuidelineIdsFromComment(comment);
        
        assertEquals(1, ids.size());
        assertTrue(ids.contains("Rule 13.11"));
        
    }

    @Test
    public void testName() {
        assertEquals("Cppcheck", parser.name());
    }
    
    @Test
    public void testSupportedMisraVersions()
    {
        Set<MisraVersion> supportedVersions = parser.supportedMisraVersions();
        //Only C 2012 is supported by cppcheck at the moment
        assertEquals(1, supportedVersions.size());
        assertTrue(supportedVersions.contains(MisraVersion.C_2012));
    }

}
