package org.faudroids.mrhyde.ui.tree;

import org.faudroids.mrhyde.ui.tree.AbstractNode;

import java.util.HashMap;
import java.util.Map;

public final class DirNode extends AbstractNode {

	private final Map<String, AbstractNode> entries = new HashMap<>();

	public DirNode(AbstractNode parent, String path) {
		super(parent, path);
	}


	public Map<String, AbstractNode> getEntries() {
		return entries;
	}

}