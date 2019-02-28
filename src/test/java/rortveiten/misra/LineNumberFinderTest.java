package rortveiten.misra;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class LineNumberFinderTest {

    private final String testText = "This is a test text\n"
            + "which contains SOMETHING good\n"
            + "and has various kinds of SOMETHING line breaks \r\n"
            + "but even so this works (?)\n"
            + "potater\n"
            + "Q";
  
    @Test
    public void findsFirstInstance() {    
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("SOMETHING");
        
        assertEquals(2, lineNo);
    }
    
    @Test 
    public void findsEntryOnLastLine() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("Q");
        
        assertEquals(6, lineNo);
    }
    
    @Test
    public void findsEntryOnFirstLine() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("xt");
        
        assertEquals(1, lineNo);
    }
    
    @Test
    public void findsMultiLineEntry() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext(")\npotater");
        
        assertEquals(4, lineNo);
    }
    
    @Test
    public void findsRegex() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext(Pattern.compile("\\(.*\\)"));
        
        assertEquals(4, lineNo);
    }
    
    @Test
    public void returnsMinusOneIfNotFound() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("PATATA");
        
        assertEquals(-1, lineNo);       
    }
    
    @Test
    public void lineBreakIsOnSameLineAsText() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("\n");
        
        assertEquals(1, lineNo);
    }
    
    @Test
    public void findsFirstChar() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        int lineNo = lnf.findNext("T");
        
        assertEquals(1, lineNo);
    }
    
    @Test
    public void skipsMatchesWeHaveAlreadyPassed() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        lnf.findNext("good"); //Should skip past the first SOMETHING
        int lineNo = lnf.findNext("SOMETHING");
        
        assertEquals(3, lineNo);
    }
    
    @Test
    public void previousMatchesDoNotAffectNext() {
        LineNumberFinder lnf = new LineNumberFinder(testText);
        
        assertEquals(1, lnf.findNext("\n"));
        assertEquals(2, lnf.findNext("\n"));
        assertEquals(4, lnf.findNext("but"));
        assertEquals(4, lnf.findNext("\n"));
        assertEquals(5, lnf.findNext("p"));
        assertEquals(6, lnf.findNext("Q"));
        assertEquals(-1, lnf.findNext("\n"));
        
    }

}
