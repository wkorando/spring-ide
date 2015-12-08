/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.withStarters;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.SpringBootStarter;
import org.springframework.ide.eclipse.boot.core.dialogs.EditStartersModel;
import org.springframework.ide.eclipse.wizard.WizardPlugin;
import org.springframework.ide.eclipse.wizard.gettingstarted.boot.PopularityTracker;
import org.springframework.ide.eclipse.wizard.gettingstarted.boot.json.InitializrServiceSpec.Dependency;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

/**
 * @author Kris De Volder
 */
public class EditStartersModelTest {

	/**
	 * Tests that the EditStartersModel is parsed and that existing starters already present on the
	 * project are initially selected.
	 */
	@Test
	public void existingStartersSelected() throws Exception {
		IProject project = harness.createBootProject("foo", withStarters("web", "actuator"));
		ISpringBootProject bootProject = SpringBootCore.create(project);
		EditStartersModel wizard = createWizard(project);
		assertEquals(bootProject.getBootVersion(), wizard.getBootVersion());
		assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web", "actuator");
	}


	/**
	 * Tests that we are able to remove a starter.
	 */
	@Test
	public void removeStarter() throws Exception {
		IProject project = harness.createBootProject("foo", withStarters("web", "actuator"));
		final ISpringBootProject bootProject = SpringBootCore.create(project);
		EditStartersModel wizard = createWizard(project);
		assertEquals(bootProject.getBootVersion(), wizard.getBootVersion());
		assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web", "actuator");

		wizard.removeDependency("web");
		assertStarterDeps(wizard.dependencies.getCurrentSelection(), /* removed: "web",*/ "actuator");
		wizard.performOk();

		StsTestUtil.assertNoErrors(project); //force project build

		assertStarters(bootProject.getBootStarters(), "actuator");
	}

	/**
	 * Tests that we are able to add a basic starter.
	 */
	@Test
	public void addStarter() throws Exception {
		IProject project = harness.createBootProject("foo", withStarters("web"));
		final ISpringBootProject bootProject = SpringBootCore.create(project);
		EditStartersModel wizard = createWizard(project);
		assertEquals(bootProject.getBootVersion(), wizard.getBootVersion());
		assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web");

		PopularityTracker popularities = new PopularityTracker(prefs);
		assertUsageCounts(bootProject, popularities  /*none*/);

		wizard.addDependency("actuator");
		assertStarterDeps(wizard.dependencies.getCurrentSelection(), "web", "actuator");
		wizard.performOk();

		Job.getJobManager().join(EditStartersModel.JOB_FAMILY, null);

		assertUsageCounts(bootProject, popularities, "actuator:1");

		StsTestUtil.assertNoErrors(project); //force project build

		assertStarters(bootProject.getBootStarters(), "web", "actuator");
	}


	//TODO: testing of...
	// - adding a starter
	// - adding a starter that has a scope 'compile' -> scope should not be set in pom element
	// - adding a starter that has a scope other than 'compile'
	// - adding a starter that has a bom
	//    - bom added if not present yet
	//       - bom section created if not existing yet
	//       - bom section reused if already exists (i.e. contains a different bom already)
	//    - bom not added if it is already present
	// - usage counts increased when starter added via this wizard?

	////////////// Harness code below ///////////////////////////////////////////////




	BootProjectTestHarness harness = new BootProjectTestHarness(ResourcesPlugin.getWorkspace());
	private IPreferenceStore prefs = new MockPrefsStore();

	private static boolean wasAutobuilding;

	@BeforeClass
	public static void setupClass() throws Exception {
		wasAutobuilding = StsTestUtil.isAutoBuilding();
		StsTestUtil.setAutoBuilding(false);
	}

	@AfterClass
	public static void teardownClass() throws Exception {
		StsTestUtil.setAutoBuilding(wasAutobuilding);
	}

	@Before
	public void setup() throws Exception {
		StsTestUtil.cleanUpProjects();
	}


	private EditStartersModel createWizard(IProject project) throws Exception {
		return new EditStartersModel(
				project,
				WizardPlugin.getUrlConnectionFactory(),
				new URL(StsProperties.getInstance().get("spring.initializr.json.url")),
				prefs
		);
	}

	private void assertStarters(List<SpringBootStarter> starters, String... expectedIds) {
		Set<String> expecteds = new HashSet<>(Arrays.asList(expectedIds));
		for (SpringBootStarter starter : starters) {
			String id = starter.getId();
			if (expecteds.remove(id)) {
				//okay
			} else {
				fail("Unexpected starter found: "+starter);
			}
		}
		if (!expecteds.isEmpty()) {
			fail("Expected starters not found: "+expecteds);
		}
	}

	private void assertStarterDeps(List<Dependency> starters, String... expectedIds) {
		Set<String> expecteds = new HashSet<>(Arrays.asList(expectedIds));
		for (Dependency starter : starters) {
			String id = starter.getId();
			if (expecteds.remove(id)) {
				//okay
			} else {
				fail("Unexpected starter found: "+starter);
			}
		}
		if (!expecteds.isEmpty()) {
			fail("Expected starters not found: "+expecteds);
		}
	}

	private void assertUsageCounts(ISpringBootProject project, PopularityTracker popularities, String... idAndCount) throws Exception {
		Map<String, Integer> expect = new HashMap<>();
		for (String pair : idAndCount) {
			String[] pieces = pair.split(":");
			assertEquals(2, pieces.length);
			String id = pieces[0];
			int count = Integer.parseInt(pieces[1]);
			expect.put(id, count);
		}


		List<SpringBootStarter> knownStarters = project.getKnownStarters();
		assertFalse(knownStarters.isEmpty());
		for (SpringBootStarter starter : knownStarters) {
			String id = starter.getId();
			Integer expectedCountOrNull = expect.get(id);
			int expectedCount = expectedCountOrNull==null ? 0 : expectedCountOrNull;
			assertEquals("Usage count for '"+id+"'", expectedCount, popularities.getUsageCount(id));
			expect.remove(id);
		}

		assertTrue("Expected usage counts not found: "+expect, expect.isEmpty());
	}


}
