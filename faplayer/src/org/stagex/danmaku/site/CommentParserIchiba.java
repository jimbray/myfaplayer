package org.stagex.danmaku.site;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.stagex.danmaku.comment.Comment;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class CommentParserIchiba extends CommentParser {

	@Override
	public ArrayList<Comment> parse(String uri) {
		ArrayList<Comment> result = new ArrayList<Comment>();
		try {
			if (uri.startsWith("file://")) {
				uri = uri.substring(7);
			} else {
				return null;
			}
			InputStream fin = new FileInputStream(uri);
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser parser = factory.newPullParser();
			parser.setInput(fin, null);
			int commentTime = -1;
			int commentType = -1;
			int commentSize = -1;
			int commentColor = -1;
			String commentText = null;
			int currentDepth = 0;
			String tagName = null;
			for (int eventType = parser.getEventType(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser
					.next()) {
				if (eventType == XmlPullParser.START_DOCUMENT) {
					continue;
				}
				if (eventType == XmlPullParser.START_TAG) {
					currentDepth += 1;
					tagName = parser.getName();
					if (currentDepth == 1 && tagName.compareTo("comments") != 0) {
						break;
					}
					if (currentDepth == 3) {
						if (tagName.compareTo("text") == 0) {
							int count = parser.getAttributeCount();
							for (int i = 0; i < count; i++) {
								String name = parser.getAttributeName(i);
								String value = parser.getAttributeValue(i);
								if (name.compareTo("size") == 0) {
									commentSize = Integer.parseInt(value);
									continue;
								}
								if (name.compareTo("color") == 0) {
									commentColor = Integer.parseInt(value);
									continue;
								}
								if (name.compareTo("type") == 0) {
									commentType = Integer.parseInt(value);
									continue;
								}
							}
						}
					}
					continue;
				}
				if (eventType == XmlPullParser.END_TAG) {
					currentDepth -= 1;
					if (currentDepth == 1) {
						Comment comment = new Comment();
						comment.site = Comment.SITE_ICHIBA;
						comment.time = commentTime;
						comment.type = commentType;
						comment.size = commentSize;
						comment.color = commentColor;
						comment.text = commentText;
						result.add(comment);
					}
					continue;
				}
				if (eventType == XmlPullParser.TEXT) {
					if (currentDepth == 3) {
						if (tagName.compareTo("time") == 0) {
							commentTime = (int) (Float.parseFloat(parser
									.getText()) * 1000);
						}
						if (tagName.compareTo("text") == 0) {
							commentText = parser.getText();
						}
					}
					continue;
				}
			}
			fin.close();
		} catch (Exception e) {

		}
		return result.size() > 0 ? result : null;
	}
}
