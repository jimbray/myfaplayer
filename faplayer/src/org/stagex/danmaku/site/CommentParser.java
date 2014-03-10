package org.stagex.danmaku.site;

import java.util.ArrayList;

import org.stagex.danmaku.comment.Comment;

public abstract class CommentParser {

	public abstract ArrayList<Comment> parse(String file);

}
