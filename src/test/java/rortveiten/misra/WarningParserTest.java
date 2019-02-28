package rortveiten.misra;

import org.junit.Test;
import org.mockito.Mockito;

import rortveiten.misra.Guideline.Category;
import rortveiten.misra.Guideline.ComplianceStatus;
import rortveiten.misra.WarningParser.CommentProperties;
import rortveiten.misra.WarningParser.MisraVersion;
import rortveiten.misra.WarningParser.Suppression;
import rortveiten.misra.WarningParser.Violation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class WarningParserTest {

    private String resourceFileName(String filename) {
        try {
            return Paths.get(getClass().getResource(filename).toURI()).toString();
        } catch (URISyntaxException e) {
            fail("Failed to find resource file path");
        }
        return null;
    }
    
    private String resourceFileName() {
        return resourceFileName("sourcefile.c");
    }
	
	private List<Guideline> someGuidelines()
	{
		List<Guideline> ret = new ArrayList<Guideline>();
		Guideline g = new Guideline("Rule 1.1","Adv");
		ret.add(g);
		g = new Guideline("Directive 1.2","Adv");
		ret.add(g);
		return ret;		
	}
	

	private WarningParser getWarningParserSpy(List<Guideline> guidelines) {
		//Simplest way of creating an implementation with stubbed abstract methods
		//Unlike mock, this one retains statically initialized fields
		WarningParser ret = Mockito.spy(WarningParser.class);
		if (guidelines != null)
			ret.setGuidelines(guidelines);	
		return ret;
	}
	
	
	@Test
	 public void testReadGrp() {
		 List<String> l = Arrays.asList("Directive 1.2, Mandatory", "Rule 1.1, Required");
		 List<Guideline> g = someGuidelines();
		 WarningParser parser = getWarningParserSpy(g);
		 
		 parser.readGrp(l);
		 
		 assertEquals(Category.MANDATORY, g.get(1).getReCategorization());
		 assertEquals(Category.REQUIRED, g.get(0).getReCategorization());
		 
		 l = Arrays.asList("Rule 1.1, disappLIED");
		 g = someGuidelines();
		 parser.setGuidelines(g);
		 parser.readGrp(l);
		 
		 assertEquals(Category.DISAPPLIED, g.get(0).getReCategorization());
	 }
	
	@Test
	public void testGuidelineInGrpNotFound() {
		 List<String> l = Arrays.asList("Directive 0.0, Mandatory");		 
		 WarningParser parser = getWarningParserSpy(someGuidelines());
		 
		 parser.readGrp(l);
		 
		 assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
	}
	
	@Test
	public void testInvalidRecategorizationLevel() {
		 List<String> l = Arrays.asList("Directive 1.2, Benedict Cumberbatch");		 
		 WarningParser parser = getWarningParserSpy(someGuidelines());
		 
		 parser.readGrp(l);
		 
		 assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());		
	}
	
	@Test
	public void testNoRecategorizationLevel() {
		 List<String> l = Arrays.asList("Directive 1.2");		 
		 WarningParser parser = getWarningParserSpy(someGuidelines());
		 
		 parser.readGrp(l);
		 
		 assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());		
	}
	
	@Test
	public void legalRecategorizationsDoNotProduceErrors() {
		List<Guideline> g = Arrays.asList(
								new Guideline("Rule 1", "Advisory"),
								new Guideline("Rule 2", "Advisory"),
								new Guideline("Rule 3", "Advisory"),
								new Guideline("Rule 4", "Advisory"),
								new Guideline("Rule 5", "Required"),
								new Guideline("Rule 6", "Required"),
								new Guideline("Rule 7", "Mandatory"));
		List<String> l = Arrays.asList(
								"Rule 1, Mandatory", 
								"Rule 2, Required",
								"Rule 3, Advisory",
								"Rule 4, Disapplied",
								"Rule 5, Mandatory",
								"Rule 6, Required",
								"Rule 7, Mandatory");
		WarningParser parser = getWarningParserSpy(g);
		
		
		parser.readGrp(l);
		
		assertEquals(0, parser.getErrorCode());
	}
	
	
	@Test
	public void IllegalRecategorizationsCauseErrors() {
		List<Guideline> g = Arrays.asList(new Guideline("Rule 1", "Mandatory"));
		List<String> l = Arrays.asList("Rule 1, Required");
		WarningParser parser = getWarningParserSpy(g);
		
		parser.readGrp(l);
		
		assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
		
		/***/
		
		g = Arrays.asList(new Guideline("Rule 1", "Mandatory"));
		l = Arrays.asList("Rule 1, Advisory");
		parser = getWarningParserSpy(g);
		
		parser.readGrp(l);
		
		assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
		
		/***/
		
		g = Arrays.asList(new Guideline("Rule 1", "Mandatory"));
		l = Arrays.asList("Rule 1, Disapplied");
		parser = getWarningParserSpy(g);
		
		parser.readGrp(l);
		
		assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
		
		/***/
		
		g = Arrays.asList(new Guideline("Rule 1", "Required"));
		l = Arrays.asList("Rule 1, Advisory");
		parser = getWarningParserSpy(g);
		
		parser.readGrp(l);
		
		assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
		
		/***/
		
		g = Arrays.asList(new Guideline("Rule 1", "Required"));
		l = Arrays.asList("Rule 1, Disapplied");
		parser = getWarningParserSpy(g);
		
		parser.readGrp(l);
		
		assertEquals(WarningParser.ERR_GRP_ERROR, parser.getErrorCode());
	}	
		
	private WarningParser getParserWithMockedCallsToLineParser(List<String> lines, List<List<Violation>> violations, List<Guideline> guidelines)
	{
		WarningParser parser = getWarningParserSpy(null);
		for(int i = 0; i < lines.size(); i++)
		{
			when(parser.parseWarningLine(lines.get(i))).thenReturn(violations.get(i));
		}		
		parser.setGuidelines(guidelines);
		return parser;
	}
	
	private List<List<Violation>> someViolations()
	{
		List<List<Violation>> ret = new LinkedList<List<Violation>>();
		Violation v1 = new Violation();
		v1.guidelineId = "Rule 1.1";
		ret.add(Arrays.asList(v1));
		
		Violation v2 = new Violation();
		v2.guidelineId = "Directive 1.2";
		ret.add(Arrays.asList(v2));
		return ret;
	}
	
	@Test
	public void warningParserCallsVirtualMethodWithEachWarningLine() {
		List<String> warningLines = Arrays.asList("Hau", "Jau");
		List<Guideline> guidelines = someGuidelines();
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, someViolations(), guidelines);
		
		parser.parseWarnings(warningLines);
		
		verify(parser).parseWarningLine("Hau");
		verify(parser).parseWarningLine("Jau");
	}
	
	@Test
	public void warningParserIgnoresNullValuesReturnedFromVirtualCall() {
		List<String> warningLines = Arrays.asList("Hau", "Rau", "Jau");
		List<List<Violation>> v = someViolations();
		v.add(1, Arrays.asList((Violation)null));
		List<Guideline> guidelines = someGuidelines();
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, v, guidelines);
		
		parser.parseWarnings(warningLines);
		
		
		assertEquals(2, guidelines.size());
		assertEquals("Rule 1.1", guidelines.get(0).getId());
		assertEquals("Directive 1.2", guidelines.get(1).getId());			
	}
	
	@Test
	public void warningParserIgnoresViolationsWithoutId() {
		List<String> warningLines = Arrays.asList("Hau", "Jau");
		List<Guideline> guidelines = someGuidelines();
		List<List<Violation>> v = someViolations();
		v.get(0).get(0).guidelineId = null;
		v.get(1).get(0).guidelineId = "";
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, v, guidelines);
		
		parser.parseWarnings(warningLines);
		
		assertEquals(0, parser.getErrorCode());
	}

	@Test
	public void warningParserSetsStateToViolations() {
		List<String> warningLines = Arrays.asList("Hau");
		List<Guideline> guidelines = someGuidelines();
		List<List<Violation>> v = someViolations();
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, v, guidelines);
		
		parser.parseWarnings(warningLines);
		
		
		assertEquals(Guideline.ComplianceStatus.VIOLATIONS, guidelines.get(0).getStatus());
	}


	@Test
	public void warningParserDoesNotSetExistingGuidelineToViolationsIfNoViolations() {
		List<String> warningLines = Arrays.asList("Hau", "Jau");
		List<List<Violation>> v = someViolations();
		List<Guideline> guidelines = someGuidelines();
		guidelines.add(new Guideline("Anothertype 5.2"));
		guidelines.get(2).setStatus(ComplianceStatus.COMPLIANT);
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, v, guidelines);
		
		parser.parseWarnings(warningLines);
		
		assertEquals(3, guidelines.size());		
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
		assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(2).getStatus());
	}

	@Test
	public void warningParserDoesNotSetViolationIfRuleIsDisapplied() {
		List<String> warningLines = Arrays.asList("Hau", "Jau");
		List<List<Violation>> v = someViolations();
		List<Guideline> guidelines = someGuidelines();
		guidelines.get(1).setReCategorization(Category.DISAPPLIED);
		guidelines.get(1).setStatus(ComplianceStatus.DISAPPLIED);
		
		WarningParser parser = getParserWithMockedCallsToLineParser(warningLines, v, guidelines);
		
		parser.parseWarnings(warningLines);
		
		assertEquals(ComplianceStatus.DISAPPLIED, guidelines.get(1).getStatus());
	}


	@Test
	public void mainCommentsParserCallsSubclassForEachFile() {
		WarningParser parser = getWarningParserSpy(null);
		List<String> files = Arrays.asList(resourceFileName("sourcefile.c"), resourceFileName("anothersourcefile.c"));
		try {
			
			parser.parseSourceFiles(files);			
			

			verify(parser).findSuppressionComments("Sourcefilecontent");
			verify(parser).findSuppressionComments("Somethingelse");
		}
		catch (Exception e){			
			fail("Unexpected Exception: " + e);
		}
	}
	
	private List<Guideline> someOtherGuidelines()
	{
		List<Guideline> ret = new ArrayList<Guideline>();
		ret.add(new Guideline("Jau 1.1"));
		ret.add(new Guideline("hus 1.2"));
		ret.get(0).setStatus(ComplianceStatus.COMPLIANT);
		ret.get(1).setStatus(ComplianceStatus.COMPLIANT);
		return ret;		
	}
	
	private List<CommentProperties> someSuppressions() {
		List<CommentProperties> comments = new ArrayList<CommentProperties>();
        addSuppression(comments, "Jau 1.1");
        addSuppression(comments, "hus 1.2");
			
		return comments;		
	}


    private void addSuppression(List<CommentProperties> comments, String guidelineId) {
        CommentProperties props = new CommentProperties();
		props.suppressions = new HashMap<String, Suppression>();		
		Suppression s = new Suppression();
		s.guidelineId = guidelineId;
		props.suppressions.put(s.guidelineId, s);
		comments.add(props);
    }
	
	private WarningParser getParserWithMockedCallToParseSourceFile(List<String> files, List<Guideline> guidelines, List<CommentProperties> comments) {
		WarningParser parser = spy(WarningParser.class);
		try {
			for (String file : files) {				
				when(parser.parseSourceFile(file)).thenReturn(comments);
			}
		} catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}
		parser.setGuidelines(guidelines);
		return parser;
	}
	
	
	@Test
	public void fileParserSetsSuppressedCommentsToViolations()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, someSuppressions());

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
	}
	
	
	@Test
	public void falsePositivesAreIgnored()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = someSuppressions();
		comments.get(0).suppressions.get("Jau 1.1").isFalsePositive = true;
		
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
	}
	
	@Test
	public void deviationCommentsMakeTheComplianceTypeIntoDeviations()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = someSuppressions();
		comments.get(0).suppressions.get("Jau 1.1").isDeviation = true;
		
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(ComplianceStatus.DEVIATIONS, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
	}
	
	@Test
	public void deviationCommentsDoesNotMakeMakeTheComplianceTypeIntoDeviationsIfViolationsPresent()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = someSuppressions();
		comments.get(0).suppressions.get("Jau 1.1").isDeviation = true;
		guidelines.get(0).setStatus(ComplianceStatus.VIOLATIONS);
		
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
	}	
	
	@Test
	public void dissappliedGuidelineStaysDisappliedAfterSuppression()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = someSuppressions();
		guidelines.get(0).setReCategorization(Category.DISAPPLIED);
		
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(ComplianceStatus.DISAPPLIED, guidelines.get(0).getStatus());
		assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
	}
	
	@Test
	public void deviationReferenceAndLinkAreRecordedForDeviations()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = someSuppressions();
		comments.get(0).suppressions.get("Jau 1.1").isDeviation = true;
		comments.get(0).suppressions.get("Jau 1.1").deviationReference = "RAI";
		comments.get(0).suppressions.get("Jau 1.1").deviationLink = "http://rai.com";
				
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		
		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}

		assertEquals(1, guidelines.get(0).getDeviationReferences().size());
		assertEquals("RAI", guidelines.get(0).getDeviationReferences().get(0).getReference());
		assertEquals("http://rai.com", guidelines.get(0).getDeviationReferences().get(0).getLink());
	}
	
	@Test
	public void errorIfSuppressedGuidelineNotFound()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = new ArrayList<CommentProperties>();
		addSuppression(comments, "Heyyy 5000002");
				
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		parser.parseSourceFiles(files);
		assertEquals(WarningParser.ERR_GUIDELINE_NOT_FOUND, parser.getErrorCode());
	}
	
	@Test
	public void nonMisraSuppressedWarningsIgnored()
	{
		List<Guideline> guidelines = someOtherGuidelines();
		List<String> files = Arrays.asList(resourceFileName());
		List<CommentProperties> comments = new ArrayList<CommentProperties>();
		comments.add(new CommentProperties());
		comments.get(0).isNonMisra = true;
		comments.get(0).suppressions = new HashMap<String, Suppression>();
				
		WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);

		try {			
			parser.parseSourceFiles(files);			
		}
		catch (Exception e)	{
			fail("Unexpected Exception: " + e);
		}
		
		assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(0).getStatus());
	}
	
	
	@Test 
	public void guidelineIdentifierCalledForEachDiscoveredSuppressionComment()
	{
		List<String> comments = Arrays.asList("A NON MISRA warning", "Another NONMISRA comment");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = new ArrayList<Guideline>();
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        
        parser.parseSourceFiles(files);
        
        
        verify(parser).getGuidelineIdsFromComment(comments.get(0));
        verify(parser).getGuidelineIdsFromComment(comments.get(1));
	}
	
	
	@Test 
	public void findsLinkAndReferenceForDeviations()
	{
		List<String> comments = Arrays.asList("First DEVIATION(Hello, hello.com) yaas?", "Another DEVIATION( Without link )");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1")));
        when(parser.getGuidelineIdsFromComment(comments.get(1))).thenReturn(new HashSet<String>(Arrays.asList("Req2")));
        
        parser.parseSourceFiles(files);
        
        assertEquals("Hello", guidelines.get(0).getDeviationReferences().get(0).getReference());
        assertEquals("hello.com", guidelines.get(0).getDeviationReferences().get(0).getLink());
        assertEquals("Without link", guidelines.get(1).getDeviationReferences().get(0).getReference());
	}
	
	@Test 
	public void setsErrorWhenGuidelineRelatedToCommentNotFound()
	{
		List<String> comments = Arrays.asList("Whatever");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"));
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(null);			
        
        parser.parseSourceFiles(files);
        
        assertEquals(WarningParser.ERR_COULD_NOT_DETERMINE_SUPPRESSED_GUIDELINE, parser.getErrorCode());
	}
	
	@Test 
	public void setsErrorWhenNonExistingGuidelineDiscovered()
	{
		List<String> comments = Arrays.asList("Whatever");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"));
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req2")));			
        
        parser.parseSourceFiles(files);
        
        assertEquals(WarningParser.ERR_GUIDELINE_NOT_FOUND, parser.getErrorCode());
	}
	
	
	@Test 
	public void usesGuidelineTagToIdentifyGuidelineIfPresent()
	{
		List<String> comments = Arrays.asList("Something something GUIDELINE(Req1) GUIDELINE(Req3) something");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"), new Guideline("Req3"));
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        //This one is ignored - overridden by the guideline tag
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req2")));			
        
        parser.parseSourceFiles(files);
        
        assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(0).getStatus());
        assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(1).getStatus());
        assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(2).getStatus());
	}
	
	@Test
	public void canReadGuidelinesForAllMisraTypes() {
		WarningParser parser = getWarningParserSpy(null);
		parser.initialize(MisraVersion.C_1998);
		List<Guideline> guidelines = parser.getGuidelines();
		assertEquals(127, guidelines.size());
		assertEquals("Rule 109", guidelines.get(108).getId());
		assertEquals(Category.ADVISORY, guidelines.get(62).getCategory());
		assertEquals(Category.REQUIRED, guidelines.get(63).getCategory());
		
		parser.initialize(MisraVersion.C_2004);
		guidelines = parser.getGuidelines();
		assertEquals(142, guidelines.size());
		assertEquals("Rule 20.10", guidelines.get(138).getId());
		assertEquals(Category.REQUIRED, guidelines.get(137).getCategory());
		assertEquals(Category.ADVISORY, guidelines.get(4).getCategory());
		
		parser.initialize(MisraVersion.CPP_2008);
		guidelines = parser.getGuidelines();
		assertEquals(228, guidelines.size());
		assertEquals("Rule 19-3-1", guidelines.get(226).getId());
		assertEquals(Category.REQUIRED, guidelines.get(225).getCategory());
		assertEquals(Category.ADVISORY, guidelines.get(23).getCategory());
		assertEquals(Category.UNKNOWN, guidelines.get(13).getCategory());//The lint file says "doc" instead of "adv" or "guideline", and I haven't figured out what that means since I don't own the misra cpp pdf
		
		
		parser.initialize(MisraVersion.C_2012);
		guidelines = parser.getGuidelines();
		assertEquals(159, guidelines.size());
		assertEquals("Rule 22.5", guidelines.get(157).getId());
		assertEquals("Directive 2.1", guidelines.get(1).getId());
	}
	
	@Test
	public void nonCompliantIfRequiredRulesViolated() {
		Guideline g = new Guideline("Rule 1", "Required");
		g.setStatus(ComplianceStatus.VIOLATIONS);
		WarningParser parser = getWarningParserSpy(Arrays.asList(g));
				
		assertFalse(parser.isCompliant());
	}
	//Null checks
	
	@Test
	public void compliantIfOnlyAdvisoryRulesViolated() {
		Guideline g = new Guideline("Rule 1", "Advisory");
		g.setStatus(ComplianceStatus.VIOLATIONS);
		WarningParser parser = getWarningParserSpy(Arrays.asList(g));
				
		assertTrue(parser.isCompliant());		
	}
	
	@Test
	public void compliantIfNoGuidelinesViolated() {
		WarningParser parser = getWarningParserSpy(Arrays.asList(new Guideline("Rule 1", "Required")));
		
		assertTrue(parser.isCompliant());		
	}
	
	@Test
	public void nonCompliantIfMandatoryRulesViolated() {		
		Guideline g = new Guideline("Rule 1", "Mandatory");
		g.setStatus(ComplianceStatus.VIOLATIONS);
		WarningParser parser = getWarningParserSpy(Arrays.asList(g));
				
		assertFalse(parser.isCompliant());
	}
	
	@Test
	public void reCategorizationOvveridesOriginalCategory() {
		//Only two recategorizations can make a difference for compliance:
		//advisory->required and advisory->mandatory
		Guideline g = new Guideline("Rule 1", "Advisory");
		g.setReCategorization("Required");
		g.setStatus(ComplianceStatus.VIOLATIONS);
		WarningParser parser = getWarningParserSpy(Arrays.asList(g));
				
		assertFalse(parser.isCompliant());
		
		g.setReCategorization("Mandatory");
		
		assertFalse(parser.isCompliant());
	}
	
	@Test 
	public void errorIfDeviationOfMandatoryGuideline()
	{
		List<String> comments = Arrays.asList("DEVIATION(Rai, rai.com)");
		List<String> files = Arrays.asList(resourceFileName());
		List<Guideline> guidelines = Arrays.asList(new Guideline("Req1", "Mandatory"));
		WarningParser parser = getWarningParserSpy(guidelines);

		when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1")));
        
        parser.parseSourceFiles(files);
        
        assertEquals(WarningParser.ERR_ILLEGAL_DEVIATION, parser.getErrorCode());
	}
	
	@Test
	public void incompliantOnError() {
		 List<String> l = Arrays.asList("Something, Advisory");		 
		 WarningParser parser = getWarningParserSpy(Arrays.asList(new Guideline("Something else")));
		 
		 parser.readGrp(l);//Will cause an error
		 
		 assertFalse(parser.isCompliant());
	}
	
    @Test
    public void testViolationsSummary() {        
        Guideline g1 = new Guideline("Rule 1", "Mandatory");
        g1.setStatus(ComplianceStatus.VIOLATIONS);
        Guideline g2 = new Guideline("Rule 2", "Mandatory");
        g2.setStatus(ComplianceStatus.VIOLATIONS);
        Guideline g3 = new Guideline("Rule 3", "Advisory");
        g3.setStatus(ComplianceStatus.VIOLATIONS);
        WarningParser parser = getWarningParserSpy(Arrays.asList(g1, g2, g3));
                
        assertEquals("There were violations of 2 mandatory guidelines and 1 advisory guideline. There were no deviations.",  parser.summary());
    }
    
    @Test
    public void testDeviationsSummary() {        
        Guideline g1 = new Guideline("Rule 1", "Mandatory");
        g1.setStatus(ComplianceStatus.DEVIATIONS);
        Guideline g2 = new Guideline("Rule 2", "Required");
        g2.setStatus(ComplianceStatus.DEVIATIONS);
        Guideline g3 = new Guideline("Rule 3", "Advisory");
        g3.setStatus(ComplianceStatus.DEVIATIONS);
        WarningParser parser = getWarningParserSpy(Arrays.asList(g1, g2, g3));
                
        assertEquals("There were no violations. There were deviations of 1 mandatory guideline, 1 required guideline and 1 advisory guideline.",  parser.summary());
    }
    
    @Test
    public void testDeviationsAndViolationsSummary() {        
        Guideline g1 = new Guideline("Rule 1", "Required");
        g1.setStatus(ComplianceStatus.VIOLATIONS);
        Guideline g2 = new Guideline("Rule 2", "Required");
        g2.setStatus(ComplianceStatus.DEVIATIONS);
        Guideline g3 = new Guideline("Rule 3", "Advisory");
        g3.setStatus(ComplianceStatus.DEVIATIONS);
        WarningParser parser = getWarningParserSpy(Arrays.asList(g1, g2, g3));
                
        assertEquals("There were violations of 1 required guideline. There were deviations of 1 required guideline and 1 advisory guideline.",  parser.summary());
    }
    
    @Test
    public void aSingleCommentCanHaveViolationsAndFalsePositives() {
        List<String> comments = Arrays.asList("Both a violation and a FALSE_POSITIVE(Req1)");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));
        
        parser.parseSourceFiles(files);
        
        assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(0).getStatus());
        assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());        
    }
    
    @Test
    public void aSingleCommentCanHaveViolationsAndDeviations() {
        List<String> comments = Arrays.asList("Both a violation and a DEVIATION(dev1, http://dev1, Req1)");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));
        
        parser.parseSourceFiles(files);
        
        assertEquals(ComplianceStatus.DEVIATIONS, guidelines.get(0).getStatus());
        assertEquals(ComplianceStatus.VIOLATIONS, guidelines.get(1).getStatus());
        assertEquals("dev1", guidelines.get(0).getDeviationReferences().get(0).getReference());
        assertEquals("http://dev1", guidelines.get(0).getDeviationReferences().get(0).getLink());        
    }
    
    @Test
    public void deviationTagWithoutThirdParameterSetsAllSuppressedGuidelinesToDeviations() {
        List<String> comments = Arrays.asList("Two deviations DEVIATION(dev1, http://dev1)");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));
        
        parser.parseSourceFiles(files);
        
        assertEquals(ComplianceStatus.DEVIATIONS, guidelines.get(0).getStatus());
        assertEquals(ComplianceStatus.DEVIATIONS, guidelines.get(1).getStatus());
        assertEquals("dev1", guidelines.get(0).getDeviationReferences().get(0).getReference());
        assertEquals("http://dev1", guidelines.get(0).getDeviationReferences().get(0).getLink());
        assertEquals("dev1", guidelines.get(1).getDeviationReferences().get(0).getReference());
        assertEquals("http://dev1", guidelines.get(1).getDeviationReferences().get(0).getLink());        
    }
    
    @Test
    public void falsePositiveTagWithoutArgumentCountsForAllSuppressedGuidelines() {
        List<String> comments = Arrays.asList("A FALSE_POSITIVE for all suppressed requirements");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments(files.get(0))).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));
        
        parser.parseSourceFiles(files);
        
        assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(0).getStatus());
        assertEquals(ComplianceStatus.COMPLIANT, guidelines.get(1).getStatus());        
    }
    
    @Test
    public void colonInTheDeviationLinkIsReplacedByColonSlashSlash()
    {
        List<String> comments = Arrays.asList("DEVIATION(Hello, http:hello.com, Req1) DEVIATION(Jau, http:/jau.com, Req2)", "DEVIATION(Hoi, http:hoi.com)");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments("Sourcefilecontent")).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));
        when(parser.getGuidelineIdsFromComment(comments.get(1))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));

        parser.parseSourceFiles(files);
        
        assertEquals("http://hello.com", guidelines.get(0).getDeviationReferences().get(0).getLink());
        assertEquals("http://hoi.com", guidelines.get(0).getDeviationReferences().get(1).getLink());
        assertEquals("http://jau.com", guidelines.get(1).getDeviationReferences().get(0).getLink());
        assertEquals("http://hoi.com", guidelines.get(1).getDeviationReferences().get(1).getLink());
    }
    
    @Test
    public void falsePositiveTagCorrectlyDetectedWhenThereIsMoreThanOne() {
        List<String> comments = Arrays.asList("FALSE-POSITIVE(Req1) FALSEPOSITIVE(Req2)");
        List<String> files = Arrays.asList(resourceFileName());
        List<Guideline> guidelines = Arrays.asList(new Guideline("Req1"), new Guideline("Req2"));
        WarningParser parser = getWarningParserSpy(guidelines);

        when(parser.findSuppressionComments(files.get(0))).thenReturn(comments);
        when(parser.getGuidelineIdsFromComment(comments.get(0))).thenReturn(new HashSet<String>(Arrays.asList("Req1", "Req2")));

        parser.parseSourceFiles(files);
        
        assertEquals(0, parser.getErrorCode());
    }
    
    @Test
    public void testLogFile() {
        List<Guideline> guidelines = someOtherGuidelines();
        guidelines.get(0).setCategory(Category.REQUIRED);
        guidelines.get(0).setReCategorization(Category.MANDATORY);
        guidelines.get(1).setCategory(Category.ADVISORY);
        guidelines.add(new Guideline("Rule 2.2", "REQUIRED"));        
        List<String> files = Arrays.asList(resourceFileName());
        List<CommentProperties> comments = new ArrayList<CommentProperties>();
        //Test violation
        addSuppression(comments, "hus 1.2");
        comments.get(0).fileName = "file1.c";
        comments.get(0).lineNumber = 20;
        //Test deviation
        addSuppression(comments, "hus 1.2");
        comments.get(1).fileName = "file2.c";
        comments.get(1).lineNumber = 30;
        comments.get(1).suppressions.get("hus 1.2").isDeviation = true;
        //Test false positive
        addSuppression(comments, "Jau 1.1");
        comments.get(2).fileName = "file3.c";
        comments.get(2).lineNumber = 5;
        comments.get(2).suppressions.get("Jau 1.1").isFalsePositive = true;
        //test that one comment can have multiple suppressions
        comments.get(2).suppressions.put("hus 1.2", new Suppression());
        comments.get(2).suppressions.get("hus 1.2").guidelineId = "hus 1.2";
        //Test non-MISRA
        comments.add(new CommentProperties());
        comments.get(3).isNonMisra = true;
        comments.get(3).fileName = "file4.c";
        comments.get(3).lineNumber = 0;
        comments.get(3).suppressions = new HashMap<String, Suppression>(0);//Empty
        //Test disapplied (should be ignored)
        guidelines.add(new Guideline("Rule 2.1", Category.ADVISORY, Category.DISAPPLIED, ""));
        addSuppression(comments, "Rule 2.1");
        //Test Non-Misra tagged, but appears to be MISRA-related
        addSuppression(comments, "Jau 1.1");
        comments.get(5).isNonMisra = true;
        comments.get(5).fileName = "file5.c";
        comments.get(5).lineNumber = 22;
        //Add another one, to see that all are listed
        comments.get(5).suppressions.put("hus 1.2", new Suppression());
        comments.get(5).suppressions.get("hus 1.2").guidelineId = "hus 1.2";
        //Violation of mandatory should be error
        addSuppression(comments, "Jau 1.1");
        comments.get(6).fileName = "file6.c";
        comments.get(6).lineNumber = 1;
        //Violation of required should be error
        addSuppression(comments, "Rule 2.2");
        comments.get(7).fileName = "file7.c";
        comments.get(7).lineNumber = 2;
                
        
        WarningParser parser = getParserWithMockedCallToParseSourceFile(files, guidelines, comments);
        when(parser.name()).thenReturn("MyCoolParser");
        parser.setLogFilePath("log.txt");

        List<String> report = null;
        try {           
            parser.parseSourceFiles(files);
            report = Files.readAllLines(Paths.get("log.txt"));
        }
        catch (IOException e) {
            fail("Unexpected IOException: " + e.getMessage());
        }
        assertEquals(8, report.size());
        assertEquals("file1.c:20: info: Violation of hus 1.2 (Advisory)", report.get(0));
        assertEquals("file2.c:30: info: Deviation of hus 1.2 (Advisory)", report.get(1));
        assertEquals("file3.c:5: info: Suppression of Jau 1.1 (Mandatory) tagged as false positive", report.get(2));
        assertEquals("file3.c:5: info: Violation of hus 1.2 (Advisory)", report.get(3));
        assertEquals("file4.c:0: info: Tool suppression comment tagged as not MISRA relevant", report.get(4));
        assertEquals("file5.c:22: warning: Tool suppression comment tagged as not MISRA relevant, but MyCoolParser indicates that this comment suppresses Jau 1.1 and hus 1.2", report.get(5));
        assertEquals("file6.c:1: error: Violation of Jau 1.1 (Mandatory)", report.get(6));
        assertEquals("file7.c:2: error: Violation of Rule 2.2 (Required)", report.get(7));
        
    }
}
