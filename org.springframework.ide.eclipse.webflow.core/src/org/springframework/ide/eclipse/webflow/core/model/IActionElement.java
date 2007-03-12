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

package org.springframework.ide.eclipse.webflow.core.model;

/**
 * 
 * 
 * @author Christian Dupuis
 * @since 2.0
 */
public interface IActionElement extends IWebflowModelElement {

	/**
	 * The Enum ACTION_TYPE.
	 */
	enum ACTION_TYPE {

		/**
		 * The RENDE r_ ACTION.
		 */
		RENDER_ACTION,
		
		/**
		 * The ENTR y_ ACTION.
		 */
		ENTRY_ACTION,
		
		/**
		 * The EXI t_ ACTION.
		 */
		EXIT_ACTION,
		
		/**
		 * The ACTION.
		 */
		ACTION
	};
	
	/**
	 * 
	 * 
	 * @return 
	 */
	ACTION_TYPE getType();
	
	/**
	 * 
	 * 
	 * @param type 
	 */
	void setType(ACTION_TYPE type);

}