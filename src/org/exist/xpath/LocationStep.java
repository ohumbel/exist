/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.dom.XMLUtil;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.w3c.dom.Node;

public class LocationStep extends Step {

	protected static Logger LOG = Logger.getLogger(LocationStep.class);
	protected NodeSet buf = null;
	protected boolean keepVirtual = false;

	public LocationStep(int axis) {
		super(axis);
	}

	public LocationStep(int axis, NodeTest test) {
		super(axis, test);
	}

	protected NodeSet applyPredicate(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet)
		throws XPathException {
		Predicate pred;
		NodeSet result = contextSet;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result =
				(NodeSet) pred.eval(context, documents, (Sequence)result);
		}
		return result;
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet documents,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		NodeSet temp;
		switch (axis) {
			case Constants.DESCENDANT_AXIS :
			case Constants.DESCENDANT_SELF_AXIS :
				temp = getDescendants(context, documents, (NodeSet)contextSequence);
				break;
			case Constants.CHILD_AXIS :
				temp = getChildren(context, documents, (NodeSet)contextSequence);
				break;
			case Constants.ANCESTOR_AXIS :
			case Constants.ANCESTOR_SELF_AXIS :
				temp = getAncestors(context, documents, (NodeSet)contextSequence);
				break;
			case Constants.SELF_AXIS :
				temp = (NodeSet)contextSequence;
				if (inPredicate) {
					if (temp instanceof VirtualNodeSet) {
						((VirtualNodeSet) temp).setInPredicate(true);
						((VirtualNodeSet) temp).setSelfIsContext();
					} else {
						NodeProxy p;
						for (Iterator i = temp.iterator(); i.hasNext();) {
							p = (NodeProxy) i.next();
							p.addContextNode(p);
						}
					}
				}
				break;
			case Constants.PARENT_AXIS :
				temp = getParents(context, documents, (NodeSet)contextSequence);
				break;
			case Constants.ATTRIBUTE_AXIS :
				// combines /descendant-or-self::node()/attribute:*
			case Constants.DESCENDANT_ATTRIBUTE_AXIS :
				temp = getAttributes(context, documents, (NodeSet)contextSequence);
				break;
			case Constants.PRECEDING_SIBLING_AXIS :
			case Constants.FOLLOWING_SIBLING_AXIS :
				temp = getSiblings(context, documents, (NodeSet)contextSequence);
				break;
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
		temp =
			(predicates.size() == 0)
				? temp
				: applyPredicate(context, documents, temp);
		return temp;
	}

	protected NodeSet getAttributes(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		NodeSet result;
		if (test.isWildcardTest()) {
			result = new VirtualNodeSet(axis, test, contextSet);
			((VirtualNodeSet) result).setInPredicate(inPredicate);
		} else {
			if (buf == null) {
				buf =
					(NodeSet) context.getBroker().getAttributesByName(
						documents,
						test.getName());
			}
			if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)
				result =
					buf.getDescendants(
						contextSet,
						ArraySet.DESCENDANT,
						inPredicate);
			else
				result =
					buf.getChildren(
						contextSet,
						ArraySet.DESCENDANT,
						inPredicate);

			LOG.debug("found " + result.getLength() + " attributes");
			//		} else {
			//				Node n;
			//				Node attr;
			//				NamedNodeMap map;
			//				result = new ArraySet(contextSet.getLength());
			//				for (int i = 0; i < contextSet.getLength(); i++) {
			//					n = contextSet.item(i);
			//					if (n.getNodeType() == Node.ELEMENT_NODE) {
			//						map = ((Element) n).getAttributes();
			//						for (int j = 0; j < map.getLength(); j++) {
			//							attr = map.item(j);
			//							result.add(attr);
			//						}
			//					}
			//				}
		}
		return result;
	}

	protected NodeSet getChildren(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (test.isWildcardTest()) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else {
			if (buf == null) {
				buf =
					(NodeSet) context.getBroker().findElementsByTagName(
						documents,
						test.getName());
			}
			return buf.getChildren(
				contextSet,
				ArraySet.DESCENDANT,
				inPredicate);
		}
	}

	protected NodeSet getDescendants(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (!test.isWildcardTest()) {
			if (buf == null) {
				buf =
					(NodeSet) context.getBroker().findElementsByTagName(
						documents,
						test.getName());
			}
			return buf.getDescendants(
				contextSet,
				ArraySet.DESCENDANT,
				axis == Constants.DESCENDANT_SELF_AXIS,
				inPredicate);
		} else {
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		}
	}

	protected NodeSet getSiblings(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (!test.isWildcardTest()) {
			if (buf == null) {
				buf =
					(NodeSet) context.getBroker().findElementsByTagName(
						documents,
						test.getName());
			}
			return contextSet.getSiblings(
				buf,
				axis == Constants.PRECEDING_SIBLING_AXIS
					? NodeSet.PRECEDING
					: NodeSet.FOLLOWING);
		} else {
			ArraySet result = new ArraySet(contextSet.getLength());
			NodeProxy p;
			NodeImpl n;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				n = (NodeImpl) p.getNode();
				while ((n = getNextSibling(n)) != null) {
					if (test.matches(n))
						result.add(
							new NodeProxy(
								(DocumentImpl) n.getOwnerDocument(),
								n.getGID(),
								n.getInternalAddress()));
				}
			}
			return result;
		}
	}

	protected NodeImpl getNextSibling(NodeImpl last) {
		switch (axis) {
			case Constants.FOLLOWING_SIBLING_AXIS :
				return (NodeImpl) last.getNextSibling();
			default :
				return (NodeImpl) last.getPreviousSibling();
		}
	}

	protected NodeSet getAncestors(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (!test.isWildcardTest()) {
			if (buf == null) {
				buf =
					(NodeSet) context.getBroker().findElementsByTagName(
						documents,
						test.getName());
			}
			NodeSet r =
				contextSet.getAncestors(
					buf,
					axis == Constants.ANCESTOR_SELF_AXIS,
					inPredicate);
			LOG.debug("getAncestors found " + r.getLength());
			return r;
		} else {
			NodeSet result = new ArraySet(contextSet.getLength());
			NodeProxy p;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				if (axis == Constants.ANCESTOR_SELF_AXIS && test.matches(p))
					result.add(new NodeProxy(p.doc, p.gid, p.getInternalAddress()));
				while ((p.gid = XMLUtil.getParentId(p.doc, p.gid)) > 0) {
					p.nodeType = Node.ELEMENT_NODE;
					if (test.matches(p))
						result.add(new NodeProxy(p.doc, p.gid));
				}
			}
			return result;
		}
	}

	protected NodeSet getParents(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		return contextSet.getParents();
	}

	public DocumentSet preselect(DocumentSet inDocs, StaticContext context)
		throws XPathException {
		return super.preselect(inDocs, context);
	}

	public void setKeepVirtual(boolean virtual) {
		keepVirtual = virtual;
	}
}
