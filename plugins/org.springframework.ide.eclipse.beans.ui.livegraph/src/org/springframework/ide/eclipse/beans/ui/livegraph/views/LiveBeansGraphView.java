/*******************************************************************************
 *  Copyright (c) 2012 - 2013 VMware, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.ui.livegraph.views;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;
import org.springframework.ide.eclipse.beans.ui.livegraph.LiveGraphUiPlugin;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.ConnectToApplicationAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.FilterInnerBeansAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.LoadModelAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.OpenBeanClassAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.OpenBeanDefinitionAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.RefreshApplicationAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.ToggleGroupByAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.actions.ToggleViewModeAction;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBean;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBeansGroup;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBeansModel;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBeansModelCollection;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBeansModelGenerator;
import org.springsource.ide.eclipse.commons.browser.javafx.JavaFxBrowserManager;
import org.springsource.ide.eclipse.commons.browser.javafx.JavaFxBrowserViewer;
import org.springsource.ide.eclipse.commons.gettingstarted.dashboard.DashboardCopier;

/**
 * A simple view to host our graph
 * 
 * @author Leo Dos Santos
 */
public class LiveBeansGraphView extends ViewPart {

	private static final String LIVE_BEANS_ROOT_URI = "platform:/plugin/org.springframework.ide.eclipse.beans.ui.livegraph/resources/livebeans";

	private static final String HTML_PAGE_GRAPH = "liveBeansGraph.html";

	private static final String HTML_PAGE_TREE = "liveBeansTree.html";

	private static final String LINE_FEED = "\n";

	private File htmlRoot;

	public static final String VIEW_ID = "org.springframework.ide.eclipse.beans.ui.livegraph.views.LiveBeansGraphView";

	public static final String PREF_DISPLAY_MODE = LiveGraphUiPlugin.PLUGIN_ID
			+ ".prefs.displayMode.LiveBeansGraphView";

	public static final String PREF_GROUP_MODE = LiveGraphUiPlugin.PLUGIN_ID + ".prefs.groupByMode.LiveBeansGraphView";

	public static final String PREF_FILTER_INNER_BEANS = LiveGraphUiPlugin.PLUGIN_ID
			+ ".prefs.filterInnerBeans.LiveBeansGraphView";

	public static final int DISPLAY_MODE_GRAPH = 0;

	public static final int DISPLAY_MODE_TREE = 1;

	public static final int GROUP_BY_RESOURCE = 0;

	public static final int GROUP_BY_CONTEXT = 1;

	private ToggleViewModeAction[] displayModeActions;

	private ToggleGroupByAction[] groupByActions;

	private BaseSelectionListenerAction openBeanClassAction;

	private BaseSelectionListenerAction openBeanDefAction;

	private FilterInnerBeansAction filterInnerBeansAction;

	private LiveBeansModel activeInput;

	private Action connectApplicationAction;

	private final IPreferenceStore prefStore;

	private int activeDisplayMode;

	private int activeGroupByMode;

	private ITreeContentProvider treeContentProvider;
	
	private PageBook pagebook;

	private JavaFxBrowserViewer graphViewer;
	private JavaFxBrowserManager graphBrowserManager = null;

	private JavaFxBrowserViewer treeViewer;
	private JavaFxBrowserManager treeBrowserManager = null;

	public LiveBeansGraphView() throws URISyntaxException, IOException {
		super();
		prefStore = LiveGraphUiPlugin.getDefault().getPreferenceStore();
	}

	private void createGraphViewer() {
		graphViewer = new JavaFxBrowserViewer(pagebook, SWT.NONE);
		graphViewer.getBrowser().getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {

			public void changed(ObservableValue<? extends State> ov, State oldState, State newState) {
				if (newState == Worker.State.SUCCEEDED && graphViewer != null) {
					if (graphBrowserManager == null) {
						graphBrowserManager = new JavaFxBrowserManager();
					}
					graphBrowserManager.setClient(graphViewer.getBrowser());
				}
			}
		});
	}
	
	private void createTreeViewer() {
		treeViewer = new JavaFxBrowserViewer(pagebook, SWT.NONE);
		treeViewer.getBrowser().getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {

			public void changed(ObservableValue<? extends State> ov, State oldState, State newState) {
				if (newState == Worker.State.SUCCEEDED && treeViewer != null) {
					if (treeBrowserManager == null) {
						treeBrowserManager = new JavaFxBrowserManager();
					}
					treeBrowserManager.setClient(treeViewer.getBrowser());
				}
			}
		});
	}
	
	private void setPage(JavaFxBrowserViewer viewer, String pageName) {
		try {
			URL fileURL = FileLocator.toFileURL(new URL(LIVE_BEANS_ROOT_URI));
			htmlRoot = DashboardCopier.getCopy(new File(fileURL.toURI()), new NullProgressMonitor());
			File liveBeansUrl = new File(htmlRoot, pageName);
			viewer.setURL(liveBeansUrl.toURI().toString());
		}
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		pagebook = new PageBook(parent, SWT.NONE);
		makeActions();
		createGraphViewer();
		createTreeViewer();
		hookToolBar();
		hookPullDownMenu();
		hookContextMenu();
		setDisplayMode(prefStore.getInt(PREF_DISPLAY_MODE));
		setGroupByMode(prefStore.getInt(PREF_GROUP_MODE));
		setFilterInnerBeans(prefStore.getBoolean(PREF_FILTER_INNER_BEANS));
	}
	
	@Override
	public void dispose() {
		if (treeBrowserManager != null) {
			treeBrowserManager.dispose();
		}
		if (treeViewer != null && !treeViewer.isDisposed()) {
			treeViewer.dispose();
		}
		if (graphBrowserManager != null) {
			graphBrowserManager.dispose();
		}
		if (graphViewer != null && !graphViewer.isDisposed()) {
			graphViewer.dispose();
		}
		super.dispose();
	}

	private void fillContextMenu(IMenuManager menuManager) {
		menuManager.add(new Separator());
		menuManager.add(openBeanClassAction);
		menuManager.add(openBeanDefAction);
	}

	private void fillPullDownMenu(IMenuManager menuManager) {
		menuManager.add(connectApplicationAction);
		Set<LiveBeansModel> collection = LiveBeansModelCollection.getInstance().getCollection();
		if (collection.size() > 0) {
			menuManager.add(new Separator());
		}
		for (LiveBeansModel model : collection) {
			menuManager.add(new LoadModelAction(this, model));
		}
		if (activeDisplayMode == DISPLAY_MODE_TREE) {
			menuManager.add(new Separator());
			for (ToggleGroupByAction action : groupByActions) {
				menuManager.add(action);
			}
		}
		// if (activeDisplayMode == DISPLAY_MODE_GRAPH) {
		menuManager.add(new Separator());
		menuManager.add(filterInnerBeansAction);
		// }
	}

	public int getGroupByMode() {
		return activeGroupByMode;
	}

	public LiveBeansModel getInput() {
		return activeInput;
	}

	private void hookContextMenu() {
		MenuManager menuManager = new MenuManager();
		menuManager.setRemoveAllWhenShown(true);
		fillContextMenu(menuManager);

		menuManager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				ISelection selection = getViewSite().getSelectionProvider().getSelection();
				if (!selection.isEmpty()) {
					fillContextMenu(manager);
				}
			}
		});
	}

	private void hookPullDownMenu() {
		IActionBars bars = getViewSite().getActionBars();
		IMenuManager menuManager = bars.getMenuManager();
		menuManager.setRemoveAllWhenShown(true);
		fillPullDownMenu(menuManager);

		menuManager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillPullDownMenu(manager);
			}
		});
	}

	private void hookToolBar() {
		IActionBars bars = getViewSite().getActionBars();
		IToolBarManager toolbar = bars.getToolBarManager();
		for (ToggleViewModeAction displayModeAction : displayModeActions) {
			toolbar.add(displayModeAction);
		}
		toolbar.add(new Separator());
		toolbar.add(new RefreshApplicationAction(this));
	}

	private boolean isViewerVisible(Composite viewer) {
		return viewer != null && !viewer.isDisposed() && viewer.isVisible();
	}

	private void makeActions() {
		openBeanClassAction = new OpenBeanClassAction();
		openBeanDefAction = new OpenBeanDefinitionAction();
		connectApplicationAction = new ConnectToApplicationAction(this);
		displayModeActions = new ToggleViewModeAction[] { new ToggleViewModeAction(this, DISPLAY_MODE_GRAPH),
				new ToggleViewModeAction(this, DISPLAY_MODE_TREE) };
		groupByActions = new ToggleGroupByAction[] { new ToggleGroupByAction(this, GROUP_BY_RESOURCE),
				new ToggleGroupByAction(this, GROUP_BY_CONTEXT) };
		filterInnerBeansAction = new FilterInnerBeansAction(this);
	}

	public void setDisplayMode(int mode) {
		if (mode == DISPLAY_MODE_GRAPH) {
			pagebook.showPage(graphViewer);
		} else if (mode == DISPLAY_MODE_TREE) {
			pagebook.showPage(treeViewer);
		}
		for (ToggleViewModeAction action : displayModeActions) {
			action.setChecked(mode == action.getDisplayMode());
		}
		activeDisplayMode = mode;
		prefStore.setValue(PREF_DISPLAY_MODE, mode);
	}

	@Override
	public void setFocus() {
		if (isViewerVisible(graphViewer)) {
			graphViewer.setFocus();
		}
		else if (isViewerVisible(treeViewer)) {
			treeViewer.setFocus();
		}
	}

	public void setGroupByMode(int mode) {
		activeGroupByMode = mode;
		for (ToggleGroupByAction action : groupByActions) {
			action.setChecked(mode == action.getGroupByMode());
		}
		if (activeInput != null) {
			setTreeInput();
		}
		prefStore.setValue(PREF_GROUP_MODE, mode);
	}

	private static String jsonValue(String name, String value) {
		return "\"" + name + "\":\"" + value + "\"";
	}

	private static String jsonValue(String name, Integer value) {
		return "\"" + name + "\":" + value;
	}

	private static String jsonValue(String name, Boolean value) {
		return "\"" + name + "\":" + value;
	}

	private static String jsonObject(String values) {
		return "{" + values + "}";
	}

	private static String jsonArray(String name, List<String> values) {
		return "\"" + name + "\": [" + LINE_FEED + StringUtils.join(values, "," + LINE_FEED) + "]" + LINE_FEED;
	}
	
	private void setGraphInput() {
		List<String> nodes = new ArrayList<String>();
		List<String> links = new ArrayList<String>();
		List<LiveBean> beans = activeInput.getBeans();
		int nodeIndex = 0;
		for (LiveBean bean : beans) {
			nodes.add(jsonObject(jsonBeanValues(nodeIndex, bean)));
			for (LiveBean dependent : bean.getDependencies()) {
				int dependentIndex = beans.indexOf(dependent);
				if (dependentIndex >= 0) {
					String link = jsonObject(jsonValue("source", nodeIndex) + ", "
							+ jsonValue("target", dependentIndex) + ", " + jsonValue("value", 1) + ", "
							+ jsonValue("left", false) + ", " + jsonValue("right", true));
					links.add(link);
				}
			}
			nodeIndex++;
		}
		String jsonData = jsonObject(jsonArray("nodes", nodes) + "," + LINE_FEED + jsonArray("links", links));
		writeDataFile("graphData.json", jsonData);
		setPage(graphViewer, HTML_PAGE_GRAPH);
	}
	
	private void setTreeInput() {
		if (treeContentProvider == null) { 
			treeContentProvider = new LiveBeansTreeContentProvider(this);
		}
		List<String> rootChildren = new ArrayList<String>();
		Object[] elements = treeContentProvider.getElements(activeInput);
		for (Object object : elements) {
			LiveBeansGroup bean = (LiveBeansGroup) object;
			rootChildren.add(jsonSubTree(bean));
		}
		String treeJson = jsonObject(jsonArray("children", rootChildren));
		writeDataFile("treeData.json", treeJson);		
		setPage(treeViewer, HTML_PAGE_TREE);
	}

	public void setInput(LiveBeansModel model) {
		activeInput = model;
		setGraphInput();
		setTreeInput();
		setDisplayMode(activeDisplayMode);
	}
	
	private String jsonSubTree(LiveBeansGroup group) {
		Object[] children = treeContentProvider.getChildren(group);
		List<String> levelChildren = new ArrayList<String>();
		for (Object object : children) {
			LiveBean childBean = (LiveBean) object;
			levelChildren.add(jsonSubTree(childBean, new HashSet<LiveBean>()));
		}
		return jsonObject(jsonValue("name", group.getDisplayName()) + "," + jsonArray("children", levelChildren));
	}

	private String jsonSubTree(LiveBean bean, Set<LiveBean> parents) {
		List<String> levelChildren = new ArrayList<String>();
		Set<LiveBean> dependencies = bean.getDependencies();
		for (LiveBean child : dependencies) {
			levelChildren.add(jsonObject(jsonValue("name", "Depends on " + child.getDisplayName()) + ","
					+ jsonValue("size", "5000")));
		}
		Set<LiveBean> injectInto = bean.getInjectedInto();
		for (LiveBean child : injectInto) {
			levelChildren.add(jsonObject(jsonValue("name", "Injected into " + child.getDisplayName()) + ","
					+ jsonValue("size", "5000")));
		}

		return jsonObject(jsonBeanValues(1, bean) + "," + jsonArray("children", levelChildren));
	}

	private String jsonBeanValues(int nodeIndex, LiveBean bean) {
		StringBuilder sb = new StringBuilder();
		sb.append(jsonValue("beanId", bean.getId()));
		sb.append(", ");
		sb.append(jsonValue("beanType", bean.getBeanType()));
		sb.append(", ");
		if (bean.getApplicationName() != null && !bean.getApplicationName().isEmpty()) {
			sb.append(jsonValue("applicationName", bean.getApplicationName()));
			sb.append(", ");			
		}
		sb.append(jsonValue("name", bean.getDisplayName()));
		sb.append(", ");			
		sb.append(jsonValue("resource", bean.getResource()));
		sb.append(", ");
		if (bean.getSession().getProject() != null && !bean.getSession().getProject().getName().isEmpty()) {
			sb.append(jsonValue("project", bean.getSession().getProject().getName()));
			sb.append(", ");
		}
		sb.append(jsonValue("group", 1));
		sb.append(", ");
		sb.append(jsonValue("item", nodeIndex));
		return sb.toString();
	}

	private void writeDataFile(String fileName, String data) {
		System.err.println(data);
		File json = new File(htmlRoot, fileName);
		try {
			json.createNewFile();
			FileWriter fileWriter = new FileWriter(json);
			fileWriter.write(data);
			fileWriter.close();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setFilterInnerBeans(boolean filtered) {
		filterInnerBeansAction.setChecked(filtered);
		prefStore.setValue(PREF_FILTER_INNER_BEANS, filtered);
		if (activeInput != null) {
			LiveBeansModel model;
			try {
				model = LiveBeansModelGenerator.refreshModel(activeInput);
				if (model != null) {
					for (ListIterator<LiveBean> itr = model.getBeans().listIterator(); itr.hasNext();) {
						if (itr.next().isInnerBean()) {
							itr.remove();
						}
					}
					setInput(model);
				}
			}
			catch (CoreException e) {
				LiveGraphUiPlugin.getDefault().getLog().log(e.getStatus());
			}
		}
	}
}
