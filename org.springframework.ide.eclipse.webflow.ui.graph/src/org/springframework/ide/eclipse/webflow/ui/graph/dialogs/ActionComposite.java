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

package org.springframework.ide.eclipse.webflow.ui.graph.dialogs;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.springframework.ide.eclipse.webflow.core.internal.model.Action;
import org.springframework.ide.eclipse.webflow.core.internal.model.BeanAction;
import org.springframework.ide.eclipse.webflow.core.internal.model.EvaluateAction;
import org.springframework.ide.eclipse.webflow.core.internal.model.Set;
import org.springframework.ide.eclipse.webflow.core.model.IActionElement;
import org.springframework.ide.eclipse.webflow.core.model.IWebflowModelElement;
import org.springframework.ide.eclipse.webflow.ui.editor.namespaces.webflow.WebflowUIImages;
import org.springframework.ide.eclipse.webflow.ui.graph.model.WebflowModelLabelDecorator;
import org.springframework.ide.eclipse.webflow.ui.graph.model.WebflowModelLabelProvider;

/**
 * 
 */
public class ActionComposite {

	/**
	 * 
	 */
	private class ActionContentProvider implements IStructuredContentProvider {

		/**
		 * 
		 */
		private List<IActionElement> actions;

		/**
		 * 
		 * 
		 * @param actions 
		 */
		public ActionContentProvider(List<IActionElement> actions) {
			this.actions = actions;
		}

		/**
		 * 
		 */
		public void dispose() {
		}

		/**
		 * 
		 * 
		 * @param obj 
		 * 
		 * @return 
		 */
		public Object[] getElements(Object obj) {
			return actions.toArray();
		}

		/**
		 * 
		 * 
		 * @param arg1 
		 * @param arg0 
		 * @param arg2 
		 */
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}
	}

	/**
	 * 
	 */
	private TableViewer configsViewer;

	/**
	 * 
	 */
	private Button editButton;

	/**
	 * 
	 */
	private Button addActionButton;

	/**
	 * 
	 */
	private Button addBeanActionButton;

	/**
	 * 
	 */
	private Button addEvaluationButton;

	/**
	 * 
	 */
	private Button addSetButton;

	/**
	 * 
	 */
	private Button deleteButton;

	// private IDialogValidator validator;

	/**
	 * 
	 */
	private Shell parentShell;

	/**
	 * 
	 */
	private List<IActionElement> actions;

	/**
	 * 
	 */
	private IWebflowModelElement parentElement;

	/**
	 * 
	 */
	@SuppressWarnings("unused")
	private IActionElement.ACTION_TYPE type;

	/**
	 * 
	 * 
	 * @param item 
	 * @param parentElement 
	 * @param parentShell 
	 * @param validator 
	 * @param type 
	 * @param actions 
	 */
	public ActionComposite(IDialogValidator validator, TabItem item,
			Shell parentShell, List<IActionElement> actions,
			IWebflowModelElement parentElement, IActionElement.ACTION_TYPE type) {
		this.actions = actions;
		if (type == IActionElement.ACTION_TYPE.ACTION) {
			item.setText("Actions");
			item.setToolTipText("Define element's actions");
		}
		else if (type == IActionElement.ACTION_TYPE.ENTRY_ACTION) {
			item.setText("Entry Actions");
			item.setToolTipText("Define element's entry actions");
		}
		else if (type == IActionElement.ACTION_TYPE.EXIT_ACTION) {
			item.setText("Exit Actions");
			item.setToolTipText("Define element's exit actions");
		}
		else if (type == IActionElement.ACTION_TYPE.RENDER_ACTION) {
			item.setText("Render Actions");
			item.setToolTipText("Define element's render actions");
		}
		item.setImage(WebflowUIImages
				.getImage(WebflowUIImages.IMG_OBJS_ACTIONS));
		this.parentShell = parentShell;
		this.parentElement = parentElement;
		// this.validator = validator;
		this.type = type;
	}

	/**
	 * 
	 * 
	 * @param parent 
	 * 
	 * @return 
	 */
	protected Control createDialogArea(Composite parent) {
		Group groupActionType = new Group(parent, SWT.NULL);
		GridLayout layoutAttMap = new GridLayout();
		layoutAttMap.marginWidth = 3;
		layoutAttMap.marginHeight = 3;
		groupActionType.setLayout(layoutAttMap);
		groupActionType.setText(" Actions ");
		groupActionType.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Composite tableAndButtons = new Composite(groupActionType, SWT.NONE);
		tableAndButtons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout layout2 = new GridLayout();
		layout2.marginHeight = 0;
		layout2.marginWidth = 0;
		layout2.numColumns = 2;
		tableAndButtons.setLayout(layout2);

		Table configsTable = new Table(tableAndButtons, SWT.MULTI
				| SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.widthHint = 150;
		data.heightHint = 200;
		configsTable.setLayoutData(data);
		configsTable.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				handleTableSelectionChanged();
			}
		});
		configsViewer = new TableViewer(configsTable);
		configsViewer
				.setContentProvider(new ActionContentProvider(this.actions));
		configsViewer.setLabelProvider(new DecoratingLabelProvider(
				new WebflowModelLabelProvider(), new WebflowModelLabelDecorator()));
		configsViewer.setInput(this);

		Composite buttonArea = new Composite(tableAndButtons, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		buttonArea.setLayout(layout);
		buttonArea.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		editButton = new Button(buttonArea, SWT.PUSH);
		editButton.setText("Edit");
		GridData data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 40;
		editButton.setLayoutData(data1);
		editButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				IStructuredSelection selection = (IStructuredSelection) configsViewer
						.getSelection();
				if (selection.getFirstElement() != null) {
					if (selection.getFirstElement() instanceof IActionElement) {
						TitleAreaDialog dialog = null;
						IActionElement actionElement = (IActionElement) selection
								.getFirstElement();
						if (actionElement instanceof Action) {
							dialog = new ActionPropertiesDialog(parentShell,
									parentElement, (Action) selection
											.getFirstElement());
						}
						else if (actionElement instanceof BeanAction) {
							dialog = new BeanActionPropertiesDialog(
									parentShell, parentElement,
									(BeanAction) selection.getFirstElement());
						}
						else if (actionElement instanceof EvaluateAction) {
							dialog = new EvaluateActionPropertiesDialog(
									parentShell, parentElement,
									(EvaluateAction) selection
											.getFirstElement());
						}
						else if (actionElement instanceof Set) {
							dialog = new SetActionPropertiesDialog(parentShell,
									parentElement, (Set) selection
											.getFirstElement());
						}
						if (Dialog.OK == dialog.open()) {
							configsViewer.refresh();
						}
					}
				}
			}
		});

		deleteButton = new Button(buttonArea, SWT.PUSH);
		deleteButton.setText("Delete");
		data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 40;
		deleteButton.setLayoutData(data1);
		deleteButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				IStructuredSelection selection = (IStructuredSelection) configsViewer
						.getSelection();
				if (selection.getFirstElement() != null
						&& selection.getFirstElement() instanceof IActionElement) {
					IActionElement actionElement = (IActionElement) selection
							.getFirstElement();
					actions.remove(actionElement);
					configsViewer.refresh(true);
				}
			}
		});

		Label sep = new Label(buttonArea, SWT.HORIZONTAL | SWT.SEPARATOR);
		sep.setLayoutData(data1);

		addActionButton = new Button(buttonArea, SWT.PUSH);
		addActionButton.setText("Add Action");
		data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 120;
		addActionButton.setLayoutData(data1);
		addActionButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Action action = new Action();
				action.createNew(parentElement);
				action.setType(type);
				if (DialogUtils.openPropertiesDialog(parentElement, action,
						true) == Dialog.OK) {
					actions.add(action);
					configsViewer.refresh();
				}
			}
		});
		addBeanActionButton = new Button(buttonArea, SWT.PUSH);
		addBeanActionButton.setText("Add Bean Action");
		data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 120;
		addBeanActionButton.setLayoutData(data1);
		addBeanActionButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				BeanAction action = new BeanAction();
				action.createNew(parentElement);
				action.setType(type);
				if (DialogUtils.openPropertiesDialog(parentElement, action,
						true) == Dialog.OK) {
					actions.add(action);
					configsViewer.refresh();
				}
			}
		});
		addEvaluationButton = new Button(buttonArea, SWT.PUSH);
		addEvaluationButton.setText("Add Evaluation Action");
		data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 120;
		addEvaluationButton.setLayoutData(data1);
		addEvaluationButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				EvaluateAction action = new EvaluateAction();
				action.createNew(parentElement);
				action.setType(type);
				if (DialogUtils.openPropertiesDialog(parentElement, action,
						true) == Dialog.OK) {
					actions.add(action);
					configsViewer.refresh();
				}
			}
		});
		addSetButton = new Button(buttonArea, SWT.PUSH);
		addSetButton.setText("Add Set");
		data1 = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data1.widthHint = 120;
		addSetButton.setLayoutData(data1);
		addSetButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Set action = new Set();
				action.createNew(parentElement);
				action.setType(type);
				if (DialogUtils.openPropertiesDialog(parentElement, action,
						true) == Dialog.OK) {
					actions.add(action);
					configsViewer.refresh();
				}
			}
		});

		editButton.setEnabled(false);
		deleteButton.setEnabled(false);

		return groupActionType;
	}

	/**
	 * 
	 */
	protected void handleTableSelectionChanged() {
		IStructuredSelection selection = (IStructuredSelection) configsViewer
				.getSelection();
		if (selection.isEmpty()) {
			this.editButton.setEnabled(false);
			this.deleteButton.setEnabled(false);
		}
		else {
			this.editButton.setEnabled(true);
			this.deleteButton.setEnabled(true);
		}
	}

	/**
	 * 
	 * 
	 * @return 
	 */
	public List<IActionElement> getActions() {
		return this.actions;
	}
}
