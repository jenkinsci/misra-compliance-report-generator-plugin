package rortveiten.misra;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.junit.Test;

import rortveiten.misra.WarningParser.MisraVersion;
import rortveiten.misra.WarningParser.Violation;

public class PcLintWarningParserTest {

    private String resourceContent() {
        return "/* This is a C sourefile */\r\n" + "\r\n" + "//This is not a suppression comment\r\n"
                + "void dostuff(int hello)\r\n" + "{\r\n" + "    doOtherStuff(4);//lint -e493 What's going on\r\n"
                + "}\r\n" + "\r\n" + "/*lint e589 a multiline comment\r\n" + "is here\r\n" + "*/\r\n" + "int b;\r\n";
    }
	
	@Test
	public void findsSingleLineComment() throws URISyntaxException {
		PcLintWarningParser parser = new PcLintWarningParser();
		List<String> comments = parser.findSuppressionComments(resourceContent());
		assertEquals("-e493 What's going on", comments.get(0));
	}

	@Test
	public void findsMultiLineComment() throws IOException, URISyntaxException {
		PcLintWarningParser parser = new PcLintWarningParser();
		List<String> comments = parser.findSuppressionComments(resourceContent());
		assertEquals("e589 a multiline comment\r\nis here\r\n", comments.get(1));
	}
	
	@Test
	public void nameIsPcLint() {
		PcLintWarningParser parser = new PcLintWarningParser();
		assertEquals("PC-Lint", parser.name());
	}
	
	@Test
	public void testParseWarningLine() {
		PcLintWarningParser parser = new PcLintWarningParser();
		
		List<Violation> vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(75): Note 9029: Mismatched essential type categories for binary operator [MISRA 2012 Rule 10.4, required] (Note <a href=\"/userContent/LintMsgRef.html#9029\">9029</a>)");
		
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(0).fileName);
		assertEquals(75, vs.get(0).lineNumber);
		assertEquals("Rule 10.4", vs.get(0).guidelineId);
	}
	
	@Test
	public void emptyListIfNoMisraWarning() {
		PcLintWarningParser parser = new PcLintWarningParser();
		
		List<Violation> vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(50): Warning 551: Symbol 'canBaudRate' (line 50, file C:\\UST3\\qse30\\Drivers\\drvCAN.c) not accessed (Warning <a href=\"/userContent/LintMsgRef.html#551\">551</a>)");
		
		assertEquals(0, vs.size());
	}
	
	@Test
	public void findMultipleMisraViolationsInOneWarning() {
		PcLintWarningParser parser = new PcLintWarningParser();
		
		List<Violation> vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(79): Note 931: Both sides have side effects [MISRA 2012 Rule 1.3, required], [MISRA 2012 Rule 13.2, required] (Note <a href=\"/userContent/LintMsgRef.html#931\">931</a>)");
		
		assertEquals(2, vs.size());
		assertEquals("Rule 1.3", vs.get(0).guidelineId);
		assertEquals(79, vs.get(0).lineNumber);
		assertEquals("Rule 13.2", vs.get(1).guidelineId);
		assertEquals(79, vs.get(0).lineNumber);
		assertEquals(79, vs.get(1).lineNumber);
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(0).fileName);
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(1).fileName);
	}
	
	@Test
	public void findWarningsFromOtherMisraVersions() {
		PcLintWarningParser parser = new PcLintWarningParser();
		
		List<Violation> vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(75): Note 9029: blabla [MISRA 2004 Rule 13.2, required]");
		
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(0).fileName);
		assertEquals(75, vs.get(0).lineNumber);
		assertEquals("Rule 13.2", vs.get(0).guidelineId);
		
		vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(75): Note 9029: blabla [MISRA C++ Rule 0-1-4]");
		
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(0).fileName);
		assertEquals(75, vs.get(0).lineNumber);
		assertEquals("Rule 0-1-4", vs.get(0).guidelineId);
		
		vs = parser.parseWarningLine("C:\\UST3\\qse30\\Drivers\\drvCAN.c(75): Note 9029: blabla [MISRA Rule 11.2]");
		
		assertEquals("C:\\UST3\\qse30\\Drivers\\drvCAN.c", vs.get(0).fileName);
		assertEquals(75, vs.get(0).lineNumber);
		assertEquals("Rule 11.2", vs.get(0).guidelineId);		
	}
	
	@Test
	public void getGuidelineId() {
		PcLintWarningParser parser = new PcLintWarningParser();
		parser.setMisraVersion(MisraVersion.CPP_2008);
		Set<String> reqIds = parser.getGuidelineIdsFromComment("-e774");
		
		assertEquals(3, reqIds.size());
		assertTrue(reqIds.contains("Rule 0-1-1"));
		assertTrue(reqIds.contains("Rule 0-1-2"));
		assertTrue(reqIds.contains("Rule 0-1-9"));		
	}
	
	@Test
	public void getGuidelineIdWithMultipleSuppressions() {
		PcLintWarningParser parser = new PcLintWarningParser();
		parser.setMisraVersion(MisraVersion.C_2012);
		Set<String> reqIds = parser.getGuidelineIdsFromComment("-esym(16,rai) !e9015 -emacro(484,hei) -efunc(9024, jau) -efile(9029, hau) Hello -e4444444");//Last one is ignored because of the text inbetween
		
		assertEquals(5, reqIds.size());
		assertTrue(reqIds.contains("Rule 20.13"));
		assertTrue(reqIds.contains("Rule 20.12"));
        assertTrue(reqIds.contains("Rule 20.10"));
		assertTrue(reqIds.contains("Rule 20.11"));		
		assertTrue(reqIds.contains("Rule 10.4"));
	}
	
	@Test
	public void eachGuidelineIsOnlyOnceInReturnedIds() {
        PcLintWarningParser parser = new PcLintWarningParser();
        parser.setMisraVersion(MisraVersion.C_2012);
        Set<String> reqIds = parser.getGuidelineIdsFromComment("-esym(16,rai) -esym(16,hei)");
        
        assertEquals(1, reqIds.size());      	    
	}
	
    @Test
    public void testSupportedMisraVersions()
    {
        Set<MisraVersion> versions = new PcLintWarningParser().supportedMisraVersions();
        assertTrue(versions.contains(MisraVersion.C_1998));
        assertTrue(versions.contains(MisraVersion.C_2004));
        assertTrue(versions.contains(MisraVersion.C_2012));
        assertTrue(versions.contains(MisraVersion.CPP_2008));
    }
}

