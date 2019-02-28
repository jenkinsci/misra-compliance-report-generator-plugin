package rortveiten.misra;

import org.junit.Test;

import rortveiten.misra.Guideline.Category;

import static org.junit.Assert.assertEquals;


public class GuidelineTest {

	@Test
	public void testComplianceLevelFromString()
	{
		assertEquals(Category.MANDATORY, Category.fromString("mAndATory"));
		assertEquals(Category.REQUIRED, Category.fromString("ReQUIred"));
		assertEquals(Category.ADVISORY, Category.fromString("advisory"));
		assertEquals(Category.DISAPPLIED, Category.fromString("Disapplied"));
		assertEquals(Category.UNKNOWN, Category.fromString("Where is MH370?"));
	}
	
	@Test
	public void testComplianceLevelToString()
	{
		assertEquals("Mandatory", Category.MANDATORY.toString());
		assertEquals("Required", Category.REQUIRED.toString());
		assertEquals("Advisory", Category.ADVISORY.toString());
		assertEquals("Disapplied", Category.DISAPPLIED.toString());
		assertEquals("Unknown", Category.UNKNOWN.toString());
	}
	
	@Test
	public void testRequirementToString()
	{
		Guideline r = new Guideline("Directive 1.1");
		assertEquals("Directive 1.1", r.toString());
	}
	
	@Test
	public void activeComplianceLevelIsSameAsComplianceLevelWhenNotRecategorized() {
		Guideline g = new Guideline("hei", "Advisory");
		assertEquals(Category.ADVISORY, g.activeCategory());
		g.setCategory("Required");
		assertEquals(Category.REQUIRED, g.activeCategory());
		g.setCategory("MANDATORY");
		assertEquals(Category.MANDATORY, g.activeCategory());
	}
	
	@Test
	public void activeComplianceLevelIsSameAsRecategorized() {
		Guideline g = new Guideline("hei", "Advisory");
		g.setReCategorization("Required");
		assertEquals(Category.REQUIRED, g.activeCategory());
		g.setReCategorization("Mandatory");
		assertEquals(Category.MANDATORY, g.activeCategory());
		g.setCategory("Required");
		assertEquals(Category.MANDATORY, g.activeCategory());
	}
}
