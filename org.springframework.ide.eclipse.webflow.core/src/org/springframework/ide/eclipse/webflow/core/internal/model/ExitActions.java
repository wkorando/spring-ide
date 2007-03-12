/*
 * Copyright 2002-2007 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ide.eclipse.webflow.core.internal.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.springframework.ide.eclipse.webflow.core.model.IActionElement;
import org.springframework.ide.eclipse.webflow.core.model.IExitActions;
import org.springframework.ide.eclipse.webflow.core.model.IWebflowModelElement;
import org.springframework.ide.eclipse.webflow.core.model.IWebflowState;
import org.w3c.dom.NodeList;

/**
 * 
 * 
 * @author Christian Dupuis
 * @since 2.0
 */
@SuppressWarnings("restriction")
public class ExitActions extends WebflowModelElement implements IExitActions {

	/**
	 * The exit actions.
	 */
	private List<IActionElement> exitActions = null;

	/**
	 * Init.
	 * 
	 * @param node the node
	 * @param parent the parent
	 */
	@Override
	public void init(IDOMNode node, IWebflowModelElement parent) {
		super.init(node, parent);
		this.exitActions = new ArrayList<IActionElement>();

		NodeList children = node.getChildNodes();
		if (children != null && children.getLength() > 0) {
			for (int i = 0; i < children.getLength(); i++) {
				IDOMNode child = (IDOMNode) children.item(i);
				if ("action".equals(child.getLocalName())) {
					Action action = new Action();
					action.init(child, this);
					action.setType(IActionElement.ACTION_TYPE.EXIT_ACTION);
					this.exitActions.add(action);
				}
				else if ("bean-action".equals(child.getLocalName())) {
					BeanAction action = new BeanAction();
					action.init(child, this);
					action.setType(IActionElement.ACTION_TYPE.EXIT_ACTION);
					this.exitActions.add(action);
				}
				else if ("evaluate-action".equals(child.getLocalName())) {
					EvaluateAction action = new EvaluateAction();
					action.init(child, this);
					action.setType(IActionElement.ACTION_TYPE.EXIT_ACTION);
					this.exitActions.add(action);
				}
				else if ("set".equals(child.getLocalName())) {
					Set action = new Set();
					action.init(child, this);
					action.setType(IActionElement.ACTION_TYPE.EXIT_ACTION);
					this.exitActions.add(action);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.ide.eclipse.web.core.model.IActionState#addAction(org.springframework.ide.eclipse.web.core.model.IAction)
	 */
	/**
	 * Adds the exit action.
	 * 
	 * @param action the action
	 */
	public void addExitAction(IActionElement action) {
		if (!this.exitActions.contains(action)) {
			this.exitActions.add(action);
			WebflowModelUtils.insertNode(action.getNode(), node);
			super.firePropertyChange(ADD_CHILDREN, new Integer(this.exitActions
					.indexOf(action)), action);
			parent.fireStructureChange(MOVE_CHILDREN, this);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.ide.eclipse.web.flow.core.model.IActionState#addAction(org.springframework.ide.eclipse.web.flow.core.model.IAction,
	 * int)
	 */
	/**
	 * Adds the exit action.
	 * 
	 * @param i the i
	 * @param action the action
	 */
	public void addExitAction(IActionElement action, int i) {
		if (!this.exitActions.contains(action)) {
			if (this.exitActions.size() > i) {
				IActionElement ref = this.exitActions.get(i);
				WebflowModelUtils.insertBefore(action.getNode(), ref.getNode());
			}
			else {
				WebflowModelUtils.insertNode(action.getNode(), node);
			}
			this.exitActions.add(i, action);
			super.firePropertyChange(ADD_CHILDREN, new Integer(i), action);
			parent.fireStructureChange(MOVE_CHILDREN, this);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.ide.eclipse.web.core.model.IActionState#getActions()
	 */
	/**
	 * Gets the exit actions.
	 * 
	 * @return the exit actions
	 */
	public List<IActionElement> getExitActions() {
		return this.exitActions;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.ide.eclipse.web.core.model.IActionState#removeAction(org.springframework.ide.eclipse.web.core.model.IAction)
	 */
	/**
	 * Removes the exit action.
	 * 
	 * @param action the action
	 */
	public void removeExitAction(IActionElement action) {
		if (this.exitActions.contains(action)) {
			this.exitActions.remove(action);
			getNode().removeChild(action.getNode());
			super.fireStructureChange(REMOVE_CHILDREN, action);
			parent.fireStructureChange(MOVE_CHILDREN, this);
		}
	}

	/**
	 * Creates the new.
	 * 
	 * @param parent the parent
	 */
	public void createNew(IWebflowModelElement parent) {
		if (parent instanceof IWebflowState) {
			IDOMNode node = (IDOMNode) parent.getNode().getOwnerDocument()
					.createElement("end-actions");
			init(node, parent);
		}
		else {
			IDOMNode node = (IDOMNode) parent.getNode().getOwnerDocument()
					.createElement("exit-actions");
			init(node, parent);
		}
	}

	/**
	 * Removes the all.
	 */
	public void removeAll() {
		for (IActionElement action : this.exitActions) {
			getNode().removeChild(action.getNode());
		}
		this.exitActions = new ArrayList<IActionElement>();
	}
}
