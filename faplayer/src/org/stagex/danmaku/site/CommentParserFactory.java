package org.stagex.danmaku.site;

import java.util.ArrayList;

import org.stagex.danmaku.comment.Comment;

public class CommentParserFactory {

	public static String[] getParserList() {
		return new String[] { "Acfun", "Bilibili", "Ichiba", "Nico" };
	}

	@SuppressWarnings("rawtypes")
	public static CommentParser createParser(String name) {
		CommentParser parser = null;
		try {
			String cln = CommentParser.class.getName() + name;
			Class clz = Class.forName(cln);
			parser = (CommentParser) clz.newInstance();
		} catch (Exception e) {
		}
		return parser;
	}

	public static ArrayList<Comment> parse(String file) {
		ArrayList<Comment> result = null;
		String[] list = CommentParserFactory.getParserList();
		for (String name : list) {
			CommentParser parser = CommentParserFactory.createParser(name);
			if (parser == null)
				continue;
			result = parser.parse(file);
			if (result != null)
				break;
		}
		return result;
	}

}
