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

package org.springframework.ide.eclipse.webflow.ui.graph.commands;

import org.eclipse.gef.commands.Command;
import org.springframework.ide.eclipse.webflow.core.model.IStateTransition;
import org.springframework.ide.eclipse.webflow.core.model.ITransition;
import org.springframework.ide.eclipse.webflow.core.model.ITransitionableFrom;
import org.springframework.ide.eclipse.webflow.core.model.ITransitionableTo;

/**
 * 
 */
public class ReconnectTargetCommand extends Command {

    /**
     * 
     */
    protected ITransitionableTo oldTarget;

    /**
     * 
     */
    protected ITransitionableFrom source;

    /**
     * 
     */
    protected ITransitionableTo target;

    /**
     * 
     */
    protected IStateTransition transition;

    /* (non-Javadoc)
     * @see org.eclipse.gef.commands.Command#canExecute()
     */
    public boolean canExecute() {
        if (transition.getFromState().equals(target))
            return false;
        return true;
    }

    /* (non-Javadoc)
     * @see org.eclipse.gef.commands.Command#execute()
     */
    public void execute() {
        if (target != null) {
            //oldTarget.removeInputTransition(transition);
            transition.setToState(target);
            //target.addInputTransition(transition);
        }
    }

    /**
     * 
     * 
     * @return 
     */
    public ITransitionableFrom getSource() {
        return source;
    }

    /**
     * 
     * 
     * @return 
     */
    public ITransitionableTo getTarget() {
        return target;
    }

    /**
     * 
     * 
     * @return 
     */
    public ITransition getTransition() {
        return transition;
    }

    /**
     * 
     * 
     * @param activity 
     */
    public void setSource(ITransitionableFrom activity) {
        source = activity;
    }

    /**
     * 
     * 
     * @param activity 
     */
    public void setTarget(ITransitionableTo activity) {
        target = activity;
    }

    /**
     * 
     * 
     * @param trans 
     */
    public void setTransition(IStateTransition trans) {
        transition = trans;
        source = trans.getFromState();
        oldTarget = trans.getToState();
    }

    /* (non-Javadoc)
     * @see org.eclipse.gef.commands.Command#undo()
     */
    public void undo() {
        //target.removeInputTransition(transition);
        transition.setToState(oldTarget);
        //oldTarget.addInputTransition(transition);
    }
}