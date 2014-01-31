/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License v1.0 
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution 
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html). 
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.eclipse.beans.ui.livegraph.views;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.springframework.ide.eclipse.beans.ui.livegraph.model.LiveBean;
import org.springsource.ide.eclipse.commons.browser.javafx.JavaFxBrowser;
import org.springsource.ide.eclipse.commons.gettingstarted.dashboard.DashboardCopier;

/**
 * @author Miles Parker
 * 
 */
public class LiveBeansEditor extends JavaFxBrowser {

	private static final String LIVE_BEANS_PAGE_URI = "platform:/plugin/org.springframework.ide.eclipse.beans.ui.livegraph/resources/livebeans";

	private static final String LINE_FEED = "\n";

	private final File htmlRoot;

	public LiveBeansEditor() throws URISyntaxException, IOException {
		setName("Live Beans Graph");
		URL fileURL = FileLocator.toFileURL(new URL(LIVE_BEANS_PAGE_URI));
		htmlRoot = DashboardCopier.getCopy(new File(fileURL.toURI()), new NullProgressMonitor());
		File welcomeHtml = new File(htmlRoot, "liveBeans.html");
		setHomeUrl(welcomeHtml.toURI().toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springsource.ide.eclipse.commons.browser.javafx.JavaFxBrowser#init
	 * (org.eclipse.ui.IEditorSite, org.eclipse.ui.IEditorInput)
	 */
	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		super.init(site, input);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.EditorPart#setInput(org.eclipse.ui.IEditorInput)
	 */
	@Override
	protected void setInput(IEditorInput input) {
		super.setInput(input);
		LiveBeansEditorInput graphInput = (LiveBeansEditorInput) input;

		List<String> nodes = new ArrayList<String>();
		List<String> links = new ArrayList<String>();
		List<LiveBean> beans = graphInput.getModel().getBeans();
		int nodeIndex = 0;
		for (LiveBean bean : beans) {
			nodes.add(jsonObject(jsonValue("id", nodeIndex) + ", " + jsonValue("beanId", bean.getId()) + ", "
					+ jsonValue("beanType", bean.getBeanType()) + ", "
					+ jsonValue("applicationName", bean.getApplicationName()) + ", "
					+ jsonValue("name", bean.getDisplayName()) + ", " + jsonValue("resource", bean.getResource())
					+ ", " + jsonValue("group", 1)));
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

		System.err.println(jsonData);

		File json = new File(htmlRoot, "data.json");
		try {
			json.createNewFile();
			FileWriter fileWriter = new FileWriter(json);
			fileWriter.write(jsonData);
			fileWriter.close();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean hasToolbar() {
		return false;
	}
}
