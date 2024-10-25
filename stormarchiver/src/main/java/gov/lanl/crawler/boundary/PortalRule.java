package gov.lanl.crawler.boundary;

import java.util.regex.Pattern;

public class PortalRule {
	public Pattern pattern;
	public boolean sign;
	public String regex;

	public PortalRule(boolean sign, String regex) {
		// super(sign, regex);
		this.sign = sign;
		this.regex = regex;
		this.pattern = Pattern.compile(regex);
	}

	/**
	 * Return if this rule is used for filtering-in or out.
	 * 
	 * @return <code>true</code> if any url matching this rule must be accepted,
	 *         otherwise <code>false</code>.
	 */

	public boolean accept() {
		return sign;
	}

	public boolean match(String url) {
		return pattern.matcher(url).find();
	}

}
