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
import java.util.Set;

import org.apache.commons.lang.StringUtils;
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
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
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
import org.springsource.ide.eclipse.commons.browser.javafx.JavaFxBrowserView;
import org.springsource.ide.eclipse.commons.gettingstarted.dashboard.DashboardCopier;

/**
 * A simple view to host our graph
 * 
 * @author Leo Dos Santos
 */
public class LiveBeansGraphView extends JavaFxBrowserView {

	private static final String HTML_PAGE_GRAPH = "liveBeansGraph.html";

	private static final String HTML_PAGE_TREE = "liveBeansTree.html";

	private static final String LIVE_BEANS_ROOT_URI = "platform:/plugin/org.springframework.ide.eclipse.beans.ui.livegraph/resources/livebeans";

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

	private final InnerBeansViewerFilter innerBeansFilter;

	private LiveBeansModel activeInput;

	private Action connectApplicationAction;

	private final IPreferenceStore prefStore;

	private int activeDisplayMode;

	private int activeGroupByMode;

	private ITreeContentProvider treeContentProvider;

	public LiveBeansGraphView() throws URISyntaxException, IOException {
		super("", false);
		prefStore = LiveGraphUiPlugin.getDefault().getPreferenceStore();
		innerBeansFilter = new InnerBeansViewerFilter();
	}

	private void setPage(String pageName) {
		try {
			URL fileURL = FileLocator.toFileURL(new URL(LIVE_BEANS_ROOT_URI));
			htmlRoot = DashboardCopier.getCopy(new File(fileURL.toURI()), new NullProgressMonitor());
			File liveBeansUrl = new File(htmlRoot, pageName);
			setUrl(liveBeansUrl.toURI().toString());
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
		super.createPartControl(parent);
		makeActions();
		hookToolBar();
		hookPullDownMenu();
		hookContextMenu();
		setDisplayMode(prefStore.getInt(PREF_DISPLAY_MODE));
		setGroupByMode(prefStore.getInt(PREF_GROUP_MODE));
		setFilterInnerBeans(prefStore.getBoolean(PREF_FILTER_INNER_BEANS));
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

	private boolean isViewerVisible(Viewer viewer) {
		return viewer != null && !viewer.getControl().isDisposed() && viewer.getControl().isVisible();
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
			setPage(HTML_PAGE_GRAPH);
		}
		else if (mode == DISPLAY_MODE_TREE) {
			setPage(HTML_PAGE_TREE);
		}
		for (ToggleViewModeAction action : displayModeActions) {
			action.setChecked(mode == action.getDisplayMode());
		}
		activeDisplayMode = mode;
		prefStore.setValue(PREF_DISPLAY_MODE, mode);
	}

	@Override
	public void setFocus() {
		getBrowserViewer().setFocus();
	}

	public void setGroupByMode(int mode) {
		activeGroupByMode = mode;
		// if (isViewerVisible(treeViewer)) {
		// treeViewer.refresh();
		// }
		// for (ToggleGroupByAction action : groupByActions) {
		// action.setChecked(mode == action.getGroupByMode());
		// }
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

	public void setInput(LiveBeansModel model) {
		activeInput = model;

		List<String> nodes = new ArrayList<String>();
		List<String> links = new ArrayList<String>();
		List<LiveBean> beans = model.getBeans();
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

		treeContentProvider = new LiveBeansTreeContentProvider(this);
		List<String> rootChildren = new ArrayList<String>();
		Object[] elements = treeContentProvider.getElements(model);
		for (Object object : elements) {
			LiveBeansGroup bean = (LiveBeansGroup) object;
			rootChildren.add(jsonSubTree(bean));
		}
		String treeJson = jsonObject(jsonValue("name", "Root") + "," + jsonArray("children", rootChildren));
		writeDataFile("treeData.json", treeJson);
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
		Set<LiveBean> children = new HashSet<LiveBean>();
		Set<LiveBean> dependencies = bean.getDependencies();
		for (LiveBean child : dependencies) {
			children.add(child);
		}
		// Set<LiveBean> injectInto = bean.getInjectedInto();
		// for (LiveBean child : injectInto) {
		// children.add(child);
		// }

		List<String> levelChildren = new ArrayList<String>();
		for (LiveBean childBean : children) {
			if (parents.contains(childBean)) {
				continue;
			}
			System.err.println(childBean.getDisplayName());
			Set<LiveBean> parentsPlus = new HashSet<LiveBean>(parents);
			parentsPlus.add(childBean);
			levelChildren.add(jsonSubTree(childBean, parentsPlus));
		}
		return jsonObject(jsonBeanValues(1, bean) + "," + jsonArray("children", levelChildren));
	}

	private String jsonBeanValues(int nodeIndex, LiveBean bean) {
		return jsonValue("id", nodeIndex) + ", "// + jsonValue("beanId",
												// bean.getId()) + ", "
				// + jsonValue("beanType", bean.getBeanType()) + ", "
				// + jsonValue("applicationName", bean.getApplicationName()) +
				// ", "
				+ jsonValue("name", bean.getDisplayName());
		// + ", " + jsonValue("resource", bean.getResource()) + ", "
		// + jsonValue("group", 1);
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
		// if (graphViewer != null) {
		// if (filtered) {
		// graphViewer.addFilter(innerBeansFilter);
		// }
		// else {
		// graphViewer.removeFilter(innerBeansFilter);
		// }
		// graphViewer.applyLayout();
		// }
		// if (treeViewer != null) {
		// if (filtered) {
		// treeViewer.addFilter(innerBeansFilter);
		// }
		// else {
		// treeViewer.removeFilter(innerBeansFilter);
		// }
		// }
		filterInnerBeansAction.setChecked(filtered);
		prefStore.setValue(PREF_FILTER_INNER_BEANS, filtered);
	}
}
