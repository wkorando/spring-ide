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

import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.springframework.ide.eclipse.webflow.core.model.IArgument;
import org.springframework.ide.eclipse.webflow.core.model.IWebflowModelElement;

/**
 * 
 * 
 * @author Christian Dupuis
 * @since 2.0
 */
@SuppressWarnings("restriction")
public class Argument extends AbstractModelElement implements IArgument {

	/**
	 * Gets the expression.
	 * 
	 * @return the expression
	 */
	public String getExpression() {
		return getAttribute("expression");
	}

	/**
	 * Gets the parameter type.
	 * 
	 * @return the parameter type
	 */
	public String getParameterType() {
		return getAttribute("parameter-type");
	}

	/**
	 * Sets the expression.
	 * 
	 * @param expression the expression
	 */
	public void setExpression(String expression) {
		setAttribute("expression", expression);
	}

	/**
	 * Sets the parameter type.
	 * 
	 * @param parameterType the parameter type
	 */
	public void setParameterType(String parameterType) {
		setAttribute("parameter-type", parameterType);
	}

	/**
	 * Creates the new.
	 * 
	 * @param parent the parent
	 */
	public void createNew(IWebflowModelElement parent) {
		IDOMNode node = (IDOMNode) parent.getNode().getOwnerDocument()
				.createElement("argument");
		init(node, parent);
	}
}
