package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.regex.Pattern;


public class PatternMatcher {
	

	/**
	 * Returns true if the rule matches the title
	 * 
	 * @param rule 	a rule to which the given title is matched
	 * @param title	a title to which the rule is applied
	 * @return 		true if the rule matches the title otherwise false
	 */
	private StringBuilder sb;
	
	public boolean match(String rule, String title) {
		Pattern pattern = Pattern.compile(generateRegEx(rule), Pattern.CASE_INSENSITIVE);
		return pattern.matcher(title).find();
	}

	
	/**
	 * Returns the regex pattern of the given rule
	 * 	; is replace by |
	 * 	* is replaced by \\w+
	 * 	+ is replaced by ^ to avoid conflicts
	 *  elements linked by Plus will be transformed: a+b ->(?=.*a)(?=.*b)
	 *  to make sure only whole words are matched by default the elements will be surrounded with \\b
	 * @param rule	the rule that will be converted into a regex pattern
	 * @return 		regex pattern
	 */
	private String generateRegEx(String rule) {
		rule = rule.replace(";", "|").replace("+", "^").replace("*", "\\w*");
		if(rule.endsWith("|"))
			rule = rule.substring(0,rule.length()-1);
		
		int previousPosition = -1;
		
		// Disassembly of the rule, could be done more efficiently
		ArrayList<String> elements = new ArrayList<String>();
		ArrayList<Boolean> operators = new ArrayList<Boolean>();
		for (int i = 0; i < rule.length(); i++) {
			switch (rule.charAt(i)) {
			case '^':
				operators.add(false);
				elements.add(rule.substring(previousPosition + 1, i));
				previousPosition = i;
				break;
			case '|':
				operators.add(true);
				elements.add(rule.substring(previousPosition + 1, i));
				previousPosition = i;
				break;
			}
		}
		elements.add(rule.substring(previousPosition + 1, rule.length()));
		
		sb = new StringBuilder();
		for (int i = 0; i < operators.size(); i++) {
			if (!operators.get(i)) {
				addPositiveLookahead(elements.get(i));
			} else {
				if (i == 0 || operators.get(i - 1)) {
					addWordBoundaries (elements.get(i));
				} else {
					addPositiveLookahead(elements.get(i));
				}
				sb.append("|");
			}
		}
		
		if (operators.size()>=1 && !operators.get(operators.size() - 1)) {
			addPositiveLookahead(elements.get(elements.size() - 1));
		} else {
			addWordBoundaries (elements.get(elements.size() - 1));
		}
		return sb.toString();
	}
	
	
	/**
	 * helper method that adds the word boundaries to an element
	 * @param element element that will be surrounded with \\b
	 */
	private void addWordBoundaries (String element) {
		sb.append("\\b").append(element).append("\\b");
	}
	
	/**
	 * helper method that creates a positive lookahead regex element with word boundaries 
	 * @param element element that will be surrounded with regex fragments for positive lookahead
	 */
	private void addPositiveLookahead (String element) {
		sb.append("(?=.*");
		addWordBoundaries (element);
		sb.append(")");
	}

}
