package com.crawljax.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Utility class that contains methods used by Crawljax and some plugin to deal with XPath
 * resolving, constructing etc.
 */
public final class XPathHelper {

	private static final Pattern TAG_PATTERN = Pattern
	        .compile("(?<=[/|::])[a-zA-z]+(?=([/|\\[]|$))");

	private static final Pattern ID_PATTERN = Pattern.compile("(@[a-zA-Z]+)");

	private static final String FULL_XPATH_CACHE = "FULL_XPATH_CACHE";
	private static final int MAX_SEARCH_LOOPS = 10000;

	/**
	 * Reverse Engineers an XPath Expression of a given Node in the DOM.
	 * 
	 * @param node
	 *            the given node.
	 * @return string xpath expression (e.g., "/html[1]/body[1]/div[3]").
	 */
	public static String getXPathExpression(Node node) {
		Object xpathCache = node.getUserData(FULL_XPATH_CACHE);
		if (xpathCache != null) {
			return xpathCache.toString();
		}
		Node parent = node.getParentNode();

		if ((parent == null) || parent.getNodeName().contains("#document")) {
			String xPath = "/" + node.getNodeName() + "[1]";
			node.setUserData(FULL_XPATH_CACHE, xPath, null);
			return xPath;
		}

		StringBuffer buffer = new StringBuffer();

		if (parent != node) {
			buffer.append(getXPathExpression(parent));
			buffer.append("/");
		}

		buffer.append(node.getNodeName());

		List<Node> mySiblings = getSiblings(parent, node);

		for (int i = 0; i < mySiblings.size(); i++) {
			Node el = mySiblings.get(i);

			if (el.equals(node)) {
				buffer.append('[').append(Integer.toString(i + 1)).append(']');
				// Found so break;
				break;
			}
		}
		String xPath = buffer.toString();
		node.setUserData(FULL_XPATH_CACHE, xPath, null);
		return xPath;
	}

	/**
	 * Get siblings of the same type as element from parent.
	 * 
	 * @param parent
	 *            parent node.
	 * @param element
	 *            element.
	 * @return List of sibling (from element) under parent
	 */
	public static List<Node> getSiblings(Node parent, Node element) {
		List<Node> result = new ArrayList<Node>();
		NodeList list = parent.getChildNodes();

		for (int i = 0; i < list.getLength(); i++) {
			Node el = list.item(i);

			if (el.getNodeName().equals(element.getNodeName())) {
				result.add(el);
			}
		}

		return result;
	}

	/**
	 * Returns the list of nodes which match the expression xpathExpr in the String domStr.
	 * 
	 * @return the list of nodes which match the query
	 * @throws XPathExpressionException
	 * @throws IOException
	 */
	public static NodeList evaluateXpathExpression(String domStr, String xpathExpr)
	        throws XPathExpressionException, IOException {
		Document dom = DomUtils.asDocument(domStr);
		return evaluateXpathExpression(dom, xpathExpr);
	}

	/**
	 * Returns the list of nodes which match the expression xpathExpr in the Document dom.
	 * 
	 * @param dom
	 *            the Document to search in
	 * @param xpathExpr
	 *            the xpath query
	 * @return the list of nodes which match the query
	 * @throws XPathExpressionException
	 *             On error.
	 */
	public static NodeList evaluateXpathExpression(Document dom, String xpathExpr)
	        throws XPathExpressionException {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile(xpathExpr);
		Object result = expr.evaluate(dom, XPathConstants.NODESET);
		NodeList nodes = (NodeList) result;
		return nodes;
	}

	/**
	 * Returns the XPaths of all nodes retrieved by xpathExpression. Example: //DIV[@id='foo']
	 * returns /HTM[1]/BODY[1]/DIV[2]
	 * 
	 * @param dom
	 *            The dom.
	 * @param xpathExpression
	 *            The expression to find the element.
	 * @return list of XPaths retrieved by xpathExpression.
	 * @throws XPathExpressionException
	 */
	public static ImmutableList<String> getXpathForXPathExpressions(Document dom,
	        String xpathExpression) throws XPathExpressionException {
		NodeList nodeList = XPathHelper.evaluateXpathExpression(dom, xpathExpression);
		Builder<String> result = ImmutableList.builder();
		if (nodeList.getLength() > 0) {
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node n = nodeList.item(i);
				result.add(getXPathExpression(n));
			}
		}
		return result.build();
	}

	/**
	 * @param xpath
	 *            The xpath to format.
	 * @return formatted xpath with tag names in uppercase and attributes in lowercase
	 */
	public static String formatXPath(String xpath) {
		String formatted = capitalizeTagNames(xpath);
		formatted = lowerCaseAttributes(formatted);
		return formatted;
	}

	private static String lowerCaseAttributes(String formatted) {
		Matcher m = ID_PATTERN.matcher(formatted);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String text = m.group();
			m.appendReplacement(sb, Matcher.quoteReplacement(text.toLowerCase()));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String capitalizeTagNames(String xpath) {
		Matcher m = TAG_PATTERN.matcher(xpath);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String text = m.group();
			m.appendReplacement(sb, Matcher.quoteReplacement(text.toUpperCase()));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * @param xpath
	 *            The xpath expression to find the last element of.
	 * @return returns the last element in the xpath expression
	 */
	public static String getLastElementXPath(String xpath) {
		String[] elements = xpath.split("/");
		for (int i = elements.length - 1; i >= 0; i--) {
			if (!elements[i].equals("") && elements[i].indexOf("()") == -1
			        && !elements[i].startsWith("@")) {
				return stripEndSquareBrackets(elements[i]);
			}
		}
		return "";
	}

	/**
	 * @param string
	 * @return string without the before [
	 */
	private static String stripEndSquareBrackets(String string) {
		if (string.contains("[")) {
			return string.substring(0, string.indexOf('['));
		} else {
			return string;
		}
	}

	/**
	 * returns position of xpath element which match the expression xpath in the String dom.
	 * 
	 * @param dom
	 *            the Document to search in
	 * @param xpath
	 *            the xpath query
	 * @return position of xpath element, if fails returns -1
	 **/
	public static int getXPathLocation(String dom, String xpath) {
		String dom_lower = dom.toLowerCase();
		String xpath_lower = xpath.toLowerCase();
		String[] elements = xpath_lower.split("/");
		int pos = 0;
		int temp;
		int number;
		for (String element : elements) {
			if (!element.isEmpty() && !element.startsWith("@") && !element.contains("()")) {
				if (element.contains("[")) {
					try {
						number =
						        Integer.parseInt(element.substring(element.indexOf("[") + 1,
						                element.indexOf("]")));
					} catch (NumberFormatException e) {
						return -1;
					}
				} else {
					number = 1;
				}
				for (int i = 0; i < number; i++) {
					// find new open element
					temp = dom_lower.indexOf("<" + stripEndSquareBrackets(element), pos);

					if (temp > -1) {
						pos = temp + 1;

						// if depth>1 then goto end of current element
						if (number > 1 && i < number - 1) {
							pos =
							        getCloseElementLocation(dom_lower, pos,
							                stripEndSquareBrackets(element));
						}

					}
				}
			}
		}
		return pos - 1;
	}

	/**
	 * @param dom
	 *            The dom string.
	 * @param pos
	 *            Position where to start searching.
	 * @param element
	 *            The element.
	 * @return the position where the close element is
	 */
	public static int getCloseElementLocation(String dom, int pos, String element) {
		String[] elements = { "LINK", "META", "INPUT", "BR" };
		List<String> singleElements = Arrays.asList(elements);
		if (singleElements.contains(element.toUpperCase())) {
			return dom.indexOf('>', pos) + 1;
		}
		// make sure not before the node
		int openElements = 1;
		int i = 0;
		int position = pos;
		String dom_lower = dom.toLowerCase();
		String element_lower = element.toLowerCase();
		String openElement = "<" + element_lower;
		String closeElement = "</" + element_lower;
		while (i < MAX_SEARCH_LOOPS) {
			if (dom_lower.indexOf(openElement, position) == -1
			        && dom_lower.indexOf(closeElement, position) == -1) {
				return -1;
			}
			if (dom_lower.indexOf(openElement, position) < dom_lower.indexOf(closeElement,
			        position)
			        && dom_lower.indexOf(openElement, position) != -1) {
				openElements++;
				position = dom_lower.indexOf(openElement, position) + 1;
			} else {

				openElements--;
				position = dom_lower.indexOf(closeElement, position) + 1;
			}
			if (openElements == 0) {
				break;
			}
			i++;
		}
		return position - 1;

	}

	/**
	 * @param dom
	 *            The dom.
	 * @param xpath
	 *            The xpath expression.
	 * @return the position where the close element is
	 */
	public static int getCloseElementLocation(String dom, String xpath) {
		return getCloseElementLocation(dom, getXPathLocation(dom, xpath) + 1,
		        getLastElementXPath(xpath));
	}

	/**
	 * @param xpath
	 *            The xpath expression.
	 * @return the xpath expression for only the element location. Leaves out the attributes and
	 *         text()
	 */
	public static String stripXPathToElement(String xpath) {
		String xpathStripped = xpath;

		if (!Strings.isNullOrEmpty(xpathStripped)) {
			if (xpathStripped.toLowerCase().contains("/text()")) {
				xpathStripped =
				        xpathStripped
				                .substring(0, xpathStripped.toLowerCase().indexOf("/text()"));
			}
			if (xpathStripped.toLowerCase().contains("/comment()")) {
				xpathStripped =
				        xpathStripped.substring(0,
				                xpathStripped.toLowerCase().indexOf("/comment()"));
			}
			if (xpathStripped.contains("@")) {
				xpathStripped = xpathStripped.substring(0, xpathStripped.indexOf("@") - 1);
			}
		}

		return xpathStripped;
	}

	private XPathHelper() {
	}
	
	public static void main(String[] args) throws XPathExpressionException, IOException
	{
		String xpath = "<HTML xmlns='http://www.w3.org/1999/xhtml'><INPUT type='Button'><HEAD><META http-equiv='Content-Type' content='text/html; charset=UTF-8'><META content='text/html; charset=utf-8' http-equiv='Content-Type'><META content='width=device-width, initial-scale=1.0' name='viewport'><LINK href='/resources/css/lib/bootstrap.min.css' rel='stylesheet'><LINK href='/resources/css/lib/font-awesome.min.css' rel='stylesheet'><LINK href='/resources/css/lib/toastr.min.css' rel='stylesheet'><LINK href='/resources/css/lib/jquery.dataTables.css' rel='stylesheet'><LINK href='/resources/css/lib/token-input.css' rel='stylesheet'><LINK href='/resources/css/lib/tagmanager.css' rel='stylesheet'><LINK href='/resources/css/home.css' rel='stylesheet'><LINK href='/resources/css/createCname.css' rel='stylesheet'><TITLE>Altus Home</TITLE><LINK href='https://www.paashead.stratus.ebay.com/static/resources/css/lib/font-awesome.min.css' rel='stylesheet' type='text/css'><LINK href='https://www.paashead.stratus.ebay.com/static/resources/css/sharedheader.css' rel='stylesheet' type='text/css'></HEAD><BODY><LINK href='/resources/css/styles.css' rel='stylesheet'><LINK href='/favicon.ico' rel='icon' type='image/ico'><DIV id='header'><DIV class='navbar transparent navbar-inverse sharedheader'><DIV class='navbar-inner'><A class='brand logo' href='https://app.altus.vip.ebay.com/app'><IMG src='https://www.paashead.stratus.ebay.com/static/resources/img/logo/altus_small_color.png'>Altus</A><DIV class='nav-collapse collapse'><UL class='nav'></UL></DIV><UL class='nav pull-right'><LI><A class='active' href='https://app.altus.vip.ebay.com/app'><I class='icon-home icon-white'></I>My Altus</A></LI><LI class='dropdown'><A class='dropdown-toggle' data-toggle='dropdown' href='#' id='Tools'><I class='icon-hdd icon-white'></I>Tools<SPAN class='caret'></SPAN></A><UL class='dropdown-menu actcallback'><LI><A href='https://app.altus.vip.ebay.com/createci'>Create CI</A></LI><LI><A href='https://app.altus.vip.ebay.com/v3featurepools'>View/Create V3 Feature Pool</A></LI></UL></LI><LI class='dropdown'><A class='dropdown-toggle' data-toggle='dropdown' href='#'><I class='icon-question-sign icon-white' style='background:transparent;'></I>Help<SPAN class='caret'></SPAN></A><UL class='dropdown-menu'><LI><A href='https://wiki.vip.corp.ebay.com/display/GPAAS/Altus+for+eBay' target='_blank'>Documentation</A></LI><LI><A href='https://jirap.corp.ebay.com/browse/GPS' target='_blank'>Report an Issue</A></LI></UL></LI><LI class='dropdown ' id='user-dropdown' style='display: block;'><A class='dropdown-toggle' data-toggle='dropdown' href='#'><I class='icon-user icon-white'></I>yunxu<SPAN class='caret'></SPAN></A><UL class='dropdown-menu'><LI><A href='#' onclick='SharedHeader.logout();'>Logout</A></LI></UL></LI></UL></DIV></DIV></DIV><DIV class='alert alert-warning' id='banner' role='alert' style='font-weight:bold;text-align:center;display:none;'><UL class='list-unstyled' id='banner-list'></UL></DIV><DIV class='container searchbar'><DIV class='input-append'><INPUT autocomplete='off' disabled id='search' placeholder='Loading Data...' type='text'><I class='fa fa-search'></I></DIV><A class='btn btn-primary pull-right' href='/appStack' id='create-app-btn' style='border-radius: 1px; '><I class='fa fa-plus'></I>Create Application</A></DIV><DIV class='container'><SECTION id='applications'><DIV class='headline expanded' id='addFavApps'><H3><SPAN class='glyphicon glyphicon-collapse-down togglebtn' data-action='addFavApps' data-target='#fav_applications'></SPAN><SPAN class='fa fa-th-list'></SPAN><SPAN>Favorite Applications</SPAN><SPAN class='badge badge-number' id='applicationsCount'>0</SPAN><SPAN class='pull-right favoriteFilter'><A class='addToFav' data-action='applications' data-target='#add_favorite_applications_modal' data-toggle='modal' href='#'>Add Favorite Applications</A></SPAN></H3></DIV><DIV class='datatable' id='fav_applications'><I class='fa fa-spinner fa-spin'></I></DIV></SECTION><SECTION id='cis'><DIV class='headline expanded' id='addFavCis'><H3><SPAN class='glyphicon glyphicon-collapse-down togglebtn' data-action='addFavCis' data-target='#fav_cis'></SPAN><IMG height='18px' src='/resources/img/logo/jenkins_logo.png' style='margin-right:3px;vertical-align: baseline;' width='18px'><SPAN>Favorite CIs</SPAN><SPAN class='badge badge-number' id='cisCount'>0</SPAN><SPAN class='pull-right favoriteFilter'><A class='addToFav' data-action='cis' data-target='#add_favorite_cis_modal' data-toggle='modal' href='#'>Add Favorite CIs</A></SPAN></H3></DIV><DIV class='datatable' id='fav_cis'><I class='fa fa-spinner fa-spin'></I></DIV></SECTION><SECTION id='pools'><DIV class='headline expanded' id='addFavPools'><H3><SPAN class='glyphicon glyphicon-collapse-down togglebtn' data-action='addFavPools' data-target='#fav_pools'></SPAN><SPAN class='fa fa-sitemap'></SPAN><SPAN>Favorite Pools</SPAN><SPAN class='badge badge-number' id='poolsCount'>0</SPAN><SPAN class='pull-right favoriteFilter'><A class='addToFav' data-action='pools' data-target='#add_favorite_pools_modal' data-toggle='modal' href='#'>Add Favorite Pools</A></SPAN></H3></DIV><DIV class='datatable' id='fav_pools'><I class='fa fa-spinner fa-spin'></I></DIV></SECTION></DIV><!-- starts footer --><DIV id='footer' style='margin-top:30px;'></DIV></BODY></HTML>";
		
		NodeList list = XPathHelper.evaluateXpathExpression(xpath, "//INPUT[translate(@type, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')='BUTTON']");
		NodeList list1 = XPathHelper.evaluateXpathExpression(xpath, "//INPUT[@type='BUTTON']");
		
		System.out.println(list);
	}

}
