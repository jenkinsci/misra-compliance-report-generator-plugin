package rortveiten.misra;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Class for finding line numbers of text patterns contained within a longer text, typically the contents of a file */
public class LineNumberFinder {
    
    private int[] charNumberAtLineBreak;
    private Matcher matcher;
    
    public LineNumberFinder(String fullText) {
        fillCharNumberAtLineBreak(fullText);
        matcher = Pattern.compile("").matcher(fullText);
    }
    
    private void fillCharNumberAtLineBreak(String text) {
        int n = numberOfEntries(text, '\n') + 1;
        charNumberAtLineBreak = new int[n];
        int count = 0;
        for (int charNo = 0; charNo < text.length(); charNo++) {
            if (text.charAt(charNo) == '\n') {
                charNumberAtLineBreak[count] = charNo;
                count++;
            }     
        }
        charNumberAtLineBreak[n - 1] = text.length();
    }

    private static int numberOfEntries(String string, char match) {
        int ret = 0;
        for (char c : string.toCharArray()) {
            if (c == match)
                ret++;
        }
        return ret;
    }

    public int findNext(Pattern p) {
        matcher.usePattern(p);
        if (!matcher.find())
            return -1;        
        return lineNumberAtCharNo(matcher.start());
    }
    
    public int findNext(String text) {
        Pattern p = Pattern.compile(text, Pattern.LITERAL);
        return findNext(p);
    }

    private int lineNumberAtCharNo(int start) {
        for (int i = 0; i < charNumberAtLineBreak.length; i++) {
            if (start <= charNumberAtLineBreak[i])
                return i + 1;
        }
        return -1;
    }
    
    /** Returns the regex matcher so you can examine the previous match
     * 
     * @return the regex matcher used to find the previous text
     */
    public Matcher getMatcher() {
        return matcher;
    }
}
